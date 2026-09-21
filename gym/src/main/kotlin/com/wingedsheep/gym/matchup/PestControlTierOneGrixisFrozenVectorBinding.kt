package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_GRIXIS_FROZEN_VECTOR_BINDING_SHA256 =
    "829afa5ed1e0bcc62481a99e236d1e5d0c04a6353c5c16efed5f40c4c5ee84cf"
const val PEST_GRIXIS_FROZEN_VECTOR_STATUS = "VECTOR_FROZEN_EXECUTION_NOT_AUTHORIZED"
const val PEST_GRIXIS_FROZEN_VECTOR_SHA256 =
    "99eb94c4ec28f073534c008b367f3384df25abebd29dde0a9574227599cb60eb"
const val PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256 =
    "0edad899b718accf979198749452e5b750adecf6a6e53a2a4ef56f94093a5017"
const val PEST_GRIXIS_FROZEN_MANIFEST_SHA256 =
    "3d9e4d3918954220addd22db0942637fa378f743492fc30265a49860152d60c2"
const val PEST_GRIXIS_FROZEN_QUARANTINE_SHA256 =
    "de651667c5845c9a75dee707688bc02fca3022dd9b7b4ce33d52c0d5a4ac2af2"
const val PEST_GRIXIS_FROZEN_ARCHIVE_SHA256 =
    "88b5e99d7aab2065fb26d51c074478d43310209d45b084d9dcfe552e8c328373"

data class GrixisFrozenVectorBindingInspection(
    val errors: List<String>,
    val bindingSha256: String,
    val status: String,
    val vectorSha256: String,
    val assignmentSha256: String,
    val vectorIdentityPresent: Boolean = true,
    val officialAssignments: Int = PEST_GRIXIS_SMOKE_GAMES,
    val officialSeedsGenerated: Int = PEST_GRIXIS_SMOKE_GAMES,
    val initializerEnabled: Boolean = false,
    val runnerEnabled: Boolean = false,
    val officialGamesAuthorized: Int = 0,
    val officialGamesInitialized: Int = 0,
    val submittedActions: Int = 0,
    val outcomeExposure: Int = 0,
    val regenerationPermitted: Boolean = false,
    val productionDispatchPresent: Boolean = false,
) {
    val green: Boolean get() = errors.isEmpty()
}

/** Binds frozen artifact identity without loading seeds or exposing an execution surface. */
object PestControlTierOneGrixisFrozenVectorBinding {
    fun inspect(
        registry: CardRegistry,
        vectorSha256: String = PEST_GRIXIS_FROZEN_VECTOR_SHA256,
        qualifiedRunner: String = PEST_V2_QUALIFIED_RUNNER,
    ): GrixisFrozenVectorBindingInspection {
        val errors = mutableListOf<String>()
        val plan = PestControlTierOneGrixisDisabledFourCellPlan.inspect(registry)
        if (!plan.green) errors += "disabled four-cell plan is not green"
        if (plan.planSha256 != PEST_GRIXIS_DISABLED_FOUR_CELL_PLAN_SHA256) {
            errors += "disabled four-cell plan proof mismatch"
        }
        if (plan.cells.size != PEST_GRIXIS_SMOKE_GAMES) errors += "four-cell plan size mismatch"
        if (vectorSha256 != PEST_GRIXIS_FROZEN_VECTOR_SHA256) errors += "frozen vector hash mismatch"
        if (qualifiedRunner != PEST_V2_QUALIFIED_RUNNER) errors += "qualified runner mismatch"

        val proofBytes = listOf(
            "pest-control-tier-one-grixis-frozen-vector-binding-v1",
            "status=$PEST_GRIXIS_FROZEN_VECTOR_STATUS",
            "protocolId=$PEST_GRIXIS_PREBOARD_PROTOCOL_ID",
            "blockId=$PEST_GRIXIS_SMOKE_BLOCK_ID",
            "qualifiedRunner=$qualifiedRunner",
            "constructionClosureSha256=$PEST_GRIXIS_CONSTRUCTION_CLOSURE_SHA256",
            "fourCellPlanSha256=${plan.planSha256}",
            "workflowRunId=35556631787",
            "artifactId=10620940806",
            "workflowSourceCommit=6465548adfa7039ff02edb8834e33318231903f6",
            "workflowSourceTree=835302e314187611fa85384e61d2066462981e3b",
            "orderedVectorSha256=$vectorSha256",
            "assignmentCsvSha256=$PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256",
            "freezeManifestSha256=$PEST_GRIXIS_FROZEN_MANIFEST_SHA256",
            "quarantinedVectorSha256=$PEST_GRIXIS_FROZEN_QUARANTINE_SHA256",
            "artifactArchiveSha256=$PEST_GRIXIS_FROZEN_ARCHIVE_SHA256",
            "expectedGames=$PEST_GRIXIS_SMOKE_GAMES",
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
        if (bindingSha256 != PEST_GRIXIS_FROZEN_VECTOR_BINDING_SHA256) {
            errors += "frozen vector binding hash mismatch"
        }
        return GrixisFrozenVectorBindingInspection(
            errors = errors.distinct(),
            bindingSha256 = bindingSha256,
            status = PEST_GRIXIS_FROZEN_VECTOR_STATUS,
            vectorSha256 = vectorSha256,
            assignmentSha256 = PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256,
        )
    }
}
