package com.wingedsheep.gym.matchup

data class MonoBlueTerrorRunnerSurfaceInventory(
    val workflowFiles: Map<String, String>,
    val commandFiles: Map<String, String>,
    val publicMethods: Map<String, Set<String>>,
)

data class MonoBlueTerrorRunnerSurfacePreflightResult(
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
object PestControlTierOneMonoBlueTerrorRunnerSurfacePreflight {
    private val forbiddenContent = listOf(
        PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID,
        "PestControlTierOneMonoBlueTerrorExecutionContract",
        "PestControlTierOneMonoBlueTerrorOfficialInitializationBoundary",
        "tier-one-mono-blue-terror-execution",
    )

    private val forbiddenMethodNames = setOf(
        "execute",
        "run",
        "main",
        "initializeOfficial",
        "generateSeeds",
        "freezeSeeds",
        "writeArtifact",
    )

    fun inspect(
        inventory: MonoBlueTerrorRunnerSurfaceInventory,
    ): MonoBlueTerrorRunnerSurfacePreflightResult {
        val errors = mutableListOf<String>()

        (inventory.workflowFiles + inventory.commandFiles).forEach { (path, content) ->
            forbiddenContent.forEach { token ->
                if (content.contains(token, ignoreCase = true)) {
                    errors += "runner surface reference in $path"
                }
            }
        }

        inventory.publicMethods.forEach { (className, methods) ->
            methods.intersect(forbiddenMethodNames).forEach { method ->
                errors += "forbidden public method $className.$method"
            }
        }

        val initializerMethods =
            inventory.publicMethods[
                "PestControlTierOneMonoBlueTerrorInitializationContract"
            ].orEmpty()
        if (initializerMethods != setOf("initializeConstructionFixture")) {
            errors += "initializer surface mismatch"
        }

        val boundaryMethods =
            inventory.publicMethods[
                "PestControlTierOneMonoBlueTerrorOfficialInitializationBoundary"
            ].orEmpty()
        if (boundaryMethods != setOf("inspect")) {
            errors += "official boundary surface mismatch"
        }

        val executionMethods =
            inventory.publicMethods[
                "PestControlTierOneMonoBlueTerrorExecutionContract"
            ].orEmpty()
        if (executionMethods != setOf("inspect")) {
            errors += "execution contract surface mismatch"
        }

        val singleGameMethods =
            inventory.publicMethods[
                "PestControlTierOneMonoBlueTerrorDisabledSingleGame"
            ].orEmpty()
        if (singleGameMethods != setOf("inspect")) {
            errors += "single-game surface mismatch"
        }

        val fourCellMethods =
            inventory.publicMethods[
                "PestControlTierOneMonoBlueTerrorDisabledFourCellPlan"
            ].orEmpty()
        if (fourCellMethods != setOf("inspect")) {
            errors += "four-cell plan surface mismatch"
        }

        val assignmentMethods =
            inventory.publicMethods[
                "PestControlTierOneMonoBlueTerrorDisabledAssignmentSchema"
            ].orEmpty()
        if (assignmentMethods != setOf("inspect")) {
            errors += "assignment schema surface mismatch"
        }

        return MonoBlueTerrorRunnerSurfacePreflightResult(
            errors = errors.distinct(),
            workflowFilesAudited = inventory.workflowFiles.size,
            commandFilesAudited = inventory.commandFiles.size,
            classesAudited = inventory.publicMethods.size,
        )
    }
}
