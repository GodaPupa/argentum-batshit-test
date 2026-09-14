package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class HalimarDepthsScenarioTest : ScenarioTestBase() {
    init {
        test("enters tapped and lets its controller reorder the top three cards") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Halimar Depths")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Island")
                .withCardInLibrary(1, "Mountain")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val land = game.findCardsInHand(1, "Halimar Depths").single()
            game.execute(PlayLand(game.player1Id, land)).error shouldBe null
            game.state.getEntity(land)?.get<TappedComponent>() shouldBe TappedComponent

            game.resolveStack()
            val decision = game.getPendingDecision() as ReorderLibraryDecision
            decision.cards.size shouldBe 3
            game.submitDecision(OrderedResponse(decision.id, decision.cards.reversed())).error shouldBe null
            game.resolveStack()

            val top = game.state.getLibrary(game.player1Id).first()
            game.state.getEntity(top)?.get<CardComponent>()?.name shouldBe "Mountain"
        }
    }
}
