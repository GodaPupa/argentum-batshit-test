package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_GRIXIS_DISABLED_SINGLE_GAME_SHA256 =
    "ec209b598a19e174b9fdae64c0a232c8cd364a4b9bbdaf6f902b00ab861fcd6e"

data class GrixisDisabledSingleGameInspection(
    val errors: List<String>,
    val compositionSha256: String,
    val gameNumber: Int,
    val pestSeat: PestSeat,
    val grixisSeat: PestSeat,
    val startingDeck: GrixisStartingDeck,
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
object PestControlTierOneGrixisDisabledSingleGame {
    fun inspect(registry: CardRegistry): GrixisDisabledSingleGameInspection {
        val errors = mutableListOf<String>()
        val boundary = PestControlTierOneGrixisOfficialInitializationBoundary.inspect(registry)
        if (!boundary.failClosed) errors += "official initialization boundary is not fail closed"
        if (boundary.constructionValidationSha256 != PEST_GRIXIS_DISABLED_INITIALIZER_CONSTRUCTION_SHA256) {
            errors += "disabled initializer construction proof mismatch"
        }
        if (boundary.blockerSha256 != PEST_GRIXIS_OFFICIAL_INITIALIZATION_BLOCKER_SHA256) {
            errors += "official initialization blocker mismatch"
        }
        if (boundary.officialInitializerEnabled) errors += "official initializer is enabled"

        val cell = PestControlTierOneGrixisSmokeHarness.cellTemplate().first()
        if (cell != GrixisSmokeCell(1, PestSeat.SEAT_ZERO, GrixisStartingDeck.PEST_CONTROL)) {
            errors += "game one cell mismatch"
        }
        val proofBytes = listOf(
            "pest-control-tier-one-grixis-disabled-single-game-v1",
            PEST_GRIXIS_PREBOARD_PROTOCOL_ID,
            PEST_GRIXIS_SMOKE_BLOCK_ID,
            "gameNumber=${cell.gameNumber}",
            "pestSeat=${cell.pestSeat.name}",
            "grixisSeat=${cell.pestSeat.opponent().name}",
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
        if (compositionSha256 != PEST_GRIXIS_DISABLED_SINGLE_GAME_SHA256) {
            errors += "disabled single-game composition hash mismatch"
        }
        return GrixisDisabledSingleGameInspection(
            errors = errors.distinct(),
            compositionSha256 = compositionSha256,
            gameNumber = cell.gameNumber,
            pestSeat = cell.pestSeat,
            grixisSeat = cell.pestSeat.opponent(),
            startingDeck = cell.startingDeck,
        )
    }

    private fun PestSeat.opponent(): PestSeat = when (this) {
        PestSeat.SEAT_ZERO -> PestSeat.SEAT_ONE
        PestSeat.SEAT_ONE -> PestSeat.SEAT_ZERO
    }
}
