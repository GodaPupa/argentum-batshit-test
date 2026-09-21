package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class PestControlTierOneGrixisDisabledSingleGameTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
    }

    test("game one wiring is deterministic and remains seedless actionless and disabled") {
        val result = PestControlTierOneGrixisDisabledSingleGame.inspect(registry)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.compositionSha256 shouldBe PEST_GRIXIS_DISABLED_SINGLE_GAME_SHA256
        result.gameNumber shouldBe 1
        result.pestSeat shouldBe PestSeat.SEAT_ZERO
        result.grixisSeat shouldBe PestSeat.SEAT_ONE
        result.startingDeck shouldBe GrixisStartingDeck.PEST_CONTROL
        result.submittedActions shouldBe 0
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("missing card registry support fails closed") {
        val result = PestControlTierOneGrixisDisabledSingleGame.inspect(CardRegistry())

        result.green shouldBe false
        result.errors.shouldContain("official initialization boundary is not fail closed")
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.outcomeExposure shouldBe 0
    }
})
