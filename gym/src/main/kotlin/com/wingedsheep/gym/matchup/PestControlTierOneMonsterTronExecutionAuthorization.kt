package com.wingedsheep.gym.matchup

const val PEST_MONSTER_TRON_EXECUTION_AUTHORIZATION_STATUS =
    "R1_VECTOR_EXECUTION_AUTHORIZED_HARNESS_STILL_DISABLED"
const val PEST_MONSTER_TRON_EXECUTION_AUTHORIZATION_SHA256 =
    "42f7e398c9e92ec44f3b42e8ad5d7351f4239518a138aa1a809b02b3333a707d"
const val PEST_MONSTER_TRON_FROZEN_SMOKE_RUN_ID = 36_066_393_701L
const val PEST_MONSTER_TRON_FROZEN_SMOKE_ARTIFACT_ID = 10_836_436_268L
const val PEST_MONSTER_TRON_FROZEN_SMOKE_ARCHIVE_SHA256 =
    "338c32e2a70c4a634e418bce2302def84ac3c359351d9e5ee994f8c4065d4f53"
const val PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256 =
    "8cdba4018aac3f423d92981581b628a8647e15be1f916a32c81ca1aef6ccff89"
const val PEST_MONSTER_TRON_FROZEN_SMOKE_ASSIGNMENTS_SHA256 =
    "edd4d831ed0e9cd319ce908e71cca117203a1f5970e00441f3c9310f10e412c7"
const val PEST_MONSTER_TRON_FROZEN_SMOKE_MANIFEST_SHA256 =
    "8c678ac397016c1e68046e1101869967d9c9544594ae9af642f035bd11738e2a"
const val PEST_MONSTER_TRON_FROZEN_SMOKE_QUARANTINE_SHA256 =
    "c59ef02660abdc99a298bbb3aec9010d29ee745bcf8bfd48ccfe684f3787896b"
const val PEST_MONSTER_TRON_FROZEN_SMOKE_CHECKSUMS_SHA256 =
    "994a9e5b2d33cfe7d2e4010d3fb5c59a2a394936ff3f6fe092d5e3bd8"
const val PEST_MONSTER_TRON_FROZEN_SMOKE_SOURCE =
    "41716f6918ec559ee60b14815b5b0f23d3aaf38a"

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
            "41716f6918ec559ee60b14815b5b0f23d3aaf38a"
        ) errors += "freeze source mismatch"
        return MonsterTronExecutionAuthorizationInspection(
            errors = errors,
            authorizationSha256 = authorizationSha256,
            status = PEST_MONSTER_TRON_EXECUTION_AUTHORIZATION_STATUS,
        )
    }
}
