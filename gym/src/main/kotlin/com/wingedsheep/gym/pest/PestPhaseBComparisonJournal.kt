package com.wingedsheep.gym.pest

import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest

/**
 * Durable raw-vs-lawful comparison journal. This is evidence storage, not a comparator and not
 * Phase-B acceptance. A future reviewed runner supplies the deterministic plan and comparator.
 *
 * Rows must arrive in exact plan order. Every result is retained, including mismatches/unknowns.
 * Comparator exceptions are written and forced immediately before being rethrown. Ordinary rows
 * are checkpointed every 1024 results to avoid one fsync per multi-million-row comparison; a hard
 * crash therefore leaves an unmistakably incomplete prefix whose next key is derivable from the
 * deterministic plan. Such an incomplete original is never silently promoted to full coverage.
 */
internal class PestPhaseBComparisonJournal private constructor(
    private val directory: Path,
    private val channel: FileChannel,
    private val expectedRows: Long,
    private val expectedPlanSha256: String,
) : AutoCloseable {
    private val digest = MessageDigest.getInstance("SHA-256")
    private var previousKey: String? = null
    private var rows = 0L
    private var matches = 0L
    private var mismatches = 0L
    private var unknowns = 0L
    private var exceptions = 0L
    private var closed = false
    private var poisoned = false

    @Synchronized
    fun compare(
        row: PestPhaseBPlannedRow,
        fixtureStateSha256: String,
        orderedHandIds: List<String>,
        block: () -> PestPhaseBRowComparison,
    ): PestPhaseBRowComparison {
        check(!closed && !poisoned)
        require(fixtureStateSha256.matches(Regex("[0-9a-f]{64}")))
        require(orderedHandIds.size == 7 && orderedHandIds.distinct().size == 7)
        val key = key(row.context)
        previousKey?.let { require(key > it) { "Phase-B rows are duplicated or out of plan order" } }
        append("INTENT", buildJsonObject {
            put("key", key)
            put("state_sha256", fixtureStateSha256)
            put("hand_ids", buildJsonArray { orderedHandIds.forEach { add(JsonPrimitive(it)) } })
        }, forceNow = false)
        return try {
            val result = block()
            require(result.context == row.context)
            require(result.orderedHandIds == orderedHandIds)
            require(result.stateSha256 == fixtureStateSha256)
            append("RESULT", buildJsonObject {
                put("key", key)
                put("disposition", result.disposition)
                put("raw_keep", result.rawKeep)
                if (result.lawfulKeep != null) {
                    put("lawful_keep", result.lawfulKeep)
                } else {
                    put("lawful_keep", JsonPrimitive("UNKNOWN"))
                }
                put("raw_bottom", buildJsonArray { result.rawBottomIds.forEach { add(JsonPrimitive(it)) } })
                if (result.lawfulBottomIds != null) {
                    put("lawful_bottom", buildJsonArray {
                        result.lawfulBottomIds.forEach { add(JsonPrimitive(it)) }
                    })
                } else {
                    put("lawful_bottom", JsonPrimitive("UNKNOWN"))
                }
                put("keep_reason", result.keepReason)
                put("bottom_reason", result.bottomReason)
            }, forceNow = result.disposition != "MATCH_ROW_ONLY_NOT_BANK_COVERAGE")
            rows++
            when (result.disposition) {
                "MATCH_ROW_ONLY_NOT_BANK_COVERAGE" -> matches++
                "MISMATCH" -> mismatches++
                "UNQUALIFIED_ROW" -> unknowns++
                else -> error("Unexpected Phase-B disposition: ${result.disposition}")
            }
            previousKey = key
            if (rows % 1024L == 0L) channel.force(false)
            result
        } catch (failure: Throwable) {
            exceptions++
            poisoned = true
            append("EXCEPTION", buildJsonObject {
                put("key", key)
                put("class", failure.javaClass.name)
                put("message", failure.message ?: "")
            }, forceNow = true)
            throw failure
        }
    }

    @Synchronized
    fun finish(): Path {
        check(!closed && !poisoned)
        require(rows == expectedRows) { "Incomplete Phase-B coverage: $rows / $expectedRows" }
        channel.force(true)
        val summary = directory.resolve("summary.json")
        writeNew(summary, (buildJsonObject {
            put("schema", "pest-phase-b-comparison-summary-v1")
            put("authority", "BEHAVIORAL_ORIGINAL_FOR_SEPARATE_AUDIT_ONLY")
            put("expected_rows", expectedRows)
            put("completed_rows", rows)
            put("matches", matches)
            put("mismatches", mismatches)
            put("unknowns", unknowns)
            put("exceptions", exceptions)
            put("plan_sha256", expectedPlanSha256)
            put("journal_sha256", digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) })
            put("official_counters_delta", 0)
        }.toString() + "\n").toByteArray(StandardCharsets.UTF_8))
        forceDirectory(directory)
        return summary
    }

    @Synchronized
    override fun close() {
        if (!closed) {
            closed = true
            channel.force(true)
            channel.close()
        }
    }

    private fun append(kind: String, payload: kotlinx.serialization.json.JsonObject, forceNow: Boolean) {
        val line = "$kind|${payload}\n".toByteArray(StandardCharsets.UTF_8)
        require(line.size <= 1024 * 1024)
        val buffer = ByteBuffer.wrap(line)
        while (buffer.hasRemaining()) channel.write(buffer)
        digest.update(line)
        if (forceNow) channel.force(false)
    }

    private fun key(context: PestPhaseBRowContext): String {
        val deck = when (context.deck) { "pest" -> 0; "monster" -> 1; else -> error("Unfrozen deck") }
        require(context.nameMultisetOrdinal >= 0 && context.physicalRepresentativeOrdinal >= 0)
        require(context.mulligans in 0..2 && context.seat in 0..1)
        return "%d|%012d|%d|%012d|%d|%d".format(
            deck, context.nameMultisetOrdinal, context.mulligans,
            context.physicalRepresentativeOrdinal, context.seat, if (context.onPlay) 1 else 0,
        )
    }

    companion object {
        fun create(root: Path, runId: String, sourceCommit: String, planSha256: String, expectedRows: Long): PestPhaseBComparisonJournal {
            require(root.isAbsolute && root.normalize() == root && root.toRealPath() == root)
            require(Files.isDirectory(root, NOFOLLOW_LINKS))
            require(runId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")))
            require(sourceCommit.matches(Regex("[0-9a-f]{40}")))
            require(planSha256.matches(Regex("[0-9a-f]{64}")) && expectedRows > 0)
            val directory = root.resolve(runId)
            Files.createDirectory(directory)
            forceDirectory(root)
            writeNew(directory.resolve("claim.json"), (buildJsonObject {
                put("schema", "pest-phase-b-comparison-claim-v1")
                put("source_commit", sourceCommit)
                put("plan_sha256", planSha256)
                put("expected_rows", expectedRows)
                put("scope", "PEST_MONSTER_PHASE_B_KEEP_BOTTOM_ONLY")
                put("official_counters_delta", 0)
            }.toString() + "\n").toByteArray(StandardCharsets.UTF_8))
            val channel = FileChannel.open(directory.resolve("journal.txt"), CREATE_NEW, WRITE)
            channel.force(true)
            forceDirectory(directory)
            return PestPhaseBComparisonJournal(directory, channel, expectedRows, planSha256)
        }

        private fun writeNew(path: Path, bytes: ByteArray) {
            FileChannel.open(path, CREATE_NEW, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
        }

        private fun forceDirectory(path: Path) = FileChannel.open(path, READ).use { it.force(true) }
    }
}