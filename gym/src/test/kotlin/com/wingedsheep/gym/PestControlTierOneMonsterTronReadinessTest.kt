package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.PEST_CONTROL_V10_HASH
import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_MAIN_SHA256
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronAdmission
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronReadiness
import com.wingedsheep.gym.matchup.TierOneMonsterTronReadiness
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class PestControlTierOneMonsterTronReadinessTest : FunSpec({
    val registry = CardRegistry().apply {
        register(PredefinedTokens.allTokens)
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("frozen mehanske preboard 60 is fully supported after Prototype and Cascade gates") {
        val readiness = TierOneMonsterTronReadiness()
        readiness.pestMainSha256 shouldBe PEST_CONTROL_V10_HASH
        readiness.opponentMainSha256 shouldBe PEST_MONSTER_TRON_MAIN_SHA256

        PestControlTierOneMonsterTronAdmission.mainCounts.values.sum() shouldBe 60
        PestControlTierOneMonsterTronAdmission.mainCounts["Maelstrom Colossus"] shouldBe 4
        PestControlTierOneMonsterTronAdmission.mainCounts["Boulderbranch Golem"] shouldBe 2
        PestControlTierOneMonsterTronAdmission.mainCounts["Crop Rotation"] shouldBe 3
        PestControlTierOneMonsterTronAdmission.mainCounts["Expedition Map"] shouldBe 4
        PestControlTierOneMonsterTronAdmission.unresolvedMain(registry) shouldBe emptyMap()
        PestControlTierOneMonsterTronReadiness.mainDeck().cards.size shouldBe 60
        PestControlTierOneMonsterTronReadiness.validationErrors(readiness, registry).shouldBeEmpty()
    }

    test("readiness remains fail closed behind policy calibration and official execution provenance") {
        PestControlTierOneMonsterTronReadiness.executionActivationErrors(
            TierOneMonsterTronReadiness(),
            registry,
        ).shouldContainExactly(
            "opponent policy calibration is not accepted",
            "no execution runner is defined",
            "no official seed vector is frozen",
            "official Monster Tron games are not authorized",
        )
    }

    test("readiness exposes no official gameplay state") {
        val readiness = TierOneMonsterTronReadiness()
        readiness.officialGamesAuthorized shouldBe 0
        readiness.officialSeedsGenerated shouldBe 0
        readiness.officialGamesInitialized shouldBe 0
        readiness.officialActionsSubmitted shouldBe 0
        readiness.outcomeExposure shouldBe 0
    }
})
