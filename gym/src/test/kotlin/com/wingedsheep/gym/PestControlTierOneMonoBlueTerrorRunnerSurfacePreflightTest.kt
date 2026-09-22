package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneMonoBlueTerrorRunnerSurfacePreflightTest : FunSpec({
    test("repository workflows commands and public methods expose no official Terror runner") {
        val root = terrorRepositoryRoot()
        val workflows = terrorTextFiles(root.resolve(".github/workflows"))
        val commands = listOf(
            "game-server/src/main",
            "gym-server/src/main",
            "mtgish-tooling/src/main",
        ).flatMap { relative ->
            terrorTextFiles(root.resolve(relative)).entries
        }.associate { it.toPair() }

        val inventory = MonoBlueTerrorRunnerSurfaceInventory(
            workflowFiles = workflows,
            commandFiles = commands,
            publicMethods = mapOf(
                "PestControlTierOneMonoBlueTerrorInitializationContract" to
                    terrorPublicMethods(
                        PestControlTierOneMonoBlueTerrorInitializationContract::class.java
                    ),
                "PestControlTierOneMonoBlueTerrorOfficialInitializationBoundary" to
                    terrorPublicMethods(
                        PestControlTierOneMonoBlueTerrorOfficialInitializationBoundary::class.java
                    ),
                "PestControlTierOneMonoBlueTerrorExecutionContract" to
                    terrorPublicMethods(
                        PestControlTierOneMonoBlueTerrorExecutionContract::class.java
                    ),
                "PestControlTierOneMonoBlueTerrorDisabledSingleGame" to
                    terrorPublicMethods(
                        PestControlTierOneMonoBlueTerrorDisabledSingleGame::class.java
                    ),
            ),
        )

        val result = PestControlTierOneMonoBlueTerrorRunnerSurfacePreflight.inspect(inventory)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        (result.workflowFilesAudited > 0) shouldBe true
        (result.commandFilesAudited > 0) shouldBe true
        result.classesAudited shouldBe 4
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("invented workflow command and official initializer surfaces fail closed") {
        val result = PestControlTierOneMonoBlueTerrorRunnerSurfacePreflight.inspect(
            MonoBlueTerrorRunnerSurfaceInventory(
                workflowFiles = mapOf(
                    "bad.yml" to "run: tier-one-mono-blue-terror-execution"
                ),
                commandFiles = mapOf(
                    "Bad.kt" to "PestControlTierOneMonoBlueTerrorExecutionContract"
                ),
                publicMethods = mapOf(
                    "PestControlTierOneMonoBlueTerrorInitializationContract" to
                        setOf("initializeOfficial"),
                    "PestControlTierOneMonoBlueTerrorOfficialInitializationBoundary" to
                        setOf("inspect"),
                    "PestControlTierOneMonoBlueTerrorExecutionContract" to
                        setOf("inspect", "execute"),
                    "PestControlTierOneMonoBlueTerrorDisabledSingleGame" to
                        setOf("inspect"),
                ),
            )
        )

        result.green shouldBe false
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }
})

private fun terrorRepositoryRoot(): Path {
    var candidate: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    while (candidate != null) {
        if (
            Files.isDirectory(candidate.resolve(".github/workflows")) &&
            Files.isDirectory(candidate.resolve("gym/src/main"))
        ) {
            return candidate
        }
        candidate = candidate.parent
    }
    error("repository root not found")
}

private fun terrorTextFiles(root: Path): Map<String, String> {
    if (!Files.isDirectory(root)) return emptyMap()

    return Files.walk(root).use { paths ->
        paths.filter(Files::isRegularFile)
            .filter { path ->
                path.toString().endsWith(".kt") ||
                    path.toString().endsWith(".kts") ||
                    path.toString().endsWith(".yml") ||
                    path.toString().endsWith(".yaml") ||
                    path.toString().endsWith(".sh")
            }
            .toList()
            .associate { path -> path.toString() to Files.readString(path) }
    }
}

private fun terrorPublicMethods(type: Class<*>): Set<String> =
    type.declaredMethods
        .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
        .map { it.name }
        .toSet()
