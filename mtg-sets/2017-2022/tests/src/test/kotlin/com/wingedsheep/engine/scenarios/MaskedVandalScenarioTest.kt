package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class MaskedVandalScenarioTest : ScenarioTestBase() {
    init {
        test("its ETB may exile a creature card to exile an opponent artifact") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Masked Vandal")
                .withCardInGraveyard(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Test Artifact")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Masked Vandal")
            game.resolveStack()
            (game.getPendingDecision() is ChooseTargetsDecision) shouldBe true
            val artifact = game.findPermanent("Test Artifact")!!
            game.selectTargets(listOf(artifact))
            game.resolveStack()
            game.answerYesNo(true)
            game.selectCards(game.findCardsInGraveyard(1, "Grizzly Bears"))
            game.resolveStack()

            game.isInExile(2, "Test Artifact") shouldBe true
            game.isInExile(1, "Grizzly Bears") shouldBe true
        }
    }
}
