package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneGrixisRunnerSurfacePreflightTest : FunSpec({
    test("repository workflows commands and public methods expose no official Grixis runner") {
        val root = repositoryRoot()
        val workflows = textFiles(root.resolve(".github/workflows"))
        val commands = listOf(
            "game-server/src/main",
            "gym-server/src/main",
            "mtgish-tooling/src/main",
        ).flatMap { relative ->
            textFiles(root.resolve(relative)).entries
        }.associate { it.toPair() }
        val inventory = GrixisRunnerSurfaceInventory(
            workflowFiles = workflows,
            commandFiles = commands,
            publicMethods = mapOf(
                "PestControlTierOneGrixisInitializationContract" to publicMethods(
                    PestControlTierOneGrixisInitializationContract::class.java,
                ),
                "PestControlTierOneGrixisOfficialInitializationBoundary" to publicMethods(
                    PestControlTierOneGrixisOfficialInitializationBoundary::class.java,
                ),
                "PestControlTierOneGrixisExecutionContract" to publicMethods(
                    PestControlTierOneGrixisExecutionContract::class.java,
                ),
                "PestControlTierOneGrixisDisabledSingleGame" to publicMethods(
                    PestControlTierOneGrixisDisabledSingleGame::class.java,
                ),
                "PestControlTierOneGrixisDisabledFourCellPlan" to publicMethods(
                    PestControlTierOneGrixisDisabledFourCellPlan::class.java,
                ),
            ),
        )
        val result = PestControlTierOneGrixisRunnerSurfacePreflight.inspect(inventory)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        (result.workflowFilesAudited > 0) shouldBe true
        (result.commandFilesAudited > 0) shouldBe true
        result.classesAudited shouldBe 5
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("invented workflow command and official initializer surfaces fail closed") {
        val result = PestControlTierOneGrixisRunnerSurfacePreflight.inspect(
            GrixisRunnerSurfaceInventory(
                workflowFiles = mapOf("bad.yml" to "run: tier-one-grixis-execution"),
                commandFiles = mapOf("Bad.kt" to "PestControlTierOneGrixisExecutionContract"),
                publicMethods = mapOf(
                    "PestControlTierOneGrixisInitializationContract" to setOf("initializeOfficial"),
                    "PestControlTierOneGrixisOfficialInitializationBoundary" to setOf("inspect"),
                    "PestControlTierOneGrixisExecutionContract" to setOf("inspect", "execute"),
                    "PestControlTierOneGrixisDisabledSingleGame" to setOf("inspect"),
                    "PestControlTierOneGrixisDisabledFourCellPlan" to setOf("inspect"),
                ),
            )
        )

        result.green shouldBe false
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }
})

private fun repositoryRoot(): Path {
    var candidate: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve(".github/workflows")) &&
            Files.isDirectory(candidate.resolve("gym/src/main"))
        ) return candidate
        candidate = candidate.parent
    }
    error("repository root not found")
}

private fun textFiles(root: Path): Map<String, String> {
    if (!Files.isDirectory(root)) return emptyMap()
    return Files.walk(root).use { paths ->
        paths.filter(Files::isRegularFile)
            .filter { path -> path.toString().endsWith(".kt") || path.toString().endsWith(".kts") ||
                path.toString().endsWith(".yml") || path.toString().endsWith(".yaml") ||
                path.toString().endsWith(".sh")
            }
            .toList()
            .associate { path -> path.toString() to Files.readString(path) }
    }
}

private fun publicMethods(type: Class<*>): Set<String> = type.declaredMethods
    .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
    .map { it.name }
    .toSet()
