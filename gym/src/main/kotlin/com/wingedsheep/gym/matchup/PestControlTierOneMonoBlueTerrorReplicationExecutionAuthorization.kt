package com.wingedsheep.gym.matchup

const val PEST_MONO_BLUE_TERROR_REPLICATION_EXECUTION_STATUS =
    "PRIMARY_REPLICATION_EXECUTION_AUTHORIZED"
const val PEST_MONO_BLUE_TERROR_REPLICATION_REVIEWED_HEAD =
    "e9c0b357ad2eca3d6d5cb99ce225f6532d640913"
const val PEST_MONO_BLUE_TERROR_REPLICATION_HARNESS_MERGE =
    "51a020b45ad278ec22883c6a6f977c77e2c32c54"
const val PEST_MONO_BLUE_TERROR_REPLICATION_CI_RUN_ID = 35_923_355_149L
const val PEST_MONO_BLUE_TERROR_REPLICATION_VALIDATE_RUN_ID = 35_923_355_357L
const val PEST_MONO_BLUE_TERROR_REPLICATION_STACK_REVIEWED_HEAD =
    "728e2d5e150c527b2049e10b8bc304945e2b7d99"
const val PEST_MONO_BLUE_TERROR_REPLICATION_STACK_MERGE =
    "b8468b5b7865db5a2138852968b9e6ef89278273"
const val PEST_MONO_BLUE_TERROR_REPLICATION_STACK_CI_RUN_ID = 35_925_031_349L
const val PEST_MONO_BLUE_TERROR_REPLICATION_STACK_VALIDATE_RUN_ID = 35_925_031_432L
const val PEST_MONO_BLUE_TERROR_REPLICATION_FREEZE_ARTIFACT_ID = 10_773_131_628L
const val PEST_MONO_BLUE_TERROR_REPLICATION_FREEZE_ARCHIVE_SHA256 =
    "4d3a19ef7febacb336911ff1271c14ab83a6ea1f99ed34ae154ae154d6ef5f25"

data class MonoBlueTerrorReplicationExecutionAuthorizationInspection(
    val errors: List<String>,
    val status: String,
    val reviewedHead: String,
    val harnessMerge: String,
    val ciRunId: Long,
    val artifactValidationRunId: Long,
    val stackReviewedHead: String,
    val stackMerge: String,
    val stackCiRunId: Long,
    val stackValidateRunId: Long,
    val authorizedGames: Int = PEST_MONO_BLUE_TERROR_REPLICATION_GAMES,
    val attemptLimit: Int = 1,
    val rerollsPermitted: Boolean = false,
    val replacementsPermitted: Boolean = false,
    val regenerationPermitted: Boolean = false,
    val executionAuthorized: Boolean = true,
    val officialGamesInitialized: Int = 0,
    val actionsSubmitted: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val green: Boolean get() = errors.isEmpty()
}

/**
 * Reviewed authorization boundary for the frozen twelve-game primary replication block.
 *
 * It binds the exact frozen artifact, the accepted harness validation and the accepted dormant
 * production stack. It exposes no seed generator, reroll, replacement or resume path.
 */
object PestControlTierOneMonoBlueTerrorReplicationExecutionAuthorization {
    fun inspect(): MonoBlueTerrorReplicationExecutionAuthorizationInspection {
        val errors = buildList {
            if (PEST_MONO_BLUE_TERROR_REPLICATION_GAMES != 12) add("replication must contain twelve games")
            if (PEST_MONO_BLUE_TERROR_REPLICATION_REVIEWED_HEAD !=
                "e9c0b357ad2eca3d6d5cb99ce225f6532d640913"
            ) add("reviewed harness head mismatch")
            if (PEST_MONO_BLUE_TERROR_REPLICATION_HARNESS_MERGE !=
                "51a020b45ad278ec22883c6a6f977c77e2c32c54"
            ) add("harness merge mismatch")
            if (PEST_MONO_BLUE_TERROR_REPLICATION_CI_RUN_ID != 35_923_355_149L) {
                add("harness CI provenance mismatch")
            }
            if (PEST_MONO_BLUE_TERROR_REPLICATION_VALIDATE_RUN_ID != 35_923_355_357L) {
                add("artifact validation provenance mismatch")
            }
            if (PEST_MONO_BLUE_TERROR_REPLICATION_STACK_REVIEWED_HEAD !=
                "728e2d5e150c527b2049e10b8bc304945e2b7d99"
            ) add("execution-stack reviewed head mismatch")
            if (PEST_MONO_BLUE_TERROR_REPLICATION_STACK_MERGE !=
                "b8468b5b7865db5a2138852968b9e6ef89278273"
            ) add("execution-stack merge mismatch")
            if (PEST_MONO_BLUE_TERROR_REPLICATION_STACK_CI_RUN_ID != 35_925_031_349L) {
                add("execution-stack CI provenance mismatch")
            }
            if (PEST_MONO_BLUE_TERROR_REPLICATION_STACK_VALIDATE_RUN_ID != 35_925_031_432L) {
                add("execution-stack validation provenance mismatch")
            }
            if (PEST_MONO_BLUE_TERROR_REPLICATION_FREEZE_ARTIFACT_ID != 10_773_131_628L) {
                add("freeze artifact mismatch")
            }
            if (PEST_MONO_BLUE_TERROR_REPLICATION_FREEZE_ARCHIVE_SHA256 !=
                "4d3a19ef7febacb336911ff1271c14ab83a6ea1f99ed34ae154ae154d6ef5f25"
            ) add("freeze archive digest mismatch")
            if (PEST_MONO_BLUE_TERROR_REPLICATION_VECTOR_SHA256 !=
                "445542e6cdf9902cc435b4db276a747e6b2200ff4f24ec9ac896b517a44bd34d"
            ) add("ordered vector mismatch")
            if (PEST_MONO_BLUE_TERROR_REPLICATION_ASSIGNMENTS_SHA256 !=
                "24c1ce43362dbb2ae61fa79926437182fb74d0b76ff28a6ec44d040c2a434b70"
            ) add("assignment CSV mismatch")
            if (PEST_MONO_BLUE_TERROR_REPLICATION_MANIFEST_SHA256 !=
                "ad10e52e6b64d85aa4d890ff772aa31a271435ed100564490f63ec9afc1d6862"
            ) add("freeze manifest mismatch")
        }
        return MonoBlueTerrorReplicationExecutionAuthorizationInspection(
            errors = errors,
            status = PEST_MONO_BLUE_TERROR_REPLICATION_EXECUTION_STATUS,
            reviewedHead = PEST_MONO_BLUE_TERROR_REPLICATION_REVIEWED_HEAD,
            harnessMerge = PEST_MONO_BLUE_TERROR_REPLICATION_HARNESS_MERGE,
            ciRunId = PEST_MONO_BLUE_TERROR_REPLICATION_CI_RUN_ID,
            artifactValidationRunId = PEST_MONO_BLUE_TERROR_REPLICATION_VALIDATE_RUN_ID,
            stackReviewedHead = PEST_MONO_BLUE_TERROR_REPLICATION_STACK_REVIEWED_HEAD,
            stackMerge = PEST_MONO_BLUE_TERROR_REPLICATION_STACK_MERGE,
            stackCiRunId = PEST_MONO_BLUE_TERROR_REPLICATION_STACK_CI_RUN_ID,
            stackValidateRunId = PEST_MONO_BLUE_TERROR_REPLICATION_STACK_VALIDATE_RUN_ID,
        )
    }
}
