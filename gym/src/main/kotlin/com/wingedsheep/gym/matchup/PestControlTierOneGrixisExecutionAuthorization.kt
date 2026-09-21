package com.wingedsheep.gym.matchup

const val PEST_GRIXIS_EXECUTION_AUTHORIZATION_STATUS =
    "EXECUTION_AUTHORIZED_HARNESS_STILL_DISABLED"
const val PEST_GRIXIS_EXECUTION_AUTHORIZATION_SHA256 =
    "a69facdd3c6c4f03d2f9910db96d0bd43e83c3bb96ecee0d8512a7fa262b17c1"

data class GrixisExecutionAuthorizationInspection(
    val errors: List<String>,
    val authorizationSha256: String,
    val status: String,
    val authorizedGames: Int = PEST_GRIXIS_SMOKE_GAMES,
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
 * This object records the decision that the already-frozen four-game Grixis smoke may proceed to a
 * separately reviewed execution implementation. It does not expose the artifact, assignment values,
 * initializer, runner, environment, or any gameplay operation.
 */
object PestControlTierOneGrixisExecutionAuthorization {
    fun inspect(): GrixisExecutionAuthorizationInspection {
        val errors = mutableListOf<String>()
        val proofBytes = listOf(
            "pest-control-tier-one-grixis-execution-authorization-v1",
            "status=$PEST_GRIXIS_EXECUTION_AUTHORIZATION_STATUS",
            "protocolId=$PEST_GRIXIS_PREBOARD_PROTOCOL_ID",
            "blockId=$PEST_GRIXIS_SMOKE_BLOCK_ID",
            "qualifiedRunner=$PEST_V2_QUALIFIED_RUNNER",
            "vectorFreezeRun=35556631787",
            "vectorArtifactId=10620940806",
            "vectorArchiveSha256=88b5e99d7aab2065fb26d51c074478d43310209d45b084d9dcfe552e8c328373",
            "orderedVectorSha256=$PEST_GRIXIS_FROZEN_VECTOR_SHA256",
            "assignmentCsvSha256=$PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256",
            "freezeManifestSha256=$PEST_GRIXIS_FROZEN_MANIFEST_SHA256",
            "quarantineSha256=$PEST_GRIXIS_FROZEN_QUARANTINE_SHA256",
            "checksumInventorySha256=$PEST_GRIXIS_FROZEN_CHECKSUM_INVENTORY_SHA256",
            "finalReadinessSha256=$PEST_GRIXIS_FINAL_HARNESS_READINESS_SHA256",
            "authorizedGames=$PEST_GRIXIS_SMOKE_GAMES",
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
        if (authorizationSha256 != PEST_GRIXIS_EXECUTION_AUTHORIZATION_SHA256) {
            errors += "execution authorization proof mismatch"
        }
        return GrixisExecutionAuthorizationInspection(
            errors = errors,
            authorizationSha256 = authorizationSha256,
            status = PEST_GRIXIS_EXECUTION_AUTHORIZATION_STATUS,
        )
    }
}
