package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class IndustrialWasteGate10EldScenarioTest : ScenarioTestBase() {
    init {
        test("Gingerbread Cabin enters untapped with three other Forests and creates Food") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Gingerbread Cabin")
                .withLandsOnBattlefield(1, "Forest", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cabin = game.state.getHand(game.player1Id).single { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Gingerbread Cabin"
            }
            val play = game.execute(PlayLand(game.player1Id, cabin))
            withClue("Gingerbread Cabin should be a legal land play: ${play.error}") {
                play.error shouldBe null
            }

            game.state.getEntity(cabin)?.has<TappedComponent>() shouldBe false
            game.resolveStack()

            // Three starting Forests + Cabin + one Food token.
            game.state.getBattlefield(game.player1Id).size shouldBe 5
        }

        test("Gingerbread Cabin enters tapped with fewer than three other Forests and creates no Food") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Gingerbread Cabin")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cabin = game.state.getHand(game.player1Id).single { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Gingerbread Cabin"
            }
            val play = game.execute(PlayLand(game.player1Id, cabin))
            withClue("Gingerbread Cabin should be a legal land play: ${play.error}") {
                play.error shouldBe null
            }

            game.state.getEntity(cabin)?.has<TappedComponent>() shouldBe true
            game.resolveStack()

            // Two starting Forests + Cabin; the untapped-only ETB did not trigger.
            game.state.getBattlefield(game.player1Id).size shouldBe 3
        }
    }
}
