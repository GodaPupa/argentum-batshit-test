package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_EXECUTION_AUTHORIZATION_SHA256
import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_FROZEN_SMOKE_ARTIFACT_ID
import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_FROZEN_SMOKE_ARCHIVE_SHA256
import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_FROZEN_SMOKE_RUN_ID
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronExecutionAuthorization
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PestControlTierOneMonsterTronExecutionAuthorizationTest : FunSpec({
    test("exact frozen four-game Monster Tron smoke is authorized but operational surfaces stay disabled") {
        val inspection = PestControlTierOneMonsterTronExecutionAuthorization.inspect()

        inspection.errors shouldBe emptyList()
        inspection.green shouldBe true
        inspection.executionAuthorized shouldBe true
        inspection.failClosed shouldBe true
        inspection.authorizedGames shouldBe 4
        inspection.attemptLimit shouldBe 1
        inspection.rerollsPermitted shouldBe false
        inspection.replacementsPermitted shouldBe false
        inspection.regenerationPermitted shouldBe false
        inspection.runnerEnabled shouldBe false
        inspection.initializerEnabled shouldBe false
        inspection.officialSeedsGenerated shouldBe 4
        inspection.officialGamesInitialized shouldBe 0
        inspection.actionsSubmitted shouldBe 0
        inspection.outcomeExposure shouldBe 0

        PEST_MONSTER_TRON_FROZEN_SMOKE_RUN_ID shouldBe 36_066_393_701L
        PEST_MONSTER_TRON_FROZEN_SMOKE_ARTIFACT_ID shouldBe 10_836_436_268L
        PEST_MONSTER_TRON_FROZEN_SMOKE_ARCHIVE_SHA256 shouldBe
            "70b9a665fbb154342e2789c1b6b2c2fd912579431a6ae1f9ab289984d5e7801c"
        inspection.authorizationSha256 shouldBe PEST_MONSTER_TRON_EXECUTION_AUTHORIZATION_SHA256
    }
})
