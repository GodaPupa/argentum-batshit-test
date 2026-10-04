package com.wingedsheep.gym.pest

import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import kotlinx.serialization.json.Json
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

internal data class PestPhaseBShardPlanIdentity(
    val shardIndex: Int,
    val shardCount: Int,
    val expectedRows: Long,
    val planSha256: String,
)

internal data class PestPhaseBShardRunResult(
    val globalPlanSha256: String,
    val shardIndex: Int,
    val shardCount: Int,
    val shardPlanSha256: String,
    val expectedRows: Long,
    val completedRows: Long,
    val summary: Path,
)

/**
 * Prospective sharded Phase-B runner for a future separately reviewed aggregate-original gate.
 *
 * This is intentionally separate from [PestPhaseBProductionBankRunner], which remains byte-identical
 * to the consumed v4.1 source. All shards are deterministic partitions of the same frozen global
 * plan. This source alone grants no execution authority.
 */
internal object PestPhaseBProductionShardRunner {
    private val json = Json {
        serializersModule = engineSerializersModule
        encodeDefaults = true
        allowStructuredMapKeys = true
    }

    fun allShardPlanIdentities(): List<PestPhaseBShardPlanIdentity> {
        val shardCount = PestPhaseBShardGeometry.SHARD_COUNT
        val digests = Array(shardCount) { MessageDigest.getInstance("SHA-256") }
        val counts = LongArray(shardCount)
        val globalDigest = MessageDigest.getInstance("SHA-256")
        var globalRows = 0L

        plannedRows().forEach { row ->
            val line = planLine(row)
            globalDigest.update(line)
            val shard = PestPhaseBShardGeometry.shardFor(globalRows)
            digests[shard].update(line)
            counts[shard]++
            globalRows++
        }

        require(globalRows == PestPhaseBShardGeometry.GLOBAL_ROWS)
        require(hex(globalDigest.digest()) == PestPhaseBShardGeometry.GLOBAL_PLAN_SHA256)
        require(counts.sum() == PestPhaseBShardGeometry.GLOBAL_ROWS)

        return (0 until shardCount).map { index ->
            require(counts[index] == PestPhaseBShardGeometry.expectedRows(index))
            PestPhaseBShardPlanIdentity(
                shardIndex = index,
                shardCount = shardCount,
                expectedRows = counts[index],
                planSha256 = hex(digests[index].digest()),
            )
        }
    }

    fun run(
        registry: CardRegistry,
        acceptedBase: Path,
        acceptedSupplement: Path,
        evidenceRoot: Path,
        runId: String,
        sourceCommit: String,
        shardIndex: Int,
    ): PestPhaseBShardRunResult {
        require(shardIndex in 0 until PestPhaseBShardGeometry.SHARD_COUNT)
        require(sourceCommit.matches(Regex("[0-9a-f]{40}")))

        val base = readAccepted(acceptedBase, PestPhaseBAcceptedTruth.BASE_SHA)
        val supplement = readAccepted(acceptedSupplement, PestPhaseBAcceptedTruth.SUPPLEMENT_SHA)
        val truth = PestPhaseBAcceptedTruth.fromAcceptedBytes(base, supplement)

        val identities = allShardPlanIdentities()
        val identity = identities.single { it.shardIndex == shardIndex }
        val journal = PestPhaseBComparisonJournal.create(
            root = evidenceRoot,
            runId = runId,
            sourceCommit = sourceCommit,
            planSha256 = identity.planSha256,
            expectedRows = identity.expectedRows,
        )
        writePartition(evidenceRoot.resolve(runId), identity)

        var completed = 0L
        var globalOrdinal = 0L
        try {
            plannedRows().forEach { row ->
                if (PestPhaseBShardGeometry.accepts(globalOrdinal, shardIndex)) {
                    val fixture = PestPhaseBDeterministicFixtureFactory.build(registry, row)
                    val stateSha = sha256(
                        json.encodeToString(GameState.serializer(), fixture.state).toByteArray()
                    )
                    journal.compare(row, stateSha, fixture.orderedHandIds.map { it.value }) {
                        PestPhaseBProductionRowComparator.compareRow(
                            row.context,
                            fixture.state,
                            fixture.roster,
                            registry,
                            truth,
                        )
                    }
                    completed++
                }
                globalOrdinal++
            }
            require(globalOrdinal == PestPhaseBShardGeometry.GLOBAL_ROWS)
            require(completed == identity.expectedRows) {
                "Silent Phase-B shard omission: shard=$shardIndex $completed / ${identity.expectedRows}"
            }
            val summary = journal.finish()
            return PestPhaseBShardRunResult(
                globalPlanSha256 = PestPhaseBShardGeometry.GLOBAL_PLAN_SHA256,
                shardIndex = shardIndex,
                shardCount = identity.shardCount,
                shardPlanSha256 = identity.planSha256,
                expectedRows = identity.expectedRows,
                completedRows = completed,
                summary = summary,
            )
        } finally {
            journal.close()
        }
    }

    private fun plannedRows(): Sequence<PestPhaseBPlannedRow> = sequence {
        yieldAll(PestPhaseBPhysicalPlan.rows("pest"))
        yieldAll(PestPhaseBPhysicalPlan.rows("monster"))
    }

    private fun planLine(row: PestPhaseBPlannedRow): ByteArray = buildString {
        append(row.context.deck).append('|')
        append(row.context.nameMultisetOrdinal).append('|')
        append(row.context.mulligans).append('|')
        append(row.context.physicalRepresentativeOrdinal).append('|')
        append(row.context.seat).append('|')
        append(if (row.context.onPlay) 1 else 0).append('|')
        row.orderedHand.forEach { card ->
            append(card.entryIndex).append(':').append(card.copyOrdinal).append(';')
        }
        append('\n')
    }.toByteArray()

    private fun writePartition(directory: Path, identity: PestPhaseBShardPlanIdentity) {
        val path = directory.resolve("partition.json")
        val raw = (buildJsonObject {
            put("schema", "pest-phase-b-shard-partition-v1")
            put("authority", "BEHAVIORAL_SHARD_FOR_SEPARATE_AGGREGATE_AUDIT_ONLY")
            put("global_plan_sha256", PestPhaseBShardGeometry.GLOBAL_PLAN_SHA256)
            put("global_rows", PestPhaseBShardGeometry.GLOBAL_ROWS)
            put("shard_index", identity.shardIndex)
            put("shard_count", identity.shardCount)
            put("expected_shard_rows", identity.expectedRows)
            put("shard_plan_sha256", identity.planSha256)
            put("official_counters_delta", 0)
        }.toString() + "\n").toByteArray(StandardCharsets.UTF_8)
        FileChannel.open(path, CREATE_NEW, WRITE).use { channel ->
            val buffer = ByteBuffer.wrap(raw)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        FileChannel.open(directory, READ).use { it.force(true) }
    }

    private fun readAccepted(path: Path, expectedSha: String): ByteArray {
        require(path.isAbsolute && path.normalize() == path)
        require(
            path.toRealPath() == path &&
                Files.isRegularFile(path, NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(path)
        )
        val size = Files.size(path)
        require(size in 1..(16L * 1024 * 1024))
        val raw = Files.readAllBytes(path)
        require(sha256(raw) == expectedSha) { "Accepted truth digest mismatch" }
        return raw
    }

    private fun sha256(raw: ByteArray): String =
        hex(MessageDigest.getInstance("SHA-256").digest(raw))

    private fun hex(raw: ByteArray): String =
        raw.joinToString("") { "%02x".format(it.toInt() and 255) }
}
