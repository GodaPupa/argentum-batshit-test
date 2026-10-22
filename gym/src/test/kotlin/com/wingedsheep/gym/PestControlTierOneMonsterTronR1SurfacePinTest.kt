package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.MonsterTronRunnerSurfaceInventory
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronRunnerSurfacePreflight
import com.wingedsheep.gym.matchup.TierOneMonsterTronRunnerState
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneMonsterTronR1SurfacePinTest : FunSpec({
    val path = PestControlTierOneMonsterTronRunnerSurfacePreflight.R1_OFFICIAL_WORKFLOW_PATH
    val methods = mapOf("PestControlTierOneMonsterTronPolicyReadiness" to
        setOf("validationErrors", "executionActivationErrors"))
    fun inspect(files: Map<String, String>, publicMethods: Map<String, Set<String>> = methods) =
        PestControlTierOneMonsterTronRunnerSurfacePreflight.inspect(
            MonsterTronRunnerSurfaceInventory(files, emptyMap(), publicMethods))
    fun root(): Path = generateSequence(Path.of(System.getProperty("user.dir")).toAbsolutePath()) { it.parent }
        .first { Files.isRegularFile(it.resolve(path)) }

    test("exact historical R1 bytes are recognized without activating or renewing execution") {
        val content = Files.readString(root().resolve(path))
        val result = inspect(mapOf(path to content))
        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.runnerState shouldBe TierOneMonsterTronRunnerState.DISABLED
        result.officialGamesAuthorized shouldBe 0
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.officialActionsSubmitted shouldBe 0
        result.outcomeExposure shouldBe 0
        // This legacy indicator continues to mean the original workflow, not R1.
        result.guardedOfficialWorkflowPresent shouldBe false
        content.contains("assert today.isoformat() < '2026-10-12'") shouldBe true
        inspect(mapOf(root().resolve(path).toString() to content)).green shouldBe true
        inspect(mapOf(path.replace('/', '\\') to content)).green shouldBe true
    }

    test("R1 pin rejects changes to source activation admission claim and evidence gates") {
        val content = Files.readString(root().resolve(path))
        val mutations = listOf(
            "ed35c3035d41a740aac1657708b2023b624cfc47" to "main",
            "433df3310efe31c49f27034b50e6d8d7e60561f7" to "main",
            "github.event_name == 'workflow_dispatch'" to "true",
            "github.run_attempt == 1" to "true",
            "refs/heads/pest-control/tier1-monster-tron-r1-repaired-official-smoke" to "refs/heads/main",
            "assert today.isoformat() < '2026-10-12'" to "assert True",
            "e9ddafe0a7aec28cb07950f2f1d5908a31af677ff58415e809117575f46d6817" to "unverified",
            "8d860e451f20f38865b725b42d82feb714c725373dd8f3b32b8652b3eeb070ca" to "unverified",
            "pest-monster-tron-r1-one-shot-claim.py" to "skip-claim.py",
            "AUTOMATIC_ONE_SHOT_MONSTER_TRON_R1_EXECUTION" to "unbound",
            "contents: read" to "contents: write",
            "--kill-after=120s 5h" to "--kill-after=120s 6h",
            "--rerun-tasks --no-build-cache" to "",
            "if: always()" to "if: success()",
            "retention-days: 90" to "retention-days: 1",
        )
        mutations.forEach { (before, after) ->
            content.contains(before) shouldBe true
            val result = inspect(mapOf(path to content.replace(before, after)))
            result.green shouldBe false
            result.errors shouldBe listOf("guarded workflow bytes changed: $path")
        }
        inspect(mapOf(path to (content + "\n# changed\n"))).green shouldBe false
    }

    test("identical R1 bytes at another workflow or command path are rejected") {
        val content = Files.readString(root().resolve(path))
        listOf(
            ".github/workflows/invented-r1.yml",
            "scripts/pest-control-tier-one-monster-tron-r1-official-smoke.yml",
            "prefix$path",
            "$path.bak",
        ).forEach { relocated ->
            inspect(mapOf(relocated to content)).green shouldBe false
        }
        PestControlTierOneMonsterTronRunnerSurfacePreflight.inspect(
            MonsterTronRunnerSurfaceInventory(emptyMap(), mapOf("commands/run.sh" to content), methods)
        ).green shouldBe false
    }

    test("recognizing R1 does not relax callable execution surface checks") {
        val content = Files.readString(root().resolve(path))
        inspect(mapOf(path to content), methods + mapOf(
            "InventedRunner" to setOf("execute"),
        )).green shouldBe false
        inspect(mapOf(path to content), methods + mapOf(
            "PestControlTierOneMonsterTronOneShotBoundary" to setOf("executeFromEnvironment", "run"),
        )).green shouldBe false
        inspect(mapOf(path to content), mapOf(
            "PestControlTierOneMonsterTronPolicyReadiness" to setOf("validationErrors", "execute"),
        )).green shouldBe false
    }
})
