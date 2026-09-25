package com.wingedsheep.gym.matchup

data class MonsterTronRunnerSurfaceInventory(
    val workflowFiles: Map<String, String>,
    val commandFiles: Map<String, String>,
    val publicMethods: Map<String, Set<String>>,
)

data class MonsterTronRunnerSurfacePreflightResult(
    val errors: List<String>,
    val workflowFilesAudited: Int,
    val commandFilesAudited: Int,
    val classesAudited: Int,
    val runnerState: TierOneMonsterTronRunnerState = TierOneMonsterTronRunnerState.DISABLED,
    val officialGamesAuthorized: Int = 0,
    val officialSeedsGenerated: Int = 0,
    val officialGamesInitialized: Int = 0,
    val officialActionsSubmitted: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val green: Boolean get() = errors.isEmpty()
}

/**
 * Pure repository/API surface audit for the frozen Monster Tron protocol.
 *
 * This object cannot discover files, create seeds, initialize a game, submit an action, or expose
 * an outcome. The caller supplies text/public-method inventories and this class only inspects them.
 */
object PestControlTierOneMonsterTronRunnerSurfacePreflight {
    internal const val CONSTRUCTION_WORKFLOW_PATH = ".github/workflows/pest-control-tier-one-monster-tron-one-shot-construction.yml"
    internal const val CONSTRUCTION_WORKFLOW_SHA256 = "855acc62245d09ac6fe2eb7884f2d434c56ae8db2df38613a980f2beb9ce65be"

    private val forbiddenContent = listOf(
        "tier-one-monster-tron-official-execution",
        "PestControlTierOneMonsterTronOfficialExecutionRunner",
        "PestControlTierOneMonsterTronOfficialInitializer",
        "PEST_MONSTER_TRON_OFFICIAL_VECTOR",
        "PEST_MONSTER_TRON_EXECUTE",
        "PEST_MONSTER_TRON_OFFICIAL_MODE",
        "PestControlTierOneMonsterTronOneShotBoundary",
        "pest-monster-tron-one-shot-claim.py",
    )

    private val forbiddenMethodNames = setOf(
        "execute",
        "executeFromEnvironment",
        "run",
        "main",
        "initializeOfficial",
        "initializeGame",
        "generateSeeds",
        "freezeSeeds",
        "consumeSeed",
        "submitAction",
        "writeArtifact",
        "exposeOutcome",
    )

    fun inspect(
        inventory: MonsterTronRunnerSurfaceInventory,
    ): MonsterTronRunnerSurfacePreflightResult {
        val errors = mutableListOf<String>()

        (inventory.workflowFiles + inventory.commandFiles).forEach { (path, content) ->
            val constructionPath = path.replace('\\', '/').endsWith(CONSTRUCTION_WORKFLOW_PATH)
            if (constructionPath) {
                if (monsterTronDigest(content.toByteArray(Charsets.UTF_8)) != CONSTRUCTION_WORKFLOW_SHA256) {
                    errors += "guarded construction workflow bytes changed: $path"
                }
            }
            forbiddenContent.forEach { token ->
                if (!constructionPath && content.contains(token, ignoreCase = true)) {
                    errors += "official Monster Tron runner surface reference in $path"
                }
            }
        }

        inventory.publicMethods.forEach { (className, methods) ->
            methods.intersect(forbiddenMethodNames).forEach { method ->
                if (className != "PestControlTierOneMonsterTronOneShotBoundary" || method != "executeFromEnvironment") {
                    errors += "forbidden public method $className.$method"
                }
            }
            if (className == "PestControlTierOneMonsterTronOneShotBoundary" && methods != setOf("executeFromEnvironment")) {
                errors += "sealed one-shot public surface mismatch"
            }
        }

        val policyMethods =
            inventory.publicMethods["PestControlTierOneMonsterTronPolicyReadiness"].orEmpty()
        if (policyMethods != setOf("validationErrors", "executionActivationErrors")) {
            errors += "policy-readiness public surface mismatch"
        }

        return MonsterTronRunnerSurfacePreflightResult(
            errors = errors.distinct(),
            workflowFilesAudited = inventory.workflowFiles.size,
            commandFilesAudited = inventory.commandFiles.size,
            classesAudited = inventory.publicMethods.size,
        )
    }
}
