package com.wingedsheep.gym.matchup

data class GrixisRunnerSurfaceInventory(
    val workflowFiles: Map<String, String>,
    val commandFiles: Map<String, String>,
    val publicMethods: Map<String, Set<String>>,
)

data class GrixisRunnerSurfacePreflightResult(
    val errors: List<String>,
    val workflowFilesAudited: Int,
    val commandFilesAudited: Int,
    val classesAudited: Int,
    val officialSeedsGenerated: Int = 0,
    val officialGamesInitialized: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val green: Boolean get() = errors.isEmpty()
}

/** Pure inventory inspection. It cannot discover files, execute commands, or initialize games. */
object PestControlTierOneGrixisRunnerSurfacePreflight {
    private val forbiddenContent = listOf(
        PEST_GRIXIS_SMOKE_BLOCK_ID,
        "PestControlTierOneGrixisExecutionContract",
        "PestControlTierOneGrixisOfficialInitializationBoundary",
        "tier-one-grixis-execution",
    )
    private val forbiddenMethodNames = setOf(
        "execute", "run", "main", "initializeOfficial", "generateSeeds", "freezeSeeds", "writeArtifact",
    )

    fun inspect(inventory: GrixisRunnerSurfaceInventory): GrixisRunnerSurfacePreflightResult {
        val errors = mutableListOf<String>()
        (inventory.workflowFiles + inventory.commandFiles).forEach { (path, content) ->
            forbiddenContent.forEach { token ->
                if (content.contains(token, ignoreCase = true)) errors += "runner surface reference in $path"
            }
        }
        inventory.publicMethods.forEach { (className, methods) ->
            methods.intersect(forbiddenMethodNames).forEach { method ->
                errors += "forbidden public method $className.$method"
            }
        }
        val initializerMethods = inventory.publicMethods["PestControlTierOneGrixisInitializationContract"].orEmpty()
        if (initializerMethods != setOf("initializeConstructionFixture")) {
            errors += "initializer surface mismatch"
        }
        val boundaryMethods = inventory.publicMethods["PestControlTierOneGrixisOfficialInitializationBoundary"].orEmpty()
        if (boundaryMethods != setOf("inspect")) errors += "official boundary surface mismatch"
        val executionMethods = inventory.publicMethods["PestControlTierOneGrixisExecutionContract"].orEmpty()
        if (executionMethods != setOf("inspect")) errors += "execution contract surface mismatch"
        val singleGameMethods = inventory.publicMethods["PestControlTierOneGrixisDisabledSingleGame"].orEmpty()
        if (singleGameMethods != setOf("inspect")) errors += "single-game surface mismatch"
        return GrixisRunnerSurfacePreflightResult(
            errors = errors.distinct(),
            workflowFilesAudited = inventory.workflowFiles.size,
            commandFilesAudited = inventory.commandFiles.size,
            classesAudited = inventory.publicMethods.size,
        )
    }
}
