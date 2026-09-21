package com.wingedsheep.gym.matchup

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class GrixisSmokeAttempt(val gameNumber: Int, val seed: Long)

@Serializable
data class GrixisSmokeArtifactIndex(
    val protocolId: String = PEST_GRIXIS_PREBOARD_PROTOCOL_ID,
    val blockId: String = PEST_GRIXIS_SMOKE_BLOCK_ID,
    val acceptedReadinessCommit: String = PEST_GRIXIS_READINESS_COMMIT,
    val expectedGames: Int = PEST_GRIXIS_SMOKE_GAMES,
    val freezeCommit: String,
    val orderedVectorSha256: String,
    val assignmentCsvSha256: String,
    val freezeManifestSha256: String,
    val attemptedGames: List<Int>,
    val attemptedSeeds: List<Long>,
    val recordedGames: List<Int>,
    val perGameRawSha256: List<String>,
    val summarySha256: String,
    val disposition: String,
)

/** Pure byte-level contract. It never reads a seed file, writes an artifact, or initializes a game. */
object PestControlTierOneGrixisArtifactContract {
    fun buildIndex(
        vectorIdentity: GrixisSmokeVectorIdentity,
        attempts: List<GrixisSmokeAttempt>,
        recordedGames: List<Int>,
        perGameRaw: List<ByteArray>,
        summary: ByteArray,
        disposition: String,
    ): ByteArray {
        require(recordedGames.size == perGameRaw.size)
        val index = GrixisSmokeArtifactIndex(
            freezeCommit = vectorIdentity.freezeCommit,
            orderedVectorSha256 = vectorIdentity.orderedVectorSha256,
            assignmentCsvSha256 = vectorIdentity.assignmentCsvSha256,
            freezeManifestSha256 = vectorIdentity.freezeManifestSha256,
            attemptedGames = attempts.map { it.gameNumber },
            attemptedSeeds = attempts.map { it.seed },
            recordedGames = recordedGames,
            perGameRawSha256 = perGameRaw.map(::sha256),
            summarySha256 = sha256(summary),
            disposition = disposition,
        )
        return (PROTOCOL_JSON.encodeToString(index) + "\n").toByteArray()
    }

    fun validate(
        index: GrixisSmokeArtifactIndex,
        vectorIdentity: GrixisSmokeVectorIdentity,
        frozenSeeds: List<Long>,
        perGameRaw: List<ByteArray>,
        summary: ByteArray,
    ): List<String> = buildList {
        if (index.protocolId != PEST_GRIXIS_PREBOARD_PROTOCOL_ID) add("protocol mismatch")
        if (index.blockId != PEST_GRIXIS_SMOKE_BLOCK_ID) add("block mismatch")
        if (index.acceptedReadinessCommit != PEST_GRIXIS_READINESS_COMMIT) {
            add("accepted readiness commit mismatch")
        }
        if (index.expectedGames != PEST_GRIXIS_SMOKE_GAMES) add("expected game count mismatch")
        if (index.freezeCommit != vectorIdentity.freezeCommit) add("freeze commit mismatch")
        if (index.orderedVectorSha256 != vectorIdentity.orderedVectorSha256) add("vector hash mismatch")
        if (index.assignmentCsvSha256 != vectorIdentity.assignmentCsvSha256) add("CSV hash mismatch")
        if (index.freezeManifestSha256 != vectorIdentity.freezeManifestSha256) {
            add("freeze manifest hash mismatch")
        }
        if (frozenSeeds.size != PEST_GRIXIS_SMOKE_GAMES) add("frozen vector must contain four seeds")
        if (frozenSeeds.distinct().size != frozenSeeds.size || frozenSeeds.any { it == 0L }) {
            add("frozen seeds must be unique and nonzero")
        }
        if (index.attemptedGames != (1..index.attemptedGames.size).toList()) {
            add("attempt sequence is not a prefix")
        }
        if (index.attemptedSeeds != frozenSeeds.take(index.attemptedSeeds.size)) {
            add("attempted seeds are not frozen-vector prefix")
        }
        if (index.attemptedGames.size != index.attemptedSeeds.size) add("attempt count mismatch")
        if (index.attemptedGames.size > PEST_GRIXIS_SMOKE_GAMES) add("too many attempts")
        if (index.recordedGames != (1..index.recordedGames.size).toList()) {
            add("recorded games are not a prefix")
        }
        if (index.recordedGames.size > index.attemptedGames.size) add("record exists without attempted seed")
        if (index.perGameRawSha256.size != index.recordedGames.size) add("per-game hash count mismatch")
        if (index.perGameRawSha256 != perGameRaw.map(::sha256)) add("per-game raw hash mismatch")
        if (index.summarySha256 != sha256(summary)) add("summary hash mismatch")
        when (index.disposition) {
            "VALIDATED" -> if (
                index.attemptedGames.size != PEST_GRIXIS_SMOKE_GAMES ||
                index.recordedGames.size != PEST_GRIXIS_SMOKE_GAMES
            ) add("partial smoke cannot be validated")
            "REJECTED" -> Unit
            else -> add("invalid disposition")
        }
    }
}
