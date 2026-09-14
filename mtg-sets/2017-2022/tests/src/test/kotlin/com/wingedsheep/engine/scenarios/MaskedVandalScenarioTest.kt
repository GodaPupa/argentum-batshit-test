package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class MaskedVandalScenarioTest : ScenarioTestBase() {
    init {
        test("its ETB trades a creature card in your graveyard for an opponent's artifact") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Masked Vandal")
                .withCardInGraveyard(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Ornithopter")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val artifact = game.findPermanent("Ornithopter")!!

            game.castSpell(1, "Masked Vandal").error shouldBe null
            game.resolveStack()
            if (game.getPendingDecision() is ChooseTargetsDecision) {
                game.selectTargets(listOf(artifact))
            }
            game.resolveStack()
            (game.getPendingDecision() is YesNoDecision) shouldBe true
            game.answerYesNo(true)
            game.resolveStack()
            (game.getPendingDecision() is SelectCardsDecision) shouldBe true
            val creature = game.state.getGraveyard(game.player1Id).single()
            game.selectCards(listOf(creature)).error shouldBe null
            game.resolveStack()

            game.isInExile(1, "Grizzly Bears") shouldBe true
            game.isInExile(2, "Ornithopter") shouldBe true
        }
    }
}
