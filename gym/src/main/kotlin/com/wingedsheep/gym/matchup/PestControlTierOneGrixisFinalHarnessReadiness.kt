package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_GRIXIS_FINAL_HARNESS_READINESS_SHA256 =
    "ff5dc2524298b4ef19700d3cc69a154a0e2fee24883d86e362c0abfd2f24551c"
const val PEST_GRIXIS_FINAL_HARNESS_READY_STATUS =
    "HARNESS_READY_EXECUTION_NOT_AUTHORIZED"

data class GrixisFinalHarnessSurfaceAudit(
    val workflowFilesAudited: Int,
    val commandFilesAudited: Int,
    val manualDispatchEntrypoints: Int,
    val officialArtifactDownloadEntrypoints: Int,
    val productionCommandEntrypoints: Int,
    val publicRunnerMethods: Int,
)

data class GrixisFinalHarnessReadinessInspection(
    val errors: List<String>,
    val readinessSha256: String,
    val status: String,
    val workflowFilesAudited: Int,
    val commandFilesAudited: Int,
    val officialVectorFrozen: Boolean = true,
    val officialArtifactValidationCount: Int = 1,
    val officialAssignmentRowsBound: Int = PEST_GRIXIS_SMOKE_GAMES,
    val officialSeedValuesExposed: Int = 0,
    val officialSeedsConsumed: Int = 0,
    val initializerEnabled: Boolean = false,
    val runnerEnabled: Boolean = false,
    val executionAuthorized: Boolean = false,
    val officialGamesInitialized: Int = 0,
    val submittedActions: Int = 0,
    val outcomeArtifactsWritten: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val green: Boolean get() = errors.isEmpty()
    val harnessReady: Boolean get() = green && status == PEST_GRIXIS_FINAL_HARNESS_READY_STATUS
    val failClosed: Boolean get() = harnessReady && !initializerEnabled && !runnerEnabled && !executionAuthorized
}

/** Final read-only closure. It proves readiness while preserving the terminal execution blocker. */
object PestControlTierOneGrixisFinalHarnessReadiness {
    fun inspect(
        registry: CardRegistry,
        surfaceAudit: GrixisFinalHarnessSurfaceAudit,
    ): GrixisFinalHarnessReadinessInspection {
        val errors = mutableListOf<String>()
        val admission = PestControlTierOneGrixisExecutionAdmissionGate.inspect(registry)
        if (!admission.green || !admission.failClosed) errors += "execution admission gate is not fail closed"
        if (admission.admissionSha256 != PEST_GRIXIS_EXECUTION_ADMISSION_BLOCKED_SHA256) {
            errors += "execution admission proof mismatch"
        }
        val plan = PestControlTierOneGrixisOpaqueExecutionPlan.inspect(registry)
        if (!plan.green || !plan.executionBlocked) errors += "opaque execution plan is not green and blocked"
        if (plan.planSha256 != PEST_GRIXIS_OPAQUE_EXECUTION_PLAN_SHA256) {
            errors += "opaque execution plan proof mismatch"
        }
        if (surfaceAudit.workflowFilesAudited <= 0) errors += "no Grixis workflow files were audited"
        if (surfaceAudit.commandFilesAudited <= 0) errors += "no production command files were audited"
        if (surfaceAudit.manualDispatchEntrypoints != 0) errors += "manual dispatch entrypoint exists"
        if (surfaceAudit.officialArtifactDownloadEntrypoints != 0) {
            errors += "official artifact download entrypoint exists"
        }
        if (surfaceAudit.productionCommandEntrypoints != 0) errors += "production command entrypoint exists"
        if (surfaceAudit.publicRunnerMethods != 0) errors += "public runner method exists"

        val proofBytes = listOf(
            "pest-control-tier-one-grixis-final-harness-readiness-v1",
            "status=$PEST_GRIXIS_FINAL_HARNESS_READY_STATUS",
            "protocolId=$PEST_GRIXIS_PREBOARD_PROTOCOL_ID",
            "blockId=$PEST_GRIXIS_SMOKE_BLOCK_ID",
            "qualifiedRunner=$PEST_V2_QUALIFIED_RUNNER",
            "frozenVectorSha256=$PEST_GRIXIS_FROZEN_VECTOR_SHA256",
            "assignmentCsvSha256=$PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256",
            "loaderValidationProvenanceSha256=$PEST_GRIXIS_OFFICIAL_LOADER_VALIDATION_PROVENANCE_SHA256",
            "loaderValidationReportSha256=$PEST_GRIXIS_OFFICIAL_LOADER_VALIDATION_REPORT_SHA256",
            "executionAdmissionSha256=${admission.admissionSha256}",
            "opaqueExecutionPlanSha256=${plan.planSha256}",
            "coordinatorSchemaSha256=${plan.coordinatorSchemaSha256}",
            "officialInitializationBlockerSha256=$PEST_GRIXIS_OFFICIAL_INITIALIZATION_BLOCKER_SHA256",
            "workflowFilesAuditedPositive=${surfaceAudit.workflowFilesAudited > 0}",
            "commandFilesAuditedPositive=${surfaceAudit.commandFilesAudited > 0}",
            "manualDispatchEntrypoints=${surfaceAudit.manualDispatchEntrypoints}",
            "officialArtifactDownloadEntrypoints=${surfaceAudit.officialArtifactDownloadEntrypoints}",
            "productionCommandEntrypoints=${surfaceAudit.productionCommandEntrypoints}",
            "publicRunnerMethods=${surfaceAudit.publicRunnerMethods}",
            "officialVectorFrozen=true",
            "officialArtifactValidationCount=1",
            "officialAssignmentRowsBound=4",
            "officialSeedValuesExposed=0",
            "officialSeedsConsumed=0",
            "initializerEnabled=false",
            "runnerEnabled=false",
            "executionAuthorized=false",
            "officialGamesInitialized=0",
            "submittedActions=0",
            "outcomeArtifactsWritten=0",
            "outcomeExposure=0",
        ).joinToString("\n", postfix = "\n").toByteArray()
        val readinessSha256 = sha256(proofBytes)
        if (readinessSha256 != PEST_GRIXIS_FINAL_HARNESS_READINESS_SHA256) {
            errors += "final harness readiness proof mismatch"
        }
        return GrixisFinalHarnessReadinessInspection(
            errors = errors.distinct(),
            readinessSha256 = readinessSha256,
            status = PEST_GRIXIS_FINAL_HARNESS_READY_STATUS,
            workflowFilesAudited = surfaceAudit.workflowFilesAudited,
            commandFilesAudited = surfaceAudit.commandFilesAudited,
        )
    }
}
