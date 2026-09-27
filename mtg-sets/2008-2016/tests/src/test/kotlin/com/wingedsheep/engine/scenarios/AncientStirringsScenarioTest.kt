package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class AncientStirringsScenarioTest : ScenarioTestBase() {
    init {
        test("colorless artifacts and lands qualify while a green land and black artifact do not") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Ancient Stirrings")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInLibrary(1, "Chromatic Star")
                .withCardInLibrary(1, "Urza's Tower")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Dryad Arbor")
                .withCardInLibrary(1, "Pactdoll Terror")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            game.castSpell(1, "Ancient Stirrings").error shouldBe null
            game.resolveStack()
            val choice = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            choice.minSelections shouldBe 0
            choice.maxSelections shouldBe 1
            choice.options.map { game.state.getEntity(it)!!.get<CardComponent>()!!.name }.toSet() shouldBe
                setOf("Chromatic Star", "Urza's Tower", "Forest")
            choice.nonSelectableOptions.map { game.state.getEntity(it)!!.get<CardComponent>()!!.name }.toSet() shouldBe
                setOf("Dryad Arbor", "Pactdoll Terror")
            val tower = choice.options.single { game.state.getEntity(it)!!.get<CardComponent>()!!.name == "Urza's Tower" }
            game.selectCards(listOf(tower)).error shouldBe null
            val order = game.getPendingDecision().shouldBeInstanceOf<ReorderLibraryDecision>()
            val wantedOrder = order.cards.reversed()
            game.submitDecision(OrderedResponse(order.id, wantedOrder)).error shouldBe null
            game.resolveStack()
            game.isInHand(1, "Urza's Tower") shouldBe true
            game.state.getLibrary(game.player1Id) shouldBe wantedOrder
            game.handSize(1) shouldBe 1
        }

        test("the optional selection may be declined and every seen card is ordered on the bottom") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Ancient Stirrings")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInLibrary(1, "Myr Retriever")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Swamp")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            game.castSpell(1, "Ancient Stirrings").error shouldBe null
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>().minSelections shouldBe 0
            game.skipSelection().error shouldBe null
            val order = game.getPendingDecision().shouldBeInstanceOf<ReorderLibraryDecision>()
            val wantedOrder = order.cards.reversed()
            game.submitDecision(OrderedResponse(order.id, wantedOrder)).error shouldBe null
            game.resolveStack()
            game.handSize(1) shouldBe 0
            game.state.getLibrary(game.player1Id) shouldBe wantedOrder
            game.isInGraveyard(1, "Ancient Stirrings") shouldBe true
        }

        test("an empty library produces no card and no unresolved choice") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Ancient Stirrings")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            game.castSpell(1, "Ancient Stirrings").error shouldBe null
            game.resolveStack()
            game.hasPendingDecision() shouldBe false
            game.handSize(1) shouldBe 0
            game.librarySize(1) shouldBe 0
        }
    }
}
