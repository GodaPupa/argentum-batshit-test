package com.wingedsheep.gym.matchup

enum class MonsterTronCascadeChoice {
    CAST_FOR_FREE,
    CAST_NORMAL_FOR_FREE,
    DECLINE,
}

data class TierOneMonsterTronPolicyCalibration(
    val protocolId: String = PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID,
    val pestMainSha256: String = PEST_CONTROL_V10_HASH,
    val opponentMainSha256: String = PEST_MONSTER_TRON_MAIN_SHA256,
    val evidenceClass: String = "SEEDLESS_CRITICAL_POLICY_CALIBRATION",
    val criticalCases: Int = 6,
    val officialGamesAuthorized: Int = 0,
    val officialSeedsGenerated: Int = 0,
    val officialGamesInitialized: Int = 0,
    val officialActionsSubmitted: Int = 0,
    val outcomeExposure: Int = 0,
)

/**
 * Seedless critical-policy overlay for the exact mehanske Monster Tron maindeck.
 *
 * This deliberately does not replace the general production pilot. It freezes the small set of
 * matchup-critical choices that would make the exact-60 opponent materially misplay if left to
 * generic ordering: Tron completion/preservation, Stirrings selection, graveyard targeting, and
 * Cascade + Prototype mode selection.
 */
object PestControlTierOneMonsterTronPolicy {
    val tronPieces: List<String> = listOf(
        "Urza's Mine",
        "Urza's Power Plant",
        "Urza's Tower",
    )

    fun missingTronPiece(knownLands: Collection<String>): String? {
        val present = knownLands.filter { it in tronPieces }.toSet()
        if (present.size != 2) return null
        return tronPieces.single { it !in present }
    }

    fun expeditionMapTarget(knownLands: Collection<String>): String =
        missingTronPiece(knownLands)
            ?: tronPieces.first { piece -> knownLands.count { it == piece } == 0 }

    fun cropRotationSacrifice(battlefieldLands: List<String>): String? {
        battlefieldLands.firstOrNull { it !in tronPieces }?.let { return it }
        val duplicate = tronPieces.firstOrNull { piece -> battlefieldLands.count { it == piece } > 1 }
        return duplicate
    }

    fun cropRotationTarget(battlefieldLands: Collection<String>, handLands: Collection<String>): String =
        expeditionMapTarget(battlefieldLands + handLands)

    fun ancientStirringsPick(revealed: List<String>, knownLands: Collection<String>): String? {
        val missing = missingTronPiece(knownLands)
        if (missing != null && missing in revealed) return missing
        if ("Expedition Map" in revealed) return "Expedition Map"
        return revealed.firstOrNull()
    }

    fun bojukaBogTarget(controllerSeat: Int, graveyardSizes: Map<Int, Int>): Int? =
        graveyardSizes
            .filterKeys { it != controllerSeat }
            .maxWithOrNull(compareBy<Map.Entry<Int, Int>> { it.value }.thenBy { -it.key })
            ?.key

    fun cascadeChoice(hitName: String, canCast: Boolean): MonsterTronCascadeChoice = when {
        !canCast -> MonsterTronCascadeChoice.DECLINE
        hitName == "Boulderbranch Golem" -> MonsterTronCascadeChoice.CAST_NORMAL_FOR_FREE
        else -> MonsterTronCascadeChoice.CAST_FOR_FREE
    }

    fun validationErrors(calibration: TierOneMonsterTronPolicyCalibration): List<String> = buildList {
        if (calibration.protocolId != PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID) add("protocol mismatch")
        if (calibration.pestMainSha256 != PEST_CONTROL_V10_HASH) add("Pest identity mismatch")
        if (calibration.opponentMainSha256 != PEST_MONSTER_TRON_MAIN_SHA256) add("Monster Tron identity mismatch")
        if (calibration.evidenceClass != "SEEDLESS_CRITICAL_POLICY_CALIBRATION") add("evidence class mismatch")
        if (calibration.criticalCases != 6) add("critical case count mismatch")
        if (calibration.officialGamesAuthorized != 0) add("games must remain unauthorized")
        if (calibration.officialSeedsGenerated != 0) add("official seeds must remain zero")
        if (calibration.officialGamesInitialized != 0) add("official games must remain uninitialized")
        if (calibration.officialActionsSubmitted != 0) add("official actions must remain zero")
        if (calibration.outcomeExposure != 0) add("outcome exposure must remain zero")
    }
}
