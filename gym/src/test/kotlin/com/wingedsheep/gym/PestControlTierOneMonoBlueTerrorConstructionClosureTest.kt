package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneMonoBlueTerrorConstructionClosureTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("complete repository construction closes without authorizing a vector") {
        val surface = terrorClosureRunnerSurface()
        val result =
            PestControlTierOneMonoBlueTerrorConstructionClosure.inspect(registry, surface)

        result.errors shouldBe emptyList()
        result.constructionReady shouldBe true
        result.status shouldBe PEST_MONO_BLUE_TERROR_CONSTRUCTION_READY_STATUS
        result.closureSha256 shouldBe PEST_MONO_BLUE_TERROR_CONSTRUCTION_CLOSURE_SHA256
        result.vectorCreationAuthorized shouldBe false
        result.vectorIdentityPresent shouldBe false
        result.officialAssignments shouldBe 0
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.artifactsWritten shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("surface failure blocks closure without granting authority") {
        val failedSurface =
            terrorClosureRunnerSurface().copy(errors = listOf("invented runner surface"))
        val result =
            PestControlTierOneMonoBlueTerrorConstructionClosure.inspect(
                registry,
                failedSurface,
            )

        result.constructionReady shouldBe false
        result.status shouldBe "CONSTRUCTION_BLOCKED"
        result.errors.shouldContain("runner surface preflight is not green")
        result.errors.shouldContain("construction closure hash mismatch")
        result.vectorCreationAuthorized shouldBe false
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }
})

private fun terrorClosureRunnerSurface(): MonoBlueTerrorRunnerSurfacePreflightResult {
    val root = terrorClosureRepositoryRoot()
    val commands = listOf(
        "game-server/src/main",
        "gym-server/src/main",
        "mtgish-tooling/src/main",
    ).flatMap { relative ->
        terrorClosureTextFiles(root.resolve(relative)).entries
    }.associate { it.toPair() }

    val types = listOf(
        PestControlTierOneMonoBlueTerrorInitializationContract::class.java,
        PestControlTierOneMonoBlueTerrorOfficialInitializationBoundary::class.java,
        PestControlTierOneMonoBlueTerrorExecutionContract::class.java,
        PestControlTierOneMonoBlueTerrorDisabledSingleGame::class.java,
        PestControlTierOneMonoBlueTerrorDisabledFourCellPlan::class.java,
        PestControlTierOneMonoBlueTerrorDisabledAssignmentSchema::class.java,
        PestControlTierOneMonoBlueTerrorDisabledSyntheticProvenance::class.java,
        PestControlTierOneMonoBlueTerrorDisabledPreExecutionManifest::class.java,
        PestControlTierOneMonoBlueTerrorConstructionClosure::class.java,
    )

    return PestControlTierOneMonoBlueTerrorRunnerSurfacePreflight.inspect(
        MonoBlueTerrorRunnerSurfaceInventory(
            workflowFiles = terrorClosureTextFiles(root.resolve(".github/workflows")),
            commandFiles = commands,
            publicMethods = types.associate { type ->
                type.simpleName to terrorClosurePublicMethods(type)
            },
        )
    )
}

private fun terrorClosureRepositoryRoot(): Path {
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

private fun terrorClosureTextFiles(root: Path): Map<String, String> {
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

private fun terrorClosurePublicMethods(type: Class<*>): Set<String> =
    type.declaredMethods
        .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
        .map { it.name }
        .toSet()
