package com.wingedsheep.gym.matchup

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString

@Serializable
data class V2OfficialArtifactIndex(
    val protocolId: String = PEST_V2_OFFICIAL_PROTOCOL,
    val blockId: String = PEST_V2_OFFICIAL_BLOCK,
    val qualifiedRunner: String = PEST_V2_QUALIFIED_RUNNER,
    val orderedVectorSha256: String = PEST_V2_ORDERED_VECTOR_SHA256,
    val assignmentCsvSha256: String = PEST_V2_ASSIGNMENT_CSV_SHA256,
    val expectedGames: Int = PEST_V2_EXPECTED_GAMES,
    val attemptedGames: List<Int>,
    val attemptedSeeds: List<Long>,
    val recordedGames: List<Int>,
    val perGameRawSha256: List<String>,
    val disposition: String,
)

object PestControlV2OfficialArtifactContract {
    fun buildIndex(
        attempts: List<V2OfficialAttempt>,
        recordedGames: List<Int>,
        perGameRaw: List<ByteArray>,
        disposition: String,
    ): ByteArray {
        require(recordedGames.size == perGameRaw.size)
        val index = V2OfficialArtifactIndex(
            attemptedGames = attempts.map { it.gameNumber },
            attemptedSeeds = attempts.map { it.seed },
            recordedGames = recordedGames,
            perGameRawSha256 = perGameRaw.map(::sha256),
            disposition = disposition,
        )
        return (PROTOCOL_JSON.encodeToString(index) + "\n").toByteArray()
    }

    fun validate(
        index: V2OfficialArtifactIndex,
        frozenSeeds: List<Long>,
    ): List<String> {
        val errors = mutableListOf<String>()
        if (index.protocolId != PEST_V2_OFFICIAL_PROTOCOL) errors += "protocol mismatch"
        if (index.blockId != PEST_V2_OFFICIAL_BLOCK) errors += "block mismatch"
        if (index.qualifiedRunner != PEST_V2_QUALIFIED_RUNNER) errors += "qualified runner mismatch"
        if (index.orderedVectorSha256 != PEST_V2_ORDERED_VECTOR_SHA256) errors += "vector hash mismatch"
        if (index.assignmentCsvSha256 != PEST_V2_ASSIGNMENT_CSV_SHA256) errors += "CSV hash mismatch"
        if (index.expectedGames != PEST_V2_EXPECTED_GAMES) errors += "expected game count mismatch"
        if (index.attemptedGames != (1..index.attemptedGames.size).toList()) errors += "attempt sequence is not a prefix"
        if (index.attemptedSeeds != frozenSeeds.take(index.attemptedSeeds.size)) errors += "attempted seeds are not frozen-vector prefix"
        if (index.recordedGames != (1..index.recordedGames.size).toList()) errors += "recorded games are not a prefix"
        if (index.recordedGames.size > index.attemptedGames.size) errors += "record exists without attempted seed"
        if (index.perGameRawSha256.size != index.recordedGames.size) errors += "per-game hash count mismatch"
        if (index.disposition == "COMPLETED") {
            if (index.attemptedGames.size != PEST_V2_EXPECTED_GAMES || index.recordedGames.size != PEST_V2_EXPECTED_GAMES) {
                errors += "partial block cannot be completed"
            }
        } else if (index.disposition != "REJECTED") {
            errors += "invalid disposition"
        }
        return errors
    }
}
