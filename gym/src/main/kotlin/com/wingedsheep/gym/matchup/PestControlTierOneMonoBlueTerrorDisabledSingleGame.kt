package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_MONO_BLUE_TERROR_DISABLED_SINGLE_GAME_SHA256 =
    "d463d2796b7e6eba536652e27d7456f914120ad0c30a34c266744b36c690a1a9"

data class MonoBlueTerrorDisabledSingleGameInspection(
    val errors: List<String>,
    val compositionSha256: String,
    val gameNumber: Int,
    val pestSeat: PestSeat,
    val terrorSeat: PestSeat,
    val startingDeck: MonoBlueTerrorStartingDeck,
    val submittedActions: Int = 0,
    val officialSeedsGenerated: Int = 0,
    val officialGamesInitialized: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val green: Boolean get() = errors.isEmpty()
}

/**
 * Seedless wiring inspection for the first smoke cell. It composes only previously validated
 * hashes and metadata; it cannot obtain an environment, accept entropy, or submit an action.
 */
object PestControlTierOneMonoBlueTerrorDisabledSingleGame {
    fun inspect(registry: CardRegistry): MonoBlueTerrorDisabledSingleGameInspection {
        val errors = mutableListOf<String>()

        val boundary =
            PestControlTierOneMonoBlueTerrorOfficialInitializationBoundary.inspect(registry)
        if (!boundary.failClosed) {
            errors += "official initialization boundary is not fail closed"
        }
        if (
            boundary.constructionValidationSha256 !=
            PEST_MONO_BLUE_TERROR_DISABLED_INITIALIZER_CONSTRUCTION_SHA256
        ) {
            errors += "disabled initializer construction proof mismatch"
        }
        if (
            boundary.blockerSha256 !=
            PEST_MONO_BLUE_TERROR_OFFICIAL_INITIALIZATION_BLOCKER_SHA256
        ) {
            errors += "official initialization blocker mismatch"
        }
        if (boundary.officialInitializerEnabled) {
            errors += "official initializer is enabled"
        }

        val cell = PestControlTierOneMonoBlueTerrorSmokeHarness.cellTemplate().first()
        if (
            cell != MonoBlueTerrorSmokeCell(
                1,
                PestSeat.SEAT_ZERO,
                MonoBlueTerrorStartingDeck.PEST_CONTROL,
            )
        ) {
            errors += "game one cell mismatch"
        }

        val proofBytes = listOf(
            "pest-control-tier-one-mono-blue-terror-disabled-single-game-v1",
            PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID,
            PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID,
            "gameNumber=${cell.gameNumber}",
            "pestSeat=${cell.pestSeat.name}",
            "terrorSeat=${cell.pestSeat.opponent().name}",
            "startingDeck=${cell.startingDeck.name}",
            "qualifiedRunner=$PEST_V2_QUALIFIED_RUNNER",
            "constructionValidationSha256=${boundary.constructionValidationSha256}",
            "blockerSha256=${boundary.blockerSha256}",
            "initializerEnabled=${boundary.officialInitializerEnabled}",
            "submittedActions=0",
            "officialSeedsGenerated=0",
            "officialGamesInitialized=0",
            "outcomeExposure=0",
        ).joinToString("\n", postfix = "\n").toByteArray()

        val compositionSha256 = sha256(proofBytes)
        if (compositionSha256 != PEST_MONO_BLUE_TERROR_DISABLED_SINGLE_GAME_SHA256) {
            errors += "disabled single-game composition hash mismatch"
        }

        return MonoBlueTerrorDisabledSingleGameInspection(
            errors = errors.distinct(),
            compositionSha256 = compositionSha256,
            gameNumber = cell.gameNumber,
            pestSeat = cell.pestSeat,
            terrorSeat = cell.pestSeat.opponent(),
            startingDeck = cell.startingDeck,
        )
    }

    private fun PestSeat.opponent(): PestSeat = when (this) {
        PestSeat.SEAT_ZERO -> PestSeat.SEAT_ONE
        PestSeat.SEAT_ONE -> PestSeat.SEAT_ZERO
    }
}
