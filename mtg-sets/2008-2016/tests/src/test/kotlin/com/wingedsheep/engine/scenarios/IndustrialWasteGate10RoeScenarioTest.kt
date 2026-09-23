package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class IndustrialWasteGate10RoeScenarioTest : ScenarioTestBase() {
    init {
        test("Lead the Stampede may take any number of creatures from the top five") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Lead the Stampede")
                .withLandsOnBattlefield(1, "Forest", 3)
                .withCardInLibrary(1, "Elvish Mystic")
                .withCardInLibrary(1, "Fyndhorn Elves")
                .withCardInLibrary(1, "Llanowar Elves")
                .withCardInLibrary(1, "Quirion Ranger")
                .withCardInLibrary(1, "Priest of Titania")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cast = game.castSpell(1, "Lead the Stampede")
            withClue("Lead the Stampede should cast legally: ${cast.error}") {
                cast.error shouldBe null
            }
            game.resolveStack()

            val decision = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            decision.minSelections shouldBe 0
            decision.maxSelections shouldBe 5
            decision.options.size shouldBe 5

            game.selectCards(decision.options).error shouldBe null
            game.resolveStack()

            game.handSize(1) shouldBe 5
        }
    }
}
