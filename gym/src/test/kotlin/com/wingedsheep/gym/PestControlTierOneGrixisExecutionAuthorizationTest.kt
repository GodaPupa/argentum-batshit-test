package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.PEST_GRIXIS_EXECUTION_AUTHORIZATION_SHA256
import com.wingedsheep.gym.matchup.PEST_GRIXIS_EXECUTION_AUTHORIZATION_STATUS
import com.wingedsheep.gym.matchup.PestControlTierOneGrixisExecutionAuthorization
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PestControlTierOneGrixisExecutionAuthorizationTest : FunSpec({
    test("authorization records the reviewed four-game decision without enabling execution") {
        val inspection = PestControlTierOneGrixisExecutionAuthorization.inspect()
        inspection.green shouldBe true
        inspection.executionAuthorized shouldBe true
        inspection.failClosed shouldBe true
        inspection.status shouldBe PEST_GRIXIS_EXECUTION_AUTHORIZATION_STATUS
        inspection.authorizationSha256 shouldBe PEST_GRIXIS_EXECUTION_AUTHORIZATION_SHA256
        inspection.authorizedGames shouldBe 4
        inspection.attemptLimit shouldBe 1
        inspection.rerollsPermitted shouldBe false
        inspection.replacementsPermitted shouldBe false
        inspection.regenerationPermitted shouldBe false
        inspection.runnerEnabled shouldBe false
        inspection.initializerEnabled shouldBe false
        inspection.officialSeedsConsumed shouldBe 0
        inspection.officialGamesInitialized shouldBe 0
        inspection.actionsSubmitted shouldBe 0
        inspection.outcomeExposure shouldBe 0
    }
})
