package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CardsRevealedEvent
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class IndustrialWasteGate10MmqScenarioTest : ScenarioTestBase() {
    init {
        test("Land Grant alternative cost reveals the entire landless hand and finds a Forest") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Land Grant")
                .withCardInHand(1, "Elvish Mystic")
                .withCardInHand(1, "Fyndhorn Elves")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Llanowar Elves")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val landGrant = game.state.getHand(game.player1Id).single { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Land Grant"
            }
            val alt = game.getLegalActions(1).single { legal ->
                val action = legal.action as? CastSpell
                action?.cardId == landGrant && action.useAlternativeCost
            }

            val cast = game.execute(alt.action)
            withClue("Land Grant's landless-hand alternative cost should be legal: ${cast.error}") {
                cast.error shouldBe null
            }
            val reveal = cast.events.filterIsInstance<CardsRevealedEvent>().single()
            reveal.cardNames.shouldContainExactlyInAnyOrder("Elvish Mystic", "Fyndhorn Elves")

            game.resolveStack()
            val search = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            search.minSelections shouldBe 0
            search.maxSelections shouldBe 1
            game.selectCards(search.options).error shouldBe null
            game.resolveStack()

            game.isInHand(1, "Forest") shouldBe true
        }

        test("Land Grant alternative cost is unavailable while any land card is in hand") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Land Grant")
                .withCardInHand(1, "Forest")
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val landGrant = game.state.getHand(game.player1Id).single { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Land Grant"
            }
            val alternatives = game.getLegalActions(1).filter { legal ->
                val action = legal.action as? CastSpell
                action?.cardId == landGrant && action.useAlternativeCost
            }
            alternatives.size shouldBe 0
        }
    }
}
