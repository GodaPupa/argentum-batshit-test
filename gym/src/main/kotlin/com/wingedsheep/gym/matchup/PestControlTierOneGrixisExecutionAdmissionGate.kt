package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_GRIXIS_OFFICIAL_LOADER_VALIDATION_PROVENANCE_SHA256 =
    "7a08e8c82dafab687b421b822facadd38e08f7825603b1187dd58442ff7a3d6b"
const val PEST_GRIXIS_OFFICIAL_LOADER_VALIDATION_REPORT_SHA256 =
    "264cf1aa248c594879d7f23e8e9c53166054cdfd8f6bf83420f190ab934618b8"
const val PEST_GRIXIS_EXECUTION_ADMISSION_BLOCKED_SHA256 =
    "192c2face0813689d816a21229212b3d79213d156086e5b45b2c34604197aa34"
const val PEST_GRIXIS_EXECUTION_ADMISSION_BLOCKED_STATUS =
    "OFFICIAL_EXECUTION_ADMISSION_BLOCKED"

data class GrixisExecutionAdmissionInspection(
    val errors: List<String>,
    val admissionSha256: String,
    val status: String,
    val frozenVectorBindingSha256: String,
    val loaderValidationProvenanceSha256: String,
    val loaderValidationReportSha256: String,
    val officialArtifactValidationCount: Int = 1,
    val officialSeedValuesParsedPrivately: Int = PEST_GRIXIS_SMOKE_GAMES,
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
    val failClosed: Boolean get() = green && status == PEST_GRIXIS_EXECUTION_ADMISSION_BLOCKED_STATUS
}

/**
 * Digest-only admission inspection. It loads no official bytes and has no initializer, runner,
 * execution, or artifact-writing method.
 */
object PestControlTierOneGrixisExecutionAdmissionGate {
    fun inspect(
        registry: CardRegistry,
        loaderValidationProvenanceSha256: String = PEST_GRIXIS_OFFICIAL_LOADER_VALIDATION_PROVENANCE_SHA256,
        executionAuthorized: Boolean = false,
    ): GrixisExecutionAdmissionInspection {
        val errors = mutableListOf<String>()
        val vector = PestControlTierOneGrixisFrozenVectorBinding.inspect(registry)
        if (!vector.green) errors += "frozen vector binding is not green"
        if (vector.bindingSha256 != PEST_GRIXIS_FROZEN_VECTOR_BINDING_SHA256) {
            errors += "frozen vector binding proof mismatch"
        }
        val loader = PestControlTierOneGrixisDisabledOfficialArtifactLoader.inspect(registry)
        if (!loader.green) errors += "disabled official artifact loader is not green"
        if (loader.status != PEST_GRIXIS_OFFICIAL_LOADER_DISABLED_STATUS ||
            loader.officialArtifactBytesLoaded || loader.officialSeedValuesParsed != 0
        ) errors += "official artifact loader is not disabled"
        if (loaderValidationProvenanceSha256 != PEST_GRIXIS_OFFICIAL_LOADER_VALIDATION_PROVENANCE_SHA256) {
            errors += "official loader validation provenance mismatch"
        }
        val boundary = PestControlTierOneGrixisOfficialInitializationBoundary.inspect(registry)
        if (!boundary.failClosed || "official initializer is disabled" !in boundary.activationBlockers) {
            errors += "official initializer terminal blocker is absent"
        }
        if (executionAuthorized) errors += "execution authorization must remain false during harness construction"

        val proofBytes = listOf(
            "pest-control-tier-one-grixis-execution-admission-gate-v1",
            "status=$PEST_GRIXIS_EXECUTION_ADMISSION_BLOCKED_STATUS",
            "protocolId=$PEST_GRIXIS_PREBOARD_PROTOCOL_ID",
            "blockId=$PEST_GRIXIS_SMOKE_BLOCK_ID",
            "qualifiedRunner=$PEST_V2_QUALIFIED_RUNNER",
            "frozenVectorBindingSha256=${vector.bindingSha256}",
            "orderedVectorSha256=$PEST_GRIXIS_FROZEN_VECTOR_SHA256",
            "assignmentCsvSha256=$PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256",
            "loaderValidationWorkflowRunId=35559222714",
            "loaderValidationEvidenceArtifactId=10620439817",
            "loaderValidationProvenanceSha256=$loaderValidationProvenanceSha256",
            "loaderValidationReportSha256=$PEST_GRIXIS_OFFICIAL_LOADER_VALIDATION_REPORT_SHA256",
            "officialArtifactValidationCount=1",
            "officialArtifactBytesLoaded=false",
            "officialSeedValuesParsedPrivately=4",
            "officialSeedValuesExposed=0",
            "officialSeedsConsumed=0",
            "initializerEnabled=false",
            "runnerEnabled=false",
            "executionAuthorized=$executionAuthorized",
            "officialGamesInitialized=0",
            "submittedActions=0",
            "outcomeArtifactsWritten=0",
            "outcomeExposure=0",
        ).joinToString("\n", postfix = "\n").toByteArray()
        val admissionSha256 = sha256(proofBytes)
        if (admissionSha256 != PEST_GRIXIS_EXECUTION_ADMISSION_BLOCKED_SHA256) {
            errors += "execution admission proof mismatch"
        }
        return GrixisExecutionAdmissionInspection(
            errors = errors.distinct(),
            admissionSha256 = admissionSha256,
            status = PEST_GRIXIS_EXECUTION_ADMISSION_BLOCKED_STATUS,
            frozenVectorBindingSha256 = vector.bindingSha256,
            loaderValidationProvenanceSha256 = loaderValidationProvenanceSha256,
            loaderValidationReportSha256 = PEST_GRIXIS_OFFICIAL_LOADER_VALIDATION_REPORT_SHA256,
            executionAuthorized = executionAuthorized,
        )
    }
}
