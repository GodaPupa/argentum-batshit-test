package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Real engine regression: Worldly Tutor finds only a creature and keeps it in the library. */
class WorldlyTutorScenarioTest : ScenarioTestBase() {
    init {
        test("search only creature cards and put the chosen creature on top") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Worldly Tutor")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInLibrary(1, "Grizzly Bears")
                .withCardInLibrary(1, "Sol Ring")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val creature = game.findCardsInLibrary(1, "Grizzly Bears").single()
            val noncreature = game.findCardsInLibrary(1, "Sol Ring").single()
            game.castSpell(1, "Worldly Tutor").error shouldBe null
            game.resolveStack()
            val decision = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            (creature in decision.options) shouldBe true
            (noncreature in decision.options) shouldBe false
            game.selectCards(listOf(creature)).error shouldBe null
            game.resolveStack()
            game.findCardsInLibrary(1, "Grizzly Bears").single() shouldBe creature
            game.state.getLibrary(game.player1Id).last() shouldBe creature
            game.isInGraveyard(1, "Worldly Tutor") shouldBe true
            game.hasPendingDecision() shouldBe false
        }
    }
}
