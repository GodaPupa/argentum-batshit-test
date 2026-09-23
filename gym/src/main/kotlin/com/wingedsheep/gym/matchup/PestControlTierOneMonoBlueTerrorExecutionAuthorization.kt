package com.wingedsheep.gym.matchup

const val PEST_MONO_BLUE_TERROR_EXECUTION_AUTHORIZATION_STATUS =
    "EXECUTION_AUTHORIZED_HARNESS_STILL_DISABLED"
const val PEST_MONO_BLUE_TERROR_EXECUTION_AUTHORIZATION_SHA256 =
    "616166cad640eb3fc7640d3967816b5ad7bb8ea8456a06104adbe69777028959"

data class MonoBlueTerrorExecutionAuthorizationInspection(
    val errors: List<String>,
    val authorizationSha256: String,
    val status: String,
    val authorizedGames: Int = PEST_MONO_BLUE_TERROR_SMOKE_GAMES,
    val attemptLimit: Int = 1,
    val rerollsPermitted: Boolean = false,
    val replacementsPermitted: Boolean = false,
    val regenerationPermitted: Boolean = false,
    val runnerEnabled: Boolean = false,
    val initializerEnabled: Boolean = false,
    val officialSeedsConsumed: Int = 0,
    val officialGamesInitialized: Int = 0,
    val actionsSubmitted: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val green: Boolean get() = errors.isEmpty()
    val executionAuthorized: Boolean get() = green
    val failClosed: Boolean get() = green && !runnerEnabled && !initializerEnabled
}

/**
 * Research authorization boundary only.
 *
 * Records that the already-frozen four-game Mono-Blue Terror smoke may proceed to separately
 * reviewed operational implementation. This object exposes no artifact bytes, assignments, seeds,
 * initializer, runner, environment, evidence path, action or gameplay operation.
 */
object PestControlTierOneMonoBlueTerrorExecutionAuthorization {
    fun inspect(): MonoBlueTerrorExecutionAuthorizationInspection {
        val errors = mutableListOf<String>()
        val proofBytes = listOf(
            "pest-control-tier-one-mono-blue-terror-execution-authorization-v1",
            "status=$PEST_MONO_BLUE_TERROR_EXECUTION_AUTHORIZATION_STATUS",
            "protocolId=$PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID",
            "blockId=$PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID",
            "qualifiedRunner=$PEST_V2_QUALIFIED_RUNNER",
            "vectorFreezeRun=35819861075",
            "vectorArtifactId=10733086089",
            "vectorArchiveSha256=$PEST_MONO_BLUE_TERROR_FROZEN_ARCHIVE_SHA256",
            "orderedVectorSha256=$PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_SHA256",
            "assignmentCsvSha256=$PEST_MONO_BLUE_TERROR_FROZEN_ASSIGNMENTS_SHA256",
            "freezeManifestSha256=$PEST_MONO_BLUE_TERROR_FROZEN_MANIFEST_SHA256",
            "quarantineSha256=$PEST_MONO_BLUE_TERROR_FROZEN_QUARANTINE_SHA256",
            "checksumInventorySha256=$PEST_MONO_BLUE_TERROR_FROZEN_CHECKSUMS_SHA256",
            "frozenVectorBindingSha256=$PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_BINDING_SHA256",
            "engineEvidenceRehearsalSha256=$PEST_MONO_BLUE_TERROR_DISABLED_ENGINE_EVIDENCE_SHA256",
            "policyCalibrationSha256=$PEST_MONO_BLUE_TERROR_POLICY_CALIBRATION_SHA256",
            "authorizedGames=$PEST_MONO_BLUE_TERROR_SMOKE_GAMES",
            "attemptLimit=1",
            "rerollsPermitted=false",
            "replacementsPermitted=false",
            "regenerationPermitted=false",
            "runnerEnabled=false",
            "initializerEnabled=false",
            "officialSeedsConsumed=0",
            "officialGamesInitialized=0",
            "actionsSubmitted=0",
            "outcomeExposure=0",
        ).joinToString("\n", postfix = "\n").toByteArray()
        val authorizationSha256 = sha256(proofBytes)
        if (authorizationSha256 != PEST_MONO_BLUE_TERROR_EXECUTION_AUTHORIZATION_SHA256) {
            errors += "execution authorization proof mismatch"
        }
        return MonoBlueTerrorExecutionAuthorizationInspection(
            errors = errors,
            authorizationSha256 = authorizationSha256,
            status = PEST_MONO_BLUE_TERROR_EXECUTION_AUTHORIZATION_STATUS,
        )
    }
}
