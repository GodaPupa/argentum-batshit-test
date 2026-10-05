package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PEST_CONTROL_V10_HASH
import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_MAIN_SHA256
import com.wingedsheep.gym.matchup.PestControlPreboardDecks
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronAdmission
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronPolicyReadiness
import com.wingedsheep.gym.matchup.TierOneMonsterTronAdmission
import com.wingedsheep.gym.matchup.TierOneMonsterTronPolicyReadiness
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Source-only current-pair inventory regression.
 *
 * No GameInitializer, GameEnvironment, official seed vector, allocation, claim, action submission,
 * game execution, or outcome is created here. This test only freezes deck identities and the
 * fail-closed Monster Tron admission/policy state used by the C2-A2 source package.
 */
class PestCurrentPairC2A2SourceInventoryTest : FunSpec({
    test("current pair identities stay exact and Monster Tron remains disabled") {
        PestControlPreboardDecks.verifyFrozenIdentities()
        PEST_CONTROL_V10_HASH shouldBe
            "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"
        PEST_MONSTER_TRON_MAIN_SHA256 shouldBe
            "79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f"

        val admission = TierOneMonsterTronAdmission()
        PestControlTierOneMonsterTronAdmission.validationErrors(admission) shouldBe emptyList()
        admission.officialGamesAuthorized shouldBe 0
        admission.officialSeedsGenerated shouldBe 0
        admission.outcomeExposure shouldBe 0

        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }
        val readiness = TierOneMonsterTronPolicyReadiness()
        PestControlTierOneMonsterTronPolicyReadiness.validationErrors(readiness, registry) shouldBe emptyList()
        PestControlTierOneMonsterTronPolicyReadiness.executionActivationErrors(readiness, registry) shouldBe listOf(
            "no execution runner is defined",
            "no official seed vector is frozen",
            "official Monster Tron games are not authorized",
        )
        readiness.officialGamesAuthorized shouldBe 0
        readiness.officialSeedsGenerated shouldBe 0
        readiness.officialGamesInitialized shouldBe 0
        readiness.officialActionsSubmitted shouldBe 0
        readiness.outcomeExposure shouldBe 0
    }
})
