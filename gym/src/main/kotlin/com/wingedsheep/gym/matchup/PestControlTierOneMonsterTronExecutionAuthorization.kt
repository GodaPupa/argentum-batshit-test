package com.wingedsheep.gym.matchup

const val PEST_MONSTER_TRON_EXECUTION_AUTHORIZATION_STATUS =
    "EXECUTION_AUTHORIZED_HARNESS_STILL_DISABLED"
const val PEST_MONSTER_TRON_EXECUTION_AUTHORIZATION_SHA256 =
    "38d2c07deec8be4332264d8873b42aa793d5606f31047a656bb29866a2e5a2a5"
const val PEST_MONSTER_TRON_FROZEN_SMOKE_RUN_ID = 36_066_393_701L
const val PEST_MONSTER_TRON_FROZEN_SMOKE_ARTIFACT_ID = 10_836_436_268L
const val PEST_MONSTER_TRON_FROZEN_SMOKE_ARCHIVE_SHA256 =
    "70b9a665fbb154342e2789c1b6b2c2fd912579431a6ae1f9ab289984d5e7801c"
const val PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256 =
    "1cace17d62bf9133bd834ac0ef7df3bbd29de141716f631764d465867975ab31"
const val PEST_MONSTER_TRON_FROZEN_SMOKE_ASSIGNMENTS_SHA256 =
    "3e8c61d729d219261eb485600039b150d66e2e73e90f0f2e1450c7b48a57e098"
const val PEST_MONSTER_TRON_FROZEN_SMOKE_MANIFEST_SHA256 =
    "12b2b0c6c8209fc557c2a2a524223c603b42ae6c7c7ce211cbd291db75fbf6a8"
const val PEST_MONSTER_TRON_FROZEN_SMOKE_QUARANTINE_SHA256 =
    "037fce3d177782fdd26b10bd7a77227f8d0d8cae64b9ef8df025171633f037c8"
const val PEST_MONSTER_TRON_FROZEN_SMOKE_CHECKSUMS_SHA256 =
    "397249d512a86c59266eb98796e96ebac2891495db1182e7c3251e4c37e58d02"
const val PEST_MONSTER_TRON_FROZEN_SMOKE_SOURCE =
    "4230ccaef2760fef54604f64b260dc897d618250"

data class MonsterTronExecutionAuthorizationInspection(
    val errors: List<String>,
    val authorizationSha256: String,
    val status: String,
    val authorizedGames: Int = PEST_MONSTER_TRON_SMOKE_GAMES,
    val attemptLimit: Int = 1,
    val rerollsPermitted: Boolean = false,
    val replacementsPermitted: Boolean = false,
    val regenerationPermitted: Boolean = false,
    val runnerEnabled: Boolean = false,
    val initializerEnabled: Boolean = false,
    val officialSeedsGenerated: Int = PEST_MONSTER_TRON_SMOKE_GAMES,
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
 * Authorizes the already-frozen four-game Monster Tron smoke to proceed to separately reviewed
 * operational implementation. This object exposes no artifact bytes, assignments, seeds,
 * initializer, runner, environment, action chooser, evidence path, or gameplay operation.
 */
object PestControlTierOneMonsterTronExecutionAuthorization {
    fun inspect(): MonsterTronExecutionAuthorizationInspection {
        val errors = mutableListOf<String>()
        val proofBytes = listOf(
            "pest-control-tier-one-monster-tron-execution-authorization-v1",
            "status=$PEST_MONSTER_TRON_EXECUTION_AUTHORIZATION_STATUS",
            "protocolId=$PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID",
            "blockId=$PEST_MONSTER_TRON_SMOKE_BLOCK_ID",
            "qualifiedRunner=$PEST_V2_QUALIFIED_RUNNER",
            "vectorFreezeRun=$PEST_MONSTER_TRON_FROZEN_SMOKE_RUN_ID",
            "vectorArtifactId=$PEST_MONSTER_TRON_FROZEN_SMOKE_ARTIFACT_ID",
            "vectorArchiveSha256=$PEST_MONSTER_TRON_FROZEN_SMOKE_ARCHIVE_SHA256",
            "orderedVectorSha256=$PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256",
            "assignmentCsvSha256=$PEST_MONSTER_TRON_FROZEN_SMOKE_ASSIGNMENTS_SHA256",
            "freezeManifestSha256=$PEST_MONSTER_TRON_FROZEN_SMOKE_MANIFEST_SHA256",
            "quarantineSha256=$PEST_MONSTER_TRON_FROZEN_SMOKE_QUARANTINE_SHA256",
            "checksumInventorySha256=$PEST_MONSTER_TRON_FROZEN_SMOKE_CHECKSUMS_SHA256",
            "freezeSourceCommit=$PEST_MONSTER_TRON_FROZEN_SMOKE_SOURCE",
            "authorizedGames=$PEST_MONSTER_TRON_SMOKE_GAMES",
            "attemptLimit=1",
            "rerollsPermitted=false",
            "replacementsPermitted=false",
            "regenerationPermitted=false",
            "runnerEnabled=false",
            "initializerEnabled=false",
            "officialSeedsGenerated=$PEST_MONSTER_TRON_SMOKE_GAMES",
            "officialGamesInitialized=0",
            "actionsSubmitted=0",
            "outcomeExposure=0",
        ).joinToString("\n", postfix = "\n").toByteArray()
        val authorizationSha256 = sha256(proofBytes)
        if (authorizationSha256 != PEST_MONSTER_TRON_EXECUTION_AUTHORIZATION_SHA256) {
            errors += "execution authorization proof mismatch"
        }
        if (PEST_MONSTER_TRON_SMOKE_GAMES != 4) errors += "smoke block must contain exactly four games"
        if (PEST_MONSTER_TRON_FROZEN_SMOKE_SOURCE !=
            "4230ccaef2760fef54604f64b260dc897d618250"
        ) errors += "freeze source mismatch"
        return MonsterTronExecutionAuthorizationInspection(
            errors = errors,
            authorizationSha256 = authorizationSha256,
            status = PEST_MONSTER_TRON_EXECUTION_AUTHORIZATION_STATUS,
        )
    }
}
