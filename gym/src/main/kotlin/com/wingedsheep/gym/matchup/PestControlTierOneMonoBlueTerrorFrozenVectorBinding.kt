package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_BINDING_SHA256 =
    "b67ed0fc7ff831114bc8bb095dfcfb19fada628213ec7d772a0ab066d7f0f4f6"
const val PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_STATUS =
    "VECTOR_FROZEN_EXECUTION_NOT_AUTHORIZED"

data class MonoBlueTerrorFrozenVectorBindingInspection(
    val errors: List<String>,
    val bindingSha256: String,
    val status: String,
    val vectorSha256: String,
    val assignmentSha256: String,
    val vectorIdentityPresent: Boolean,
) {
    val green: Boolean get() = errors.isEmpty()
    val officialAssignments: Int get() = PEST_MONO_BLUE_TERROR_SMOKE_GAMES
    val officialSeedsGenerated: Int get() = PEST_MONO_BLUE_TERROR_SMOKE_GAMES
    val initializerEnabled: Boolean get() = false
    val runnerEnabled: Boolean get() = false
    val officialGamesAuthorized: Int get() = 0
    val regenerationPermitted: Boolean get() = false
    val productionDispatchPresent: Boolean get() = false
}

/** Reuses accepted construction checks; never loads official seed values or authorizes gameplay. */
object PestControlTierOneMonoBlueTerrorFrozenVectorBinding {
    fun inspect(
        registry: CardRegistry,
        vectorSha256: String = PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_SHA256,
        qualifiedRunner: String = PEST_V2_QUALIFIED_RUNNER,
    ): MonoBlueTerrorFrozenVectorBindingInspection {
        val errors = mutableListOf<String>()
        val plan = PestControlTierOneMonoBlueTerrorDisabledFourCellPlan.inspect(registry)
        if (!plan.green) errors += "disabled four-cell plan is not green"
        if (plan.planSha256 != PEST_MONO_BLUE_TERROR_DISABLED_FOUR_CELL_PLAN_SHA256) {
            errors += "disabled four-cell plan proof mismatch"
        }
        if (plan.cells.size != PEST_MONO_BLUE_TERROR_SMOKE_GAMES) {
            errors += "four-cell plan size mismatch"
        }
        if (vectorSha256 != PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_SHA256) {
            errors += "frozen vector hash mismatch"
        }
        if (qualifiedRunner != PEST_V2_QUALIFIED_RUNNER) errors += "qualified runner mismatch"

        val proofBytes = listOf(
            "pest-control-tier-one-mono-blue-terror-frozen-vector-binding-v1",
            "status=$PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_STATUS",
            "protocolId=$PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID",
            "blockId=$PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID",
            "qualifiedRunner=$qualifiedRunner",
            "constructionClosureSha256=$PEST_MONO_BLUE_TERROR_CONSTRUCTION_CLOSURE_SHA256",
            "fourCellPlanSha256=${plan.planSha256}",
            "workflowRunId=35819861075",
            "artifactId=10733086089",
            "workflowSourceCommit=eb140403cceff8e930afdfb2874972c6e44f77e7",
            "workflowSourceTree=2af00cc7e04eb0157930dc5254fcde27a7253f38",
            "orderedVectorSha256=$vectorSha256",
            "assignmentCsvSha256=$PEST_MONO_BLUE_TERROR_FROZEN_ASSIGNMENTS_SHA256",
            "freezeManifestSha256=$PEST_MONO_BLUE_TERROR_FROZEN_MANIFEST_SHA256",
            "quarantinedVectorSha256=$PEST_MONO_BLUE_TERROR_FROZEN_QUARANTINE_SHA256",
            "artifactArchiveSha256=$PEST_MONO_BLUE_TERROR_FROZEN_ARCHIVE_SHA256",
            "expectedGames=$PEST_MONO_BLUE_TERROR_SMOKE_GAMES",
            "vectorIdentityPresent=true",
            "officialAssignments=4",
            "officialSeedsGenerated=4",
            "initializerEnabled=false",
            "runnerEnabled=false",
            "officialGamesAuthorized=0",
            "officialGamesInitialized=0",
            "submittedActions=0",
            "outcomeExposure=0",
            "regenerationPermitted=false",
            "productionDispatchPresent=false",
        ).joinToString("\n", postfix = "\n").toByteArray()
        val bindingSha256 = sha256(proofBytes)
        if (bindingSha256 != PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_BINDING_SHA256) {
            errors += "frozen vector binding hash mismatch"
        }
        return MonoBlueTerrorFrozenVectorBindingInspection(
            errors = errors.distinct(),
            bindingSha256 = bindingSha256,
            status = if (errors.isEmpty()) {
                PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_STATUS
            } else {
                "VECTOR_BINDING_REJECTED"
            },
            vectorSha256 = vectorSha256,
            assignmentSha256 = PEST_MONO_BLUE_TERROR_FROZEN_ASSIGNMENTS_SHA256,
            vectorIdentityPresent = errors.isEmpty(),
        )
    }
}
