package com.wingedsheep.gym.matchup

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class MonoBlueTerrorReplicationArtifactIndex(
    val protocolId: String = PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID,
    val blockId: String = PEST_MONO_BLUE_TERROR_REPLICATION_BLOCK_ID,
    val expectedGames: Int = PEST_MONO_BLUE_TERROR_REPLICATION_GAMES,
    val qualifiedRunner: String = PEST_V2_QUALIFIED_RUNNER,
    val freezeCommit: String,
    val orderedVectorSha256: String,
    val assignmentCsvSha256: String,
    val freezeManifestSha256: String,
    val attemptedGames: List<Int>,
    val attemptedSeeds: List<Long>,
    val initializedGames: List<Int>,
    val recordedGames: List<Int>,
    val perGameRawSha256: List<String>,
    val summarySha256: String,
    val disposition: String,
)

object PestControlTierOneMonoBlueTerrorReplicationArtifactContract {
    fun buildIndex(
        vectorIdentity: MonoBlueTerrorSmokeVectorIdentity,
        frozenSeeds: List<Long>,
        outcome: MonoBlueTerrorReplicationExecutionOutcome,
        summary: ByteArray,
    ): ByteArray {
        val index = MonoBlueTerrorReplicationArtifactIndex(
            freezeCommit = vectorIdentity.freezeCommit,
            orderedVectorSha256 = vectorIdentity.orderedVectorSha256,
            assignmentCsvSha256 = vectorIdentity.assignmentCsvSha256,
            freezeManifestSha256 = vectorIdentity.freezeManifestSha256,
            attemptedGames = outcome.attempts.map { it.gameNumber },
            attemptedSeeds = outcome.attempts.map { it.seed },
            initializedGames = outcome.initializedGames,
            recordedGames = outcome.recordedGames,
            perGameRawSha256 = outcome.perGameRaw.map(::sha256),
            summarySha256 = sha256(summary),
            disposition = outcome.disposition.name,
        )
        val errors = validate(index, vectorIdentity, frozenSeeds, outcome.perGameRaw, summary)
        require(errors.isEmpty()) {
            "Mono-Blue Terror replication artifact reconciliation failed: ${errors.joinToString()}"
        }
        return (PROTOCOL_JSON.encodeToString(index) + "\n").toByteArray()
    }

    fun validate(
        index: MonoBlueTerrorReplicationArtifactIndex,
        vectorIdentity: MonoBlueTerrorSmokeVectorIdentity,
        frozenSeeds: List<Long>,
        perGameRaw: List<ByteArray>,
        summary: ByteArray,
    ): List<String> = buildList {
        if (index.protocolId != PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID) add("protocol mismatch")
        if (index.blockId != PEST_MONO_BLUE_TERROR_REPLICATION_BLOCK_ID) add("block mismatch")
        if (index.expectedGames != PEST_MONO_BLUE_TERROR_REPLICATION_GAMES) {
            add("expected replication game count mismatch")
        }
        if (index.qualifiedRunner != PEST_V2_QUALIFIED_RUNNER) add("qualified runner mismatch")
        if (index.freezeCommit != vectorIdentity.freezeCommit) add("freeze commit mismatch")
        if (index.orderedVectorSha256 != vectorIdentity.orderedVectorSha256) {
            add("ordered vector hash mismatch")
        }
        if (index.assignmentCsvSha256 != vectorIdentity.assignmentCsvSha256) {
            add("assignment CSV hash mismatch")
        }
        if (index.freezeManifestSha256 != vectorIdentity.freezeManifestSha256) {
            add("freeze manifest hash mismatch")
        }

        if (frozenSeeds.size != PEST_MONO_BLUE_TERROR_REPLICATION_GAMES) {
            add("frozen replication vector must contain twelve seeds")
        }
        if (frozenSeeds.distinct().size != frozenSeeds.size || frozenSeeds.any { it == 0L }) {
            add("frozen replication seeds must be unique and nonzero")
        }

        if (index.attemptedGames != (1..index.attemptedGames.size).toList()) {
            add("attempt sequence is not an exact prefix")
        }
        if (index.attemptedSeeds != frozenSeeds.take(index.attemptedSeeds.size)) {
            add("attempted seeds are not a frozen-vector prefix")
        }
        if (index.attemptedGames.size != index.attemptedSeeds.size) add("attempt count mismatch")
        if (index.attemptedGames.size > PEST_MONO_BLUE_TERROR_REPLICATION_GAMES) {
            add("too many replication attempts")
        }

        if (index.initializedGames != (1..index.initializedGames.size).toList()) {
            add("initialization sequence is not an exact prefix")
        }
        if (index.initializedGames.size > index.attemptedGames.size) {
            add("initialization exists without durable attempt")
        }

        if (index.recordedGames != (1..index.recordedGames.size).toList()) {
            add("recorded games are not an exact prefix")
        }
        if (index.recordedGames.size > index.initializedGames.size) {
            add("record exists without initialization entry")
        }
        if (index.perGameRawSha256.size != index.recordedGames.size) {
            add("per-game raw hash count mismatch")
        }
        if (index.perGameRawSha256 != perGameRaw.map(::sha256)) add("per-game raw hash mismatch")
        if (index.summarySha256 != sha256(summary)) add("summary hash mismatch")

        when (index.disposition) {
            "VALIDATED" -> {
                if (index.attemptedGames.size != PEST_MONO_BLUE_TERROR_REPLICATION_GAMES) {
                    add("validated replication must attempt all twelve games")
                }
                if (index.initializedGames.size != PEST_MONO_BLUE_TERROR_REPLICATION_GAMES) {
                    add("validated replication must initialize all twelve games")
                }
                if (index.recordedGames.size != PEST_MONO_BLUE_TERROR_REPLICATION_GAMES) {
                    add("validated replication must record all twelve games")
                }
            }
            "REJECTED" -> Unit
            else -> add("invalid replication disposition")
        }
    }
}
