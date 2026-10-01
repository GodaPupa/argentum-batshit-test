package com.wingedsheep.gym.pest

import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest

internal data class PestPhaseBBankRunResult(
    val planSha256: String,
    val expectedRows: Long,
    val completedRows: Long,
    val summary: Path,
)

/**
 * Trusted Phase-B bank orchestration seam. Deliberately has no main/CLI or workflow.
 *
 * A future separately reviewed gate must authenticate source/runtime and materialize the two
 * accepted byte streams. This object then verifies those bytes through PestPhaseBAcceptedTruth,
 * derives the exact deterministic plan twice (preflight count/digest, then execution), constructs
 * each fixture without fresh entropy, compares exactly once, and journals every row.
 */
internal object PestPhaseBProductionBankRunner {
    private val json = Json {
        serializersModule = engineSerializersModule
        encodeDefaults = true
        allowStructuredMapKeys = true
    }

    fun run(
        registry: CardRegistry,
        acceptedBase: Path,
        acceptedSupplement: Path,
        evidenceRoot: Path,
        runId: String,
        sourceCommit: String,
    ): PestPhaseBBankRunResult {
        require(sourceCommit.matches(Regex("[0-9a-f]{40}")))
        val base = readAccepted(acceptedBase, PestPhaseBAcceptedTruth.BASE_SHA)
        val supplement = readAccepted(acceptedSupplement, PestPhaseBAcceptedTruth.SUPPLEMENT_SHA)
        val truth = PestPhaseBAcceptedTruth.fromAcceptedBytes(base, supplement)

        val (expectedRows, planSha) = planIdentity()
        require(expectedRows > 0)
        val journal = PestPhaseBComparisonJournal.create(evidenceRoot, runId, sourceCommit, planSha, expectedRows)
        var completed = 0L
        try {
            plannedRows().forEach { row ->
                val fixture = PestPhaseBDeterministicFixtureFactory.build(registry, row)
                val stateSha = sha256(json.encodeToString(GameState.serializer(), fixture.state).toByteArray())
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
            require(completed == expectedRows) { "Silent Phase-B row omission: $completed / $expectedRows" }
            val summary = journal.finish()
            return PestPhaseBBankRunResult(planSha, expectedRows, completed, summary)
        } finally {
            journal.close()
        }
    }

    /** Exact plan identity without accepted truth, registry, raw controller, GameState or entropy. */
    fun planIdentity(): Pair<Long, String> {
        val digest = MessageDigest.getInstance("SHA-256")
        var rows = 0L
        plannedRows().forEach { row ->
            val line = buildString {
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
            digest.update(line)
            rows++
        }
        return rows to digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    private fun plannedRows(): Sequence<PestPhaseBPlannedRow> = sequence {
        yieldAll(PestPhaseBPhysicalPlan.rows("pest"))
        yieldAll(PestPhaseBPhysicalPlan.rows("monster"))
    }

    private fun readAccepted(path: Path, expectedSha: String): ByteArray {
        require(path.isAbsolute && path.normalize() == path)
        require(path.toRealPath() == path && Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path))
        val size = Files.size(path)
        require(size in 1..(16L * 1024 * 1024))
        val raw = Files.readAllBytes(path)
        require(sha256(raw) == expectedSha) { "Accepted truth digest mismatch" }
        return raw
    }

    private fun sha256(raw: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(raw).joinToString("") { "%02x".format(it.toInt() and 255) }
}
