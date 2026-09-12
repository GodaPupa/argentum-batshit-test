package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class MaskedVandalScenarioTest : ScenarioTestBase() {
    init {
        test("ETB exiles a creature card to exile an opponent artifact") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Masked Vandal")
                .withCardInGraveyard(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Sol Ring")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val artifact = game.findPermanent("Sol Ring")!!
            game.castSpell(1, "Masked Vandal").error shouldBe null
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<ChooseTargetsDecision>()
            game.selectTargets(listOf(artifact)).error shouldBe null
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
            game.answerYesNo(true).error shouldBe null
            game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            game.selectCards(game.findCardsInGraveyard(1, "Grizzly Bears")).error shouldBe null
            game.resolveStack()

            game.isInExile(2, "Sol Ring") shouldBe true
            game.isInExile(1, "Grizzly Bears") shouldBe true
        }
    }
}
