package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.player.PlayerInitiativeComponent
import com.wingedsheep.engine.state.components.player.UndercityProgressComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.UndercityRoom
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class IndustrialWasteGate10ClbScenarioTest : ScenarioTestBase() {
    init {
        test("Avenging Hunter takes the initiative and enters Secret Entrance") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Avenging Hunter")
                .withLandsOnBattlefield(1, "Forest", 5)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Elvish Mystic")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cast = game.castSpell(1, "Avenging Hunter")
            withClue("Avenging Hunter should cast legally: ${cast.error}") {
                cast.error shouldBe null
            }

            // Resolve the creature, its ETB, the initiative's inherent venture trigger, and the
            // Secret Entrance room trigger. The basic-land search is the first player decision.
            game.resolveStack()

            val search = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            search.minSelections shouldBe 0
            search.maxSelections shouldBe 1
            game.selectCards(search.options).error shouldBe null
            game.resolveStack()

            game.state.getEntity(game.player1Id)?.has<PlayerInitiativeComponent>() shouldBe true
            game.state.getEntity(game.player2Id)?.has<PlayerInitiativeComponent>() shouldBe false
            game.state.getEntity(game.player1Id)
                ?.get<UndercityProgressComponent>()?.room shouldBe UndercityRoom.SECRET_ENTRANCE
            game.isInHand(1, "Forest") shouldBe true
        }
    }
}
