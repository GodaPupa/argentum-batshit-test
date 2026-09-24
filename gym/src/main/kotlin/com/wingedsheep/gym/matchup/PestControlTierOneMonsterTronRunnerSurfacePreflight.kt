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
    private val forbiddenContent = listOf(
        "tier-one-monster-tron-official-execution",
        "PestControlTierOneMonsterTronOfficialExecutionRunner",
        "PestControlTierOneMonsterTronOfficialInitializer",
        "PEST_MONSTER_TRON_OFFICIAL_VECTOR",
        "PEST_MONSTER_TRON_EXECUTE",
    )

    private val forbiddenMethodNames = setOf(
        "execute",
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
            forbiddenContent.forEach { token ->
                if (content.contains(token, ignoreCase = true)) {
                    errors += "official Monster Tron runner surface reference in $path"
                }
            }
        }

        inventory.publicMethods.forEach { (className, methods) ->
            methods.intersect(forbiddenMethodNames).forEach { method ->
                errors += "forbidden public method $className.$method"
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
