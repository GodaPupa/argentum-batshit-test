package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Deterministic policy coverage for choosing between tapped and immediately usable land drops. */
class LandSequencingAgentDecisionTest : ScenarioTestBase() {

    init {
        test("an untapped land that enables a meaningful one-drop precedes a tapped land") {
            val game = scenario().withPlayers()
                .withRngSeed(0x1A4D_5EEDL)
                .withCardInHand(1, "Mountain")
                .withCardInHand(1, "Rootbound Crag")
                // A vanilla 1/1 keeps the probe about development, not haste or a named payoff.
                .withCardInHand(1, "Mons's Goblin Raiders")
                .withCardInHand(1, "Craw Wurm")
                .build()

            val action = AIPlayer.create(
                cardRegistry,
                game.player1Id,
                AiProfile.PRODUCTION_CANDIDATE_EXPIRING,
            ).chooseAction(game.state).shouldBeInstanceOf<PlayLand>()

            game.state.getEntity(action.cardId)?.get<CardComponent>()?.name shouldBe "Mountain"
        }
    }
}
