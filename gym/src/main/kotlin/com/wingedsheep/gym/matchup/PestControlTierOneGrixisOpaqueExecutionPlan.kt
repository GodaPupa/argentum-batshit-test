package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_GRIXIS_OPAQUE_EXECUTION_PLAN_SHA256 =
    "0ddf928336155a0de27ff29c26af0b2d008f55768b257acd7c4f7eb7f6ca1bee"
const val PEST_GRIXIS_OPAQUE_EXECUTION_PLAN_STATUS =
    "OPAQUE_EXECUTION_PLAN_VALIDATED_ADMISSION_BLOCKED"

data class GrixisOpaqueExecutionPlanSlot(
    val ordinal: Int,
    val opaqueAssignmentRef: String,
    val predecessorRef: String?,
    val pestSeat: PestSeat,
    val grixisSeat: PestSeat,
    val startingDeck: GrixisStartingDeck,
)

data class GrixisOpaqueExecutionPlanInspection(
    val errors: List<String>,
    val planSha256: String,
    val status: String,
    val slots: List<GrixisOpaqueExecutionPlanSlot>,
    val executionAdmissionSha256: String,
    val coordinatorSchemaSha256: String,
    val officialAssignmentRowsBound: Int,
    val officialSeedValuesExposed: Int = 0,
    val officialSeedsConsumed: Int = 0,
    val initializerEnabled: Boolean = false,
    val runnerEnabled: Boolean = false,
    val executionAuthorized: Boolean = false,
    val executable: Boolean = false,
    val officialGamesInitialized: Int = 0,
    val submittedActions: Int = 0,
    val outcomeArtifactsWritten: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val green: Boolean get() = errors.isEmpty()
    val executionBlocked: Boolean get() = !executable && !executionAuthorized
}

/** Opaque plan composition only. It cannot contain or load a seed, assignment row, or environment. */
object PestControlTierOneGrixisOpaqueExecutionPlan {
    fun inspect(
        registry: CardRegistry,
        slots: List<GrixisOpaqueExecutionPlanSlot>? = null,
    ): GrixisOpaqueExecutionPlanInspection {
        val errors = mutableListOf<String>()
        val admission = PestControlTierOneGrixisExecutionAdmissionGate.inspect(registry)
        if (!admission.green || !admission.failClosed) errors += "execution admission gate is not fail closed"
        if (admission.admissionSha256 != PEST_GRIXIS_EXECUTION_ADMISSION_BLOCKED_SHA256) {
            errors += "execution admission proof mismatch"
        }
        val disabledPlan = PestControlTierOneGrixisDisabledFourCellPlan.inspect(registry)
        if (!disabledPlan.green) errors += "disabled four-cell plan is not green"
        if (disabledPlan.planSha256 != PEST_GRIXIS_DISABLED_FOUR_CELL_PLAN_SHA256) {
            errors += "disabled four-cell plan proof mismatch"
        }
        val coordinatorSchemaSha256 = PestControlTierOneGrixisCoordinatorLedger.schemaSha256()
        if (coordinatorSchemaSha256 != PEST_GRIXIS_COORDINATOR_SCHEMA_SHA256) {
            errors += "coordinator schema proof mismatch"
        }

        val expectedSlots = disabledPlan.cells.mapIndexed { index, cell ->
            val ordinal = index + 1
            GrixisOpaqueExecutionPlanSlot(
                ordinal = ordinal,
                opaqueAssignmentRef = "FROZEN_ASSIGNMENT_ROW_$ordinal",
                predecessorRef = if (ordinal == 1) null else "FROZEN_ASSIGNMENT_ROW_${ordinal - 1}",
                pestSeat = cell.pestSeat,
                grixisSeat = cell.grixisSeat,
                startingDeck = cell.startingDeck,
            )
        }
        val actualSlots = slots ?: expectedSlots
        if (actualSlots.size != PEST_GRIXIS_SMOKE_GAMES) errors += "opaque plan must contain four slots"
        if (actualSlots.map { it.ordinal } != (1..PEST_GRIXIS_SMOKE_GAMES).toList()) {
            errors += "opaque plan ordinal order mismatch"
        }
        if (actualSlots.map { it.opaqueAssignmentRef }.distinct().size != actualSlots.size) {
            errors += "opaque assignment references are not unique"
        }
        if (actualSlots != expectedSlots) errors += "opaque plan does not match frozen assignment order"

        val proofBytes = buildList {
            add("pest-control-tier-one-grixis-opaque-execution-plan-v1")
            add("status=$PEST_GRIXIS_OPAQUE_EXECUTION_PLAN_STATUS")
            add("protocolId=$PEST_GRIXIS_PREBOARD_PROTOCOL_ID")
            add("blockId=$PEST_GRIXIS_SMOKE_BLOCK_ID")
            add("qualifiedRunner=$PEST_V2_QUALIFIED_RUNNER")
            add("executionAdmissionSha256=${admission.admissionSha256}")
            add("frozenVectorSha256=$PEST_GRIXIS_FROZEN_VECTOR_SHA256")
            add("assignmentCsvSha256=$PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256")
            add("loaderValidationProvenanceSha256=$PEST_GRIXIS_OFFICIAL_LOADER_VALIDATION_PROVENANCE_SHA256")
            add("coordinatorSchemaSha256=$coordinatorSchemaSha256")
            actualSlots.forEach { slot ->
                add(
                    listOf(
                        slot.ordinal,
                        slot.opaqueAssignmentRef,
                        slot.predecessorRef ?: "BLOCK_START",
                        slot.pestSeat.name,
                        slot.grixisSeat.name,
                        slot.startingDeck.name,
                    ).joinToString("|"),
                )
            }
            add("durableAttemptRequiredBeforeInitialization=true")
            add("globalOrderRequired=true")
            add("retryPermitted=false")
            add("continueAfterFailure=false")
            add("officialAssignmentRowsBound=${actualSlots.size}")
            add("officialSeedValuesExposed=0")
            add("officialSeedsConsumed=0")
            add("initializerEnabled=false")
            add("runnerEnabled=false")
            add("executionAuthorized=false")
            add("executable=false")
            add("officialGamesInitialized=0")
            add("submittedActions=0")
            add("outcomeArtifactsWritten=0")
            add("outcomeExposure=0")
        }.joinToString("\n", postfix = "\n").toByteArray()
        val planSha256 = sha256(proofBytes)
        if (planSha256 != PEST_GRIXIS_OPAQUE_EXECUTION_PLAN_SHA256) {
            errors += "opaque execution plan proof mismatch"
        }
        return GrixisOpaqueExecutionPlanInspection(
            errors = errors.distinct(),
            planSha256 = planSha256,
            status = PEST_GRIXIS_OPAQUE_EXECUTION_PLAN_STATUS,
            slots = actualSlots,
            executionAdmissionSha256 = admission.admissionSha256,
            coordinatorSchemaSha256 = coordinatorSchemaSha256,
            officialAssignmentRowsBound = actualSlots.size,
        )
    }
}
