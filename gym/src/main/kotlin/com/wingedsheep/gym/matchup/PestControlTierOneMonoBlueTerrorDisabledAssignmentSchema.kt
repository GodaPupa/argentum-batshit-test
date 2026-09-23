package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_MONO_BLUE_TERROR_DISABLED_ASSIGNMENT_SCHEMA_SHA256 =
    "8de7cd011a21453f74b8ef5b0e2c2312fb6b8be5fffe75e8cfcfeb64f1f6ef9f"
const val PEST_MONO_BLUE_TERROR_DISABLED_ASSIGNMENT_CLASSIFICATION =
    "NONEXPERIMENTAL_SCHEMA_VALIDATION_ONLY"

data class MonoBlueTerrorDisabledAssignmentSchemaRow(
    val gameNumber: Int,
    val pestSeat: PestSeat,
    val terrorSeat: PestSeat,
    val startingDeck: MonoBlueTerrorStartingDeck,
    val syntheticSlotLabel: String,
)

data class MonoBlueTerrorDisabledAssignmentSchemaInspection(
    val errors: List<String>,
    val schemaSha256: String,
    val rows: List<MonoBlueTerrorDisabledAssignmentSchemaRow>,
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
object PestControlTierOneMonoBlueTerrorDisabledAssignmentSchema {
    fun inspect(
        registry: CardRegistry,
        rows: List<MonoBlueTerrorDisabledAssignmentSchemaRow> = syntheticRows(),
    ): MonoBlueTerrorDisabledAssignmentSchemaInspection {
        val errors = mutableListOf<String>()

        val plan = PestControlTierOneMonoBlueTerrorDisabledFourCellPlan.inspect(registry)
        if (!plan.green) errors += "disabled four-cell plan is not green"
        if (plan.planSha256 != PEST_MONO_BLUE_TERROR_DISABLED_FOUR_CELL_PLAN_SHA256) {
            errors += "disabled four-cell plan proof mismatch"
        }
        if (rows.size != PEST_MONO_BLUE_TERROR_SMOKE_GAMES) {
            errors += "assignment schema must contain four rows"
        }
        if (rows.map { it.gameNumber } != (1..PEST_MONO_BLUE_TERROR_SMOKE_GAMES).toList()) {
            errors += "assignment schema game order mismatch"
        }

        val rowCells = rows.map { row ->
            MonoBlueTerrorDisabledPlanCell(
                row.gameNumber,
                row.pestSeat,
                row.terrorSeat,
                row.startingDeck,
            )
        }
        if (rowCells != plan.cells) {
            errors += "assignment schema cell mismatch"
        }

        val expectedLabels = (1..PEST_MONO_BLUE_TERROR_SMOKE_GAMES).map {
            "SYNTHETIC_NONEXPERIMENTAL_SLOT_$it"
        }
        if (rows.map { it.syntheticSlotLabel } != expectedLabels) {
            errors += "synthetic slot labels mismatch"
        }
        if (rows.map { it.syntheticSlotLabel }.distinct().size != rows.size) {
            errors += "synthetic slot labels are not unique"
        }

        val proofBytes = buildList {
            add("pest-control-tier-one-mono-blue-terror-disabled-assignment-schema-v1")
            add("fourCellPlanSha256=$PEST_MONO_BLUE_TERROR_DISABLED_FOUR_CELL_PLAN_SHA256")
            add("classification=$PEST_MONO_BLUE_TERROR_DISABLED_ASSIGNMENT_CLASSIFICATION")
            rows.forEach { row ->
                add(
                    "${row.gameNumber}|${row.pestSeat.name}|${row.terrorSeat.name}|" +
                        "${row.startingDeck.name}|${row.syntheticSlotLabel}"
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
        if (schemaSha256 != PEST_MONO_BLUE_TERROR_DISABLED_ASSIGNMENT_SCHEMA_SHA256) {
            errors += "disabled assignment schema hash mismatch"
        }

        return MonoBlueTerrorDisabledAssignmentSchemaInspection(
            errors = errors.distinct(),
            schemaSha256 = schemaSha256,
            rows = rows,
        )
    }

    private fun syntheticRows(): List<MonoBlueTerrorDisabledAssignmentSchemaRow> =
        PestControlTierOneMonoBlueTerrorSmokeHarness.cellTemplate().map { cell ->
            MonoBlueTerrorDisabledAssignmentSchemaRow(
                gameNumber = cell.gameNumber,
                pestSeat = cell.pestSeat,
                terrorSeat = when (cell.pestSeat) {
                    PestSeat.SEAT_ZERO -> PestSeat.SEAT_ONE
                    PestSeat.SEAT_ONE -> PestSeat.SEAT_ZERO
                },
                startingDeck = cell.startingDeck,
                syntheticSlotLabel =
                    "SYNTHETIC_NONEXPERIMENTAL_SLOT_${cell.gameNumber}",
            )
        }
}
