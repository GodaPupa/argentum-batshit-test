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

class PestControlTierOneGrixisFinalHarnessReadinessTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
    }

    test("complete frozen harness is green ready and fail closed without execution authority") {
        val audit = finalHarnessSurfaceAudit()
        val result = PestControlTierOneGrixisFinalHarnessReadiness.inspect(registry, audit)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.harnessReady shouldBe true
        result.failClosed shouldBe true
        result.status shouldBe PEST_GRIXIS_FINAL_HARNESS_READY_STATUS
        result.readinessSha256 shouldBe PEST_GRIXIS_FINAL_HARNESS_READINESS_SHA256
        (result.workflowFilesAudited > 0) shouldBe true
        (result.commandFilesAudited > 0) shouldBe true
        result.officialVectorFrozen shouldBe true
        result.officialArtifactValidationCount shouldBe 1
        result.officialAssignmentRowsBound shouldBe 4
        result.officialSeedValuesExposed shouldBe 0
        result.officialSeedsConsumed shouldBe 0
        result.initializerEnabled shouldBe false
        result.runnerEnabled shouldBe false
        result.executionAuthorized shouldBe false
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.outcomeArtifactsWritten shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("any production surface blocks readiness without changing official counters") {
        val audit = finalHarnessSurfaceAudit().copy(
            manualDispatchEntrypoints = 1,
            officialArtifactDownloadEntrypoints = 1,
            productionCommandEntrypoints = 1,
            publicRunnerMethods = 1,
        )
        val result = PestControlTierOneGrixisFinalHarnessReadiness.inspect(registry, audit)

        result.green shouldBe false
        result.harnessReady shouldBe false
        result.failClosed shouldBe false
        result.errors.shouldContain("manual dispatch entrypoint exists")
        result.errors.shouldContain("official artifact download entrypoint exists")
        result.errors.shouldContain("production command entrypoint exists")
        result.errors.shouldContain("public runner method exists")
        result.errors.shouldContain("final harness readiness proof mismatch")
        result.officialSeedsConsumed shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("final readiness exposes inspection only") {
        val methods = PestControlTierOneGrixisFinalHarnessReadiness::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
            .map { it.name }
            .toSet()
        methods shouldBe setOf("inspect")
    }
})

private fun finalHarnessSurfaceAudit(): GrixisFinalHarnessSurfaceAudit {
    val root = finalHarnessRepositoryRoot()
    val workflows = finalHarnessTextFiles(root.resolve(".github/workflows"))
        .filterKeys { it.contains("tier-one-grixis") }
    val commands = listOf(
        "game-server/src/main",
        "gym-server/src/main",
        "mtgish-tooling/src/main",
    ).flatMap { relative -> finalHarnessTextFiles(root.resolve(relative)).entries }.associate { it.toPair() }
    val downloadTokens = listOf(
        "10620940806",
        "PEST_GRIXIS_OFFICIAL_ARTIFACT_DIR",
        "VALIDATE_TIER_ONE_GRIXIS_OFFICIAL_ARTIFACT_BYTES_NO_GAMEPLAY",
        "/actions/artifacts/",
    )
    val commandTokens = listOf(
        PEST_GRIXIS_SMOKE_BLOCK_ID,
        "PestControlTierOneGrixisExecutionContract",
        "PestControlTierOneGrixisOfficialInitializationBoundary",
    )
    val runnerMethodNames = setOf(
        "execute", "run", "main", "initializeOfficial", "writeArtifact", "downloadArtifact",
    )
    val auditedTypes = listOf(
        PestControlTierOneGrixisDisabledOfficialArtifactLoader::class.java,
        PestControlTierOneGrixisExecutionAdmissionGate::class.java,
        PestControlTierOneGrixisOpaqueExecutionPlan::class.java,
        PestControlTierOneGrixisFinalHarnessReadiness::class.java,
    )
    return GrixisFinalHarnessSurfaceAudit(
        workflowFilesAudited = workflows.size,
        commandFilesAudited = commands.size,
        manualDispatchEntrypoints = workflows.values.count { it.contains("workflow_dispatch") },
        officialArtifactDownloadEntrypoints = workflows.values.count { content ->
            downloadTokens.any(content::contains)
        },
        productionCommandEntrypoints = commands.values.count { content ->
            commandTokens.any(content::contains)
        },
        publicRunnerMethods = auditedTypes.sumOf { type ->
            type.declaredMethods.count { method ->
                Modifier.isPublic(method.modifiers) && !method.isSynthetic && method.name in runnerMethodNames
            }
        },
    )
}

private fun finalHarnessRepositoryRoot(): Path {
    var candidate: Path? = Path.of(System.getProperty("user.dir")).toAbsolutePath()
    while (candidate != null) {
        if (Files.isDirectory(candidate.resolve(".github/workflows")) &&
            Files.isDirectory(candidate.resolve("gym/src/main"))
        ) return candidate
        candidate = candidate.parent
    }
    error("repository root not found")
}

private fun finalHarnessTextFiles(root: Path): Map<String, String> {
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
