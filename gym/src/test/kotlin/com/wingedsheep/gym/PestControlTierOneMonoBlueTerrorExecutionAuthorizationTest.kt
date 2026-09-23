package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.PEST_MONO_BLUE_TERROR_EXECUTION_AUTHORIZATION_SHA256
import com.wingedsheep.gym.matchup.PEST_MONO_BLUE_TERROR_EXECUTION_AUTHORIZATION_STATUS
import com.wingedsheep.gym.matchup.PestControlTierOneMonoBlueTerrorExecutionAuthorization
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier

class PestControlTierOneMonoBlueTerrorExecutionAuthorizationTest : FunSpec({
    test("authorization records exactly four frozen games while keeping execution surfaces disabled") {
        val result = PestControlTierOneMonoBlueTerrorExecutionAuthorization.inspect()

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.executionAuthorized shouldBe true
        result.failClosed shouldBe true
        result.status shouldBe PEST_MONO_BLUE_TERROR_EXECUTION_AUTHORIZATION_STATUS
        result.authorizationSha256 shouldBe PEST_MONO_BLUE_TERROR_EXECUTION_AUTHORIZATION_SHA256
        result.authorizedGames shouldBe 4
        result.attemptLimit shouldBe 1
        result.rerollsPermitted shouldBe false
        result.replacementsPermitted shouldBe false
        result.regenerationPermitted shouldBe false
        result.runnerEnabled shouldBe false
        result.initializerEnabled shouldBe false
        result.officialSeedsConsumed shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.actionsSubmitted shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("authorization facade exposes zero-argument inspection only") {
        val methods = PestControlTierOneMonoBlueTerrorExecutionAuthorization::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }

        methods.map { it.name }.toSet() shouldBe setOf("inspect")
        methods.single().parameterCount shouldBe 0
    }
})
