package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Real ActionProcessor test: shockland optional life payment determines tapped entry. */
class StompingGroundScenarioTest : ScenarioTestBase() {
    init {
        context("Stomping Ground replacement choice") {
            test("declining life payment enters tapped with no life loss") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Stomping Ground")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                val life = game.getLifeTotal(1)
                val card = game.findCardsInHand(1, "Stomping Ground").single()
                game.execute(PlayLand(game.player1Id, card)).error shouldBe null
                game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
                game.answerYesNo(false).error shouldBe null
                game.state.getEntity(card)?.has<TappedComponent>() shouldBe true
                game.getLifeTotal(1) shouldBe life
            }

            test("paying two life enters untapped and deducts two life") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardInHand(1, "Stomping Ground")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                val life = game.getLifeTotal(1)
                val card = game.findCardsInHand(1, "Stomping Ground").single()
                game.execute(PlayLand(game.player1Id, card)).error shouldBe null
                game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
                game.answerYesNo(true).error shouldBe null
                game.state.getEntity(card)?.has<TappedComponent>() shouldBe false
                game.getLifeTotal(1) shouldBe life - 2
            }
        }
    }
}
