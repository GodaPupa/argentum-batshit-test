package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class PestControlTierOneMonoBlueTerrorDisabledSingleGameTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("game one wiring is deterministic and remains seedless actionless and disabled") {
        val result = PestControlTierOneMonoBlueTerrorDisabledSingleGame.inspect(registry)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.compositionSha256 shouldBe PEST_MONO_BLUE_TERROR_DISABLED_SINGLE_GAME_SHA256
        result.gameNumber shouldBe 1
        result.pestSeat shouldBe PestSeat.SEAT_ZERO
        result.terrorSeat shouldBe PestSeat.SEAT_ONE
        result.startingDeck shouldBe MonoBlueTerrorStartingDeck.PEST_CONTROL
        result.submittedActions shouldBe 0
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("missing card registry support fails closed") {
        val result = PestControlTierOneMonoBlueTerrorDisabledSingleGame.inspect(CardRegistry())

        result.green shouldBe false
        result.errors.shouldContain("official initialization boundary is not fail closed")
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }
})
