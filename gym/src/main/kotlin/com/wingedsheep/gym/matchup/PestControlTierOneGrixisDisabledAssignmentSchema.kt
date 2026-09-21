package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_GRIXIS_DISABLED_ASSIGNMENT_SCHEMA_SHA256 =
    "d6ad31c02b2e018c6c507ea2d96c39574f5163679374141a73d07f10fa85c7eb"
const val PEST_GRIXIS_DISABLED_ASSIGNMENT_CLASSIFICATION =
    "NONEXPERIMENTAL_SCHEMA_VALIDATION_ONLY"

data class GrixisDisabledAssignmentSchemaRow(
    val gameNumber: Int,
    val pestSeat: PestSeat,
    val grixisSeat: PestSeat,
    val startingDeck: GrixisStartingDeck,
    val syntheticSlotLabel: String,
)

data class GrixisDisabledAssignmentSchemaInspection(
    val errors: List<String>,
    val schemaSha256: String,
    val rows: List<GrixisDisabledAssignmentSchemaRow>,
    val vectorIdentityPresent: Boolean = false,
    val numericEntropyFields: Int = 0,
    val officialAssignments: Int = 0,
    val officialSeedsGenerated: Int = 0,
    val officialGamesInitialized: Int = 0,
    val submittedActions: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val green: Boolean get() = errors.isEmpty()
}

/**
 * Shape-only assignment inspection. Rows contain opaque synthetic labels and structurally have no
 * seed, entropy, vector, commit, or artifact field.
 */
object PestControlTierOneGrixisDisabledAssignmentSchema {
    fun inspect(
        registry: CardRegistry,
        rows: List<GrixisDisabledAssignmentSchemaRow> = syntheticRows(),
    ): GrixisDisabledAssignmentSchemaInspection {
        val errors = mutableListOf<String>()
        val plan = PestControlTierOneGrixisDisabledFourCellPlan.inspect(registry)
        if (!plan.green) errors += "disabled four-cell plan is not green"
        if (plan.planSha256 != PEST_GRIXIS_DISABLED_FOUR_CELL_PLAN_SHA256) {
            errors += "disabled four-cell plan proof mismatch"
        }
        if (rows.size != PEST_GRIXIS_SMOKE_GAMES) errors += "assignment schema must contain four rows"
        if (rows.map { it.gameNumber } != (1..PEST_GRIXIS_SMOKE_GAMES).toList()) {
            errors += "assignment schema game order mismatch"
        }
        val rowCells = rows.map { row ->
            GrixisDisabledPlanCell(row.gameNumber, row.pestSeat, row.grixisSeat, row.startingDeck)
        }
        if (rowCells != plan.cells) errors += "assignment schema cell mismatch"
        val expectedLabels = (1..PEST_GRIXIS_SMOKE_GAMES).map {
            "SYNTHETIC_NONEXPERIMENTAL_SLOT_$it"
        }
        if (rows.map { it.syntheticSlotLabel } != expectedLabels) {
            errors += "synthetic slot labels mismatch"
        }
        if (rows.map { it.syntheticSlotLabel }.distinct().size != rows.size) {
            errors += "synthetic slot labels are not unique"
        }

        val proofBytes = buildList {
            add("pest-control-tier-one-grixis-disabled-assignment-schema-v1")
            add("fourCellPlanSha256=$PEST_GRIXIS_DISABLED_FOUR_CELL_PLAN_SHA256")
            add("classification=$PEST_GRIXIS_DISABLED_ASSIGNMENT_CLASSIFICATION")
            rows.forEach { row ->
                add(
                    "${row.gameNumber}|${row.pestSeat.name}|${row.grixisSeat.name}|" +
                        "${row.startingDeck.name}|${row.syntheticSlotLabel}",
                )
            }
            add("vectorIdentityPresent=false")
            add("numericEntropyFields=0")
            add("officialAssignments=0")
            add("officialSeedsGenerated=0")
            add("officialGamesInitialized=0")
            add("submittedActions=0")
            add("outcomeExposure=0")
        }.joinToString("\n", postfix = "\n").toByteArray()
        val schemaSha256 = sha256(proofBytes)
        if (schemaSha256 != PEST_GRIXIS_DISABLED_ASSIGNMENT_SCHEMA_SHA256) {
            errors += "disabled assignment schema hash mismatch"
        }
        return GrixisDisabledAssignmentSchemaInspection(
            errors = errors.distinct(),
            schemaSha256 = schemaSha256,
            rows = rows,
        )
    }

    private fun syntheticRows(): List<GrixisDisabledAssignmentSchemaRow> =
        PestControlTierOneGrixisSmokeHarness.cellTemplate().map { cell ->
            GrixisDisabledAssignmentSchemaRow(
                gameNumber = cell.gameNumber,
                pestSeat = cell.pestSeat,
                grixisSeat = when (cell.pestSeat) {
                    PestSeat.SEAT_ZERO -> PestSeat.SEAT_ONE
                    PestSeat.SEAT_ONE -> PestSeat.SEAT_ZERO
                },
                startingDeck = cell.startingDeck,
                syntheticSlotLabel = "SYNTHETIC_NONEXPERIMENTAL_SLOT_${cell.gameNumber}",
            )
        }
}
