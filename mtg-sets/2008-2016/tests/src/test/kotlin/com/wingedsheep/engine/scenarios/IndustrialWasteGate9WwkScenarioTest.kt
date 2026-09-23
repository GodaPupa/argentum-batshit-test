package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

class IndustrialWasteGate9WwkScenarioTest : ScenarioTestBase() {
    init {
        test("Bojuka Bog enters tapped and exiles only the targeted player's graveyard") {
            val game = scenario()
                .withPlayers("Monster Tron", "Opponent")
                .withCardInHand(1, "Bojuka Bog")
                .withCardInGraveyard(1, "Forest")
                .withCardInGraveyard(2, "Forest")
                .withCardInGraveyard(2, "Mountain")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val bog = game.state.getHand(game.player1Id).single { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Bojuka Bog"
            }

            val play = game.execute(PlayLand(game.player1Id, bog))
            withClue("Bojuka Bog should be a legal land play: ${play.error}") {
                play.error shouldBe null
            }

            withClue("Bojuka Bog's ETB must ask for its target player") {
                game.hasPendingDecision() shouldBe true
            }
            val target = game.selectTargets(listOf(game.player2Id))
            withClue("targeting the opponent should be legal: ${target.error}") {
                target.error shouldBe null
            }
            game.resolveStack()

            game.graveyardSize(2) shouldBe 0
            game.isInGraveyard(1, "Forest") shouldBe true
            game.state.getEntity(bog)?.has<TappedComponent>() shouldBe true
        }
    }
}
