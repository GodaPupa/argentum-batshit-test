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

class PestControlTierOneGrixisConstructionClosureTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
    }

    test("complete repository construction closes without authorizing a vector") {
        val surface = closureRunnerSurface()
        val result = PestControlTierOneGrixisConstructionClosure.inspect(registry, surface)

        result.errors shouldBe emptyList()
        result.constructionReady shouldBe true
        result.status shouldBe PEST_GRIXIS_CONSTRUCTION_READY_STATUS
        result.closureSha256 shouldBe PEST_GRIXIS_CONSTRUCTION_CLOSURE_SHA256
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
        val failedSurface = closureRunnerSurface().copy(errors = listOf("invented runner surface"))
        val result = PestControlTierOneGrixisConstructionClosure.inspect(registry, failedSurface)

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

private fun closureRunnerSurface(): GrixisRunnerSurfacePreflightResult {
    val root = closureRepositoryRoot()
    val commands = listOf(
        "game-server/src/main",
        "gym-server/src/main",
        "mtgish-tooling/src/main",
    ).flatMap { relative -> closureTextFiles(root.resolve(relative)).entries }.associate { it.toPair() }
    val types = listOf(
        PestControlTierOneGrixisInitializationContract::class.java,
        PestControlTierOneGrixisOfficialInitializationBoundary::class.java,
        PestControlTierOneGrixisExecutionContract::class.java,
        PestControlTierOneGrixisDisabledSingleGame::class.java,
        PestControlTierOneGrixisDisabledFourCellPlan::class.java,
        PestControlTierOneGrixisDisabledAssignmentSchema::class.java,
        PestControlTierOneGrixisDisabledSyntheticProvenance::class.java,
        PestControlTierOneGrixisDisabledPreExecutionManifest::class.java,
        PestControlTierOneGrixisConstructionClosure::class.java,
    )
    return PestControlTierOneGrixisRunnerSurfacePreflight.inspect(
        GrixisRunnerSurfaceInventory(
            workflowFiles = closureTextFiles(root.resolve(".github/workflows")),
            commandFiles = commands,
            publicMethods = types.associate { type -> type.simpleName to closurePublicMethods(type) },
        )
    )
}

private fun closureRepositoryRoot(): Path {
    var candidate: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve(".github/workflows")) &&
            Files.isDirectory(candidate.resolve("gym/src/main"))
        ) return candidate
        candidate = candidate.parent
    }
    error("repository root not found")
}

private fun closureTextFiles(root: Path): Map<String, String> {
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

private fun closurePublicMethods(type: Class<*>): Set<String> = type.declaredMethods
    .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
    .map { it.name }
    .toSet()
