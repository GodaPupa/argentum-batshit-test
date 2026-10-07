package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.MonsterTronRunnerSurfaceInventory
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronOfficialInitializationBoundary
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronPolicyReadiness
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronRunnerContract
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronOneShotBoundary
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronRunnerSurfacePreflight
import com.wingedsheep.gym.matchup.TierOneMonsterTronRunnerState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneMonsterTronRunnerSurfacePreflightTest : FunSpec({
    test("repository permits only exact pinned reviewed Monster Tron workflows without executing games") {
        val root = monsterTronRepositoryRoot()
        val workflows = monsterTronTextFiles(root.resolve(".github/workflows"))
        val commands = listOf(
            "game-server/src/main",
            "gym-server/src/main",
            "mtgish-tooling/src/main",
        ).flatMap { relative ->
            monsterTronTextFiles(root.resolve(relative)).entries
        }.associate { it.toPair() }

        val result = PestControlTierOneMonsterTronRunnerSurfacePreflight.inspect(
            MonsterTronRunnerSurfaceInventory(
                workflowFiles = workflows,
                commandFiles = commands,
                publicMethods = mapOf(
                    "PestControlTierOneMonsterTronOneShotBoundary" to
                        monsterTronPublicMethods(PestControlTierOneMonsterTronOneShotBoundary::class.java),
                    "PestControlTierOneMonsterTronPolicyReadiness" to
                        monsterTronPublicMethods(
                            PestControlTierOneMonsterTronPolicyReadiness::class.java
                        ),
                    "PestControlTierOneMonsterTronRunnerContract" to
                        monsterTronPublicMethods(
                            PestControlTierOneMonsterTronRunnerContract::class.java
                        ),
                    "PestControlTierOneMonsterTronOfficialInitializationBoundary" to
                        monsterTronPublicMethods(
                            PestControlTierOneMonsterTronOfficialInitializationBoundary::class.java
                        ),
                ),
            )
        )

        println("runner_surface_errors=" + result.errors.joinToString(" | "))
        result.errors shouldBe emptyList()
        result.green shouldBe true
        (result.workflowFilesAudited > 0) shouldBe true
        (result.commandFilesAudited > 0) shouldBe true
        result.classesAudited shouldBe 4
        result.runnerState shouldBe TierOneMonsterTronRunnerState.DISABLED
        result.officialGamesAuthorized shouldBe 0
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.officialActionsSubmitted shouldBe 0
        result.outcomeExposure shouldBe 0
        result.guardedOfficialWorkflowPresent shouldBe true
        result.guardedR1OfficialWorkflowPresent shouldBe true

        val report = buildString {
            appendLine("schema=pest-monster-tron-runner-surface-preflight-v3")
            appendLine("protocol_id=PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1")
            appendLine("workflow_files_audited=${result.workflowFilesAudited}")
            appendLine("command_files_audited=${result.commandFilesAudited}")
            appendLine("classes_audited=${result.classesAudited}")
            appendLine("legacy_construction_runner_state=${result.runnerState}")
            appendLine("guarded_official_workflow_present=${result.guardedOfficialWorkflowPresent}")
            appendLine("guarded_r1_official_workflow_present=${result.guardedR1OfficialWorkflowPresent}")
            appendLine("official_games_authorized=0")
            appendLine("official_seeds_generated=0")
            appendLine("official_games_initialized=0")
            appendLine("official_actions=0")
            appendLine("outcome_exposure=0")
            appendLine("status=GUARDED_ONE_SHOT_ACTIVATION_SURFACE_VERIFIED_NO_GAMEPLAY")
        }
        println(report)
        System.getenv("PEST_MONSTER_TRON_RUNNER_PREFLIGHT_REPORT")?.let { raw ->
            val path = Path.of(raw)
            Files.createDirectories(path.parent)
            Files.writeString(path, report)
        }
    }

    test("exact construction workflow allowlist rejects relocation mutation and activation") {
        val root = monsterTronRepositoryRoot()
        val path = PestControlTierOneMonsterTronRunnerSurfacePreflight.CONSTRUCTION_WORKFLOW_PATH
        val content = Files.readString(root.resolve(path))
        val methods = mapOf("PestControlTierOneMonsterTronPolicyReadiness" to
            setOf("validationErrors", "executionActivationErrors"))
        fun inspect(files: Map<String, String>) = PestControlTierOneMonsterTronRunnerSurfacePreflight.inspect(
            MonsterTronRunnerSurfaceInventory(files, emptyMap(), methods))
        inspect(mapOf(path to content)).green shouldBe true
        inspect(mapOf(".github/workflows/invented.yml" to content)).green shouldBe false
        inspect(mapOf(path to content.replace("contents: read", "contents: write"))).green shouldBe false
        inspect(mapOf(path to (content + "\n# unauthorized activation\n"))).green shouldBe false
        inspect(mapOf(".github/workflows/invented.yml" to "env: {PEST_MONSTER_TRON_OFFICIAL_MODE: EXECUTE}"))
            .green shouldBe false
        Files.exists(root.resolve(".github/workflows/pest-control-tier-one-monster-tron-official-smoke.yml")) shouldBe true
        content.contains("workflow" + "_dispatch") shouldBe false
        content.contains("\n  push:") shouldBe false
        content.contains("AUTOMATIC_ONE_SHOT_" + "MONSTER_TRON_EXECUTION") shouldBe false
    }

    test("official workflow accepts only exact reviewed bytes and fixed source") {
        val root = monsterTronRepositoryRoot()
        val path = PestControlTierOneMonsterTronRunnerSurfacePreflight.OFFICIAL_WORKFLOW_PATH
        val content = Files.readString(root.resolve(path))
        val methods = mapOf("PestControlTierOneMonsterTronPolicyReadiness" to
            setOf("validationErrors", "executionActivationErrors"))
        fun inspect(files: Map<String, String>) = PestControlTierOneMonsterTronRunnerSurfacePreflight.inspect(
            MonsterTronRunnerSurfaceInventory(files, emptyMap(), methods))
        inspect(mapOf(path to content)).green shouldBe true
        inspect(mapOf(path to content)).guardedOfficialWorkflowPresent shouldBe true
        inspect(mapOf(".github/workflows/another-official.yml" to content)).green shouldBe false
        inspect(mapOf(path to content.replace("8a425d8532395e3d4262fbb35cfbac71e96feeb5", "main"))).green shouldBe false
        inspect(mapOf(path to content.replace("github.run_attempt == 1", "true"))).green shouldBe false
        inspect(mapOf(path to content.replace("if: always()", "if: success()"))).green shouldBe false
        content.contains("--verify-receipt") shouldBe false // The sealed source C performs remote verification.
        content.contains("--rerun-tasks --no-build-cache") shouldBe true
        content.contains("--kill-after=120s 5h") shouldBe true
    }

    test("R1 official workflow accepts only exact frozen bytes source branch and first attempt") {
        val root = monsterTronRepositoryRoot()
        val path = PestControlTierOneMonsterTronRunnerSurfacePreflight.R1_OFFICIAL_WORKFLOW_PATH
        val content = Files.readString(root.resolve(path))
        val methods = mapOf("PestControlTierOneMonsterTronPolicyReadiness" to
            setOf("validationErrors", "executionActivationErrors"))
        fun inspect(files: Map<String, String>) = PestControlTierOneMonsterTronRunnerSurfacePreflight.inspect(
            MonsterTronRunnerSurfaceInventory(files, emptyMap(), methods))
        inspect(mapOf(path to content)).green shouldBe true
        inspect(mapOf(path to content)).guardedR1OfficialWorkflowPresent shouldBe true
        inspect(mapOf(".github/workflows/invented-r1.yml" to content)).green shouldBe false
        inspect(mapOf(path to content.replace("dc22d47efa7b67442a6120b3a2aa8802c75d5a5a", "main"))).green shouldBe false
        inspect(mapOf(path to content.replace("refs/heads/pest-control/tier1-monster-tron-r1-official-smoke", "refs/heads/pest-control/tier1-monster-tron-r1-invented"))).green shouldBe false
        inspect(mapOf(path to content.replace("github.run_attempt == 1", "true"))).green shouldBe false
        inspect(mapOf(path to content.replace("if: always()", "if: success()"))).green shouldBe false
        content.contains("workflow" + "_dispatch") shouldBe true
        content.contains("EXECUTION_SOURCE_SHA: dc22d47efa7b67442a6120b3a2aa8802c75d5a5a") shouldBe true
        content.contains("github.run_attempt == 1") shouldBe true
        content.contains("refs/heads/pest-control/tier1-monster-tron-r1-official-smoke") shouldBe true
        content.contains("--kill-after=120s 5h") shouldBe true
        content.contains("if: always()") shouldBe true
    }

    test("historical V3 and V3.1 qualification workflows remain exact pinned and fail closed") {
        val root = monsterTronRepositoryRoot()
        val methods = mapOf("PestControlTierOneMonsterTronPolicyReadiness" to
            setOf("validationErrors", "executionActivationErrors"))
        fun inspect(files: Map<String, String>) = PestControlTierOneMonsterTronRunnerSurfacePreflight.inspect(
            MonsterTronRunnerSurfaceInventory(files, emptyMap(), methods))

        for (path in listOf(
            PestControlTierOneMonsterTronRunnerSurfacePreflight.V3_REBIND_QUALIFICATION_WORKFLOW_PATH,
            PestControlTierOneMonsterTronRunnerSurfacePreflight.V3_1_REBIND_QUALIFICATION_WORKFLOW_PATH,
        )) {
            val content = Files.readString(root.resolve(path))
            inspect(mapOf(path to content)).green shouldBe true
            inspect(mapOf(".github/workflows/invented-historical-qualify.yml" to content)).green shouldBe false
            inspect(mapOf(path to (content + "\n# unauthorized mutation\n"))).green shouldBe false
            content.contains("workflow" + "_dispatch") shouldBe false
        }
    }

    test("invented execution workflow and callable method fail closed") {
        val result = PestControlTierOneMonsterTronRunnerSurfacePreflight.inspect(
            MonsterTronRunnerSurfaceInventory(
                workflowFiles = mapOf(
                    "bad.yml" to "run: tier-one-monster-tron-official-execution"
                ),
                commandFiles = mapOf(
                    "Bad.kt" to "PestControlTierOneMonsterTronOfficialExecutionRunner"
                ),
                publicMethods = mapOf(
                    "PestControlTierOneMonsterTronPolicyReadiness" to
                        setOf("validationErrors", "executionActivationErrors", "execute"),
                ),
            )
        )

        result.green shouldBe false
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }
})

private fun monsterTronRepositoryRoot(): Path {
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

private fun monsterTronTextFiles(root: Path): Map<String, String> {
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

private fun monsterTronPublicMethods(type: Class<*>): Set<String> =
    type.declaredMethods
        .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
        .map { it.name }
        .toSet()
