package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

class PestControlTierOneMonoBlueTerrorDisabledFourCellPlanTest : FunSpec({
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("four-cell plan is exact balanced ordered and remains vectorless") {
        val result = PestControlTierOneMonoBlueTerrorDisabledFourCellPlan.inspect(registry)

        result.errors shouldBe emptyList()
        result.green shouldBe true
        result.planSha256 shouldBe PEST_MONO_BLUE_TERROR_DISABLED_FOUR_CELL_PLAN_SHA256
        result.cells shouldBe listOf(
            MonoBlueTerrorDisabledPlanCell(
                1,
                PestSeat.SEAT_ZERO,
                PestSeat.SEAT_ONE,
                MonoBlueTerrorStartingDeck.PEST_CONTROL,
            ),
            MonoBlueTerrorDisabledPlanCell(
                2,
                PestSeat.SEAT_ZERO,
                PestSeat.SEAT_ONE,
                MonoBlueTerrorStartingDeck.MONO_BLUE_TERROR,
            ),
            MonoBlueTerrorDisabledPlanCell(
                3,
                PestSeat.SEAT_ONE,
                PestSeat.SEAT_ZERO,
                MonoBlueTerrorStartingDeck.PEST_CONTROL,
            ),
            MonoBlueTerrorDisabledPlanCell(
                4,
                PestSeat.SEAT_ONE,
                PestSeat.SEAT_ZERO,
                MonoBlueTerrorStartingDeck.MONO_BLUE_TERROR,
            ),
        )

        result.vectorPresent shouldBe false
        result.assignments shouldBe 0
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.outcomeExposure shouldBe 0
    }

    test("missing card registry support fails closed without changing counters") {
        val result = PestControlTierOneMonoBlueTerrorDisabledFourCellPlan.inspect(CardRegistry())

        result.green shouldBe false
        result.errors.shouldContain("disabled single-game wiring is not green")
        result.vectorPresent shouldBe false
        result.assignments shouldBe 0
        result.officialSeedsGenerated shouldBe 0
        result.officialGamesInitialized shouldBe 0
        result.submittedActions shouldBe 0
        result.outcomeExposure shouldBe 0
    }
})
