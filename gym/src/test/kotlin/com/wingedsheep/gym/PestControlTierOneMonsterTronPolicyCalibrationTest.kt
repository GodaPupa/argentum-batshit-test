package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.MonsterTronCascadeChoice
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronAdmission
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronPolicy
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronReadiness
import com.wingedsheep.gym.matchup.TierOneMonsterTronPolicyCalibration
import com.wingedsheep.gym.matchup.TierOneMonsterTronReadiness
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

class PestControlTierOneMonsterTronPolicyCalibrationTest : FunSpec({
    val calibration = TierOneMonsterTronPolicyCalibration()

    test("calibration is bound to the frozen exact-60 identities and consumes no official state") {
        PestControlTierOneMonsterTronPolicy.validationErrors(calibration) shouldBe emptyList()
        PestControlTierOneMonsterTronAdmission.mainCounts["Expedition Map"] shouldBe 4
        PestControlTierOneMonsterTronAdmission.mainCounts["Crop Rotation"] shouldBe 3
        PestControlTierOneMonsterTronAdmission.mainCounts["Maelstrom Colossus"] shouldBe 4
        PestControlTierOneMonsterTronAdmission.mainCounts["Boulderbranch Golem"] shouldBe 2
        calibration.officialGamesAuthorized shouldBe 0
        calibration.officialSeedsGenerated shouldBe 0
        calibration.officialGamesInitialized shouldBe 0
        calibration.officialActionsSubmitted shouldBe 0
        calibration.outcomeExposure shouldBe 0
    }

    test("Expedition Map completes two-piece Tron") {
        PestControlTierOneMonsterTronPolicy.expeditionMapTarget(
            listOf("Urza's Mine", "Urza's Tower")
        ) shouldBe "Urza's Power Plant"
    }

    test("Crop Rotation preserves unique Tron pieces and converts a spare land into the missing piece") {
        val battlefield = listOf("Urza's Mine", "Urza's Tower", "Forest")
        PestControlTierOneMonsterTronPolicy.cropRotationSacrifice(battlefield) shouldBe "Forest"
        PestControlTierOneMonsterTronPolicy.cropRotationTarget(
            battlefieldLands = battlefield,
            handLands = emptyList(),
        ) shouldBe "Urza's Power Plant"
    }

    test("Ancient Stirrings takes the missing Tron piece over generic value") {
        PestControlTierOneMonsterTronPolicy.ancientStirringsPick(
            revealed = listOf("Bonder's Ornament", "Urza's Power Plant", "Giant's Boulder"),
            knownLands = listOf("Urza's Mine", "Urza's Tower"),
        ) shouldBe "Urza's Power Plant"
    }

    test("Bojuka Bog targets the opposing graveyard with the most cards") {
        PestControlTierOneMonsterTronPolicy.bojukaBogTarget(
            controllerSeat = 0,
            graveyardSizes = mapOf(0 to 9, 1 to 5),
        ) shouldBe 1
    }

    test("free Cascade never shrinks Boulderbranch to Prototype and declines an uncastable hit") {
        PestControlTierOneMonsterTronPolicy.cascadeChoice(
            "Boulderbranch Golem", canCast = true
        ) shouldBe MonsterTronCascadeChoice.CAST_NORMAL_FOR_FREE

        PestControlTierOneMonsterTronPolicy.cascadeChoice(
            "Bramble Wurm", canCast = true
        ) shouldBe MonsterTronCascadeChoice.CAST_FOR_FREE

        PestControlTierOneMonsterTronPolicy.cascadeChoice(
            "Bramble Wurm", canCast = false
        ) shouldBe MonsterTronCascadeChoice.DECLINE
    }

    test("policy acceptance does not open execution on its own") {
        val blockers = PestControlTierOneMonsterTronReadiness.executionActivationErrors(
            TierOneMonsterTronReadiness(),
            com.wingedsheep.engine.registry.CardRegistry().apply {
                com.wingedsheep.mtg.sets.MtgSetCatalog.all.forEach { set ->
                    register(set.cards)
                    register(set.basicLands)
                }
            }
        )
        blockers shouldContain "opponent policy calibration is not accepted"
        blockers shouldContain "no execution runner is defined"
        blockers shouldContain "no official seed vector is frozen"
        blockers shouldContain "official Monster Tron games are not authorized"
    }

    afterSpec {
        val report = buildString {
            appendLine("schema=pest-monster-tron-policy-calibration-v1")
            appendLine("protocol_id=${calibration.protocolId}")
            appendLine("pest_main_sha256=${calibration.pestMainSha256}")
            appendLine("opponent_main_sha256=${calibration.opponentMainSha256}")
            appendLine("evidence_class=${calibration.evidenceClass}")
            appendLine("critical_cases=${calibration.criticalCases}")
            appendLine("official_games_authorized=0")
            appendLine("official_seeds_generated=0")
            appendLine("official_games_initialized=0")
            appendLine("official_actions=0")
            appendLine("outcome_exposure=0")
            appendLine("status=SEEDLESS_POLICY_CALIBRATION_COMPLETE_EXECUTION_STILL_LOCKED")
        }
        println(report)
        System.getenv("PEST_MONSTER_TRON_POLICY_REPORT")?.let { raw ->
            val path = Path.of(raw)
            Files.createDirectories(path.parent)
            Files.writeString(path, report)
        }
    }
})
