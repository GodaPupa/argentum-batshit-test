package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_GRIXIS_DISABLED_FOUR_CELL_PLAN_SHA256 =
    "c52c6186d42c554dd3972feaed7d1325b7aa7fbdf83cce31492a525c3bf826a3"

data class GrixisDisabledPlanCell(
    val gameNumber: Int,
    val pestSeat: PestSeat,
    val grixisSeat: PestSeat,
    val startingDeck: GrixisStartingDeck,
)

data class GrixisDisabledFourCellPlanInspection(
    val errors: List<String>,
    val planSha256: String,
    val cells: List<GrixisDisabledPlanCell>,
    val vectorPresent: Boolean = false,
    val assignments: Int = 0,
    val officialSeedsGenerated: Int = 0,
    val officialGamesInitialized: Int = 0,
    val submittedActions: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val green: Boolean get() = errors.isEmpty()
}

/** Pure four-cell metadata composition. It cannot accept assignments, entropy, or environments. */
object PestControlTierOneGrixisDisabledFourCellPlan {
    fun inspect(registry: CardRegistry): GrixisDisabledFourCellPlanInspection {
        val errors = mutableListOf<String>()
        val singleGame = PestControlTierOneGrixisDisabledSingleGame.inspect(registry)
        if (!singleGame.green) errors += "disabled single-game wiring is not green"
        if (singleGame.compositionSha256 != PEST_GRIXIS_DISABLED_SINGLE_GAME_SHA256) {
            errors += "disabled single-game proof mismatch"
        }
        val cells = PestControlTierOneGrixisSmokeHarness.cellTemplate().map { cell ->
            GrixisDisabledPlanCell(
                gameNumber = cell.gameNumber,
                pestSeat = cell.pestSeat,
                grixisSeat = cell.pestSeat.opponent(),
                startingDeck = cell.startingDeck,
            )
        }
        if (cells.map { it.gameNumber } != (1..PEST_GRIXIS_SMOKE_GAMES).toList()) {
            errors += "game order mismatch"
        }
        if (cells.count { it.pestSeat == PestSeat.SEAT_ZERO } != 2 ||
            cells.count { it.pestSeat == PestSeat.SEAT_ONE } != 2
        ) errors += "Pest seat balance mismatch"
        if (cells.count { it.startingDeck == GrixisStartingDeck.PEST_CONTROL } != 2 ||
            cells.count { it.startingDeck == GrixisStartingDeck.GRIXIS_AFFINITY } != 2
        ) errors += "starting-deck balance mismatch"

        val proofBytes = buildList {
            add("pest-control-tier-one-grixis-disabled-four-cell-plan-v1")
            add(PEST_GRIXIS_PREBOARD_PROTOCOL_ID)
            add(PEST_GRIXIS_SMOKE_BLOCK_ID)
            add("qualifiedRunner=$PEST_V2_QUALIFIED_RUNNER")
            add("singleGameSha256=$PEST_GRIXIS_DISABLED_SINGLE_GAME_SHA256")
            add("constructionValidationSha256=$PEST_GRIXIS_DISABLED_INITIALIZER_CONSTRUCTION_SHA256")
            add("blockerSha256=$PEST_GRIXIS_OFFICIAL_INITIALIZATION_BLOCKER_SHA256")
            cells.forEach { cell ->
                add(
                    "${cell.gameNumber}|${cell.pestSeat.name}|${cell.grixisSeat.name}|" +
                        cell.startingDeck.name,
                )
            }
            add("pestSeatZero=2")
            add("pestSeatOne=2")
            add("pestStarts=2")
            add("grixisStarts=2")
            add("vectorPresent=false")
            add("assignments=0")
            add("officialSeedsGenerated=0")
            add("officialGamesInitialized=0")
            add("submittedActions=0")
            add("outcomeExposure=0")
        }.joinToString("\n", postfix = "\n").toByteArray()
        val planSha256 = sha256(proofBytes)
        if (planSha256 != PEST_GRIXIS_DISABLED_FOUR_CELL_PLAN_SHA256) {
            errors += "disabled four-cell plan hash mismatch"
        }
        return GrixisDisabledFourCellPlanInspection(
            errors = errors.distinct(),
            planSha256 = planSha256,
            cells = cells,
        )
    }

    private fun PestSeat.opponent(): PestSeat = when (this) {
        PestSeat.SEAT_ZERO -> PestSeat.SEAT_ONE
        PestSeat.SEAT_ONE -> PestSeat.SEAT_ZERO
    }
}
