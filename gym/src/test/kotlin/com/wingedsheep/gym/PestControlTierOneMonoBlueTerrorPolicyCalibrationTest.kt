package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PEST_MONO_BLUE_TERROR_POLICY_CALIBRATION_SHA256
import com.wingedsheep.gym.matchup.PEST_MONO_BLUE_TERROR_POLICY_CALIBRATION_STATUS
import com.wingedsheep.gym.matchup.PestControlTierOneMonoBlueTerrorPolicyCalibration
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier
import kotlin.time.Duration.Companion.hours

class PestControlTierOneMonoBlueTerrorPolicyCalibrationTest : FunSpec({
    val registry = CardRegistry().apply {
        register(PredefinedTokens.allTokens)
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("production AI completes all four synthetic Terror cells cleanly").config(timeout = 2.hours) {
        val result = PestControlTierOneMonoBlueTerrorPolicyCalibration.inspect(registry)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.failClosed shouldBe true
        result.calibrationSha256 shouldBe PEST_MONO_BLUE_TERROR_POLICY_CALIBRATION_SHA256
        result.status shouldBe PEST_MONO_BLUE_TERROR_POLICY_CALIBRATION_STATUS
        result.completedGames shouldBe 4
        result.terrorActedGames shouldBe 4
        result.actionCounts.size shouldBe 4
        result.actionCounts.all { it > 0 } shouldBe true
        result.terminalTurns.size shouldBe 4
        result.terminalTurns.all { it in 1..60 } shouldBe true
        result.rejectedActions shouldBe 0
        result.wedges shouldBe 0
        result.officialSeedValuesExposed shouldBe 0
        result.officialSeedsConsumed shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
        result.executionAuthorized shouldBe false
    }

    test("policy calibration exposes registry inspection only") {
        val methods = PestControlTierOneMonoBlueTerrorPolicyCalibration::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }

        methods.map { it.name }.toSet() shouldBe setOf("inspect")
        methods.single().parameterTypes.toList() shouldBe listOf(CardRegistry::class.java)
    }
})
