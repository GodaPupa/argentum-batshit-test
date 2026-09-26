package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/** Canonical ZEN Kor Skyfisher: mandatory non-targeting bounce, including itself and lands. */
class KorSkyfisherScenarioTest : ScenarioTestBase() {
    private fun setup() = scenario().withPlayers("Skyfisher", "Opponent")
        .withCardInHand(1, "Kor Skyfisher").withLandsOnBattlefield(1, "Plains", 2)
        .withCardInLibrary(1, "Island").withCardInLibrary(2, "Island")
        .withRngSeed(0xFEC002).withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
    private fun TestGame.advance() { resolveStack().forEach { it.error shouldBe null } }
    private fun TestGame.finish() {
        advance(); state.pendingDecision shouldBe null; state.stack shouldBe emptyList()
        state.gameOver shouldBe false
    }
    private fun TestGame.castSkyfisher() {
        castSpell(1, "Kor Skyfisher").error shouldBe null; advance()
        (state.pendingDecision is SelectCardsDecision) shouldBe true
    }

    init {
        test("returns a creature chosen on resolution and keeps the Skyfisher") {
            val game = setup().withCardOnBattlefield(1, "Grizzly Bears").build()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.castSkyfisher()
            game.selectCards(listOf(bears)).error shouldBe null
            game.finish()
            game.isInHand(1, "Grizzly Bears") shouldBe true
            game.isOnBattlefield("Kor Skyfisher") shouldBe true
        }

        test("a land is a legal return and the mandatory choice cannot be declined") {
            val game = setup().build()
            val land = game.findPermanent("Plains")!!
            game.castSkyfisher()
            val before = game.state
            val rejected = game.selectCards(emptyList())
            rejected.error.shouldNotBeNull()
            rejected.state shouldBe before
            game.state shouldBe before
            game.selectCards(listOf(land)).error shouldBe null
            game.finish()
            game.isInHand(1, "Plains") shouldBe true
            game.isOnBattlefield("Kor Skyfisher") shouldBe true
        }

        test("can return itself even while other permanents are available") {
            val game = setup().build()
            game.castSkyfisher()
            game.selectCards(listOf(game.findPermanent("Kor Skyfisher")!!)).error shouldBe null
            game.finish()
            game.isInHand(1, "Kor Skyfisher") shouldBe true
            game.isOnBattlefield("Kor Skyfisher") shouldBe false
        }

        test("opponent-controlled permanents are excluded from the choice") {
            val game = setup().withCardOnBattlefield(2, "Grizzly Bears").build()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.castSkyfisher()
            val choice = game.state.pendingDecision as SelectCardsDecision
            choice.options.contains(bears) shouldBe false
            val before = game.state
            val rejected = game.selectCards(listOf(bears))
            rejected.error.shouldNotBeNull()
            rejected.state shouldBe before
            game.state shouldBe before
            game.selectCards(listOf(game.findPermanent("Plains")!!)).error shouldBe null
            game.finish()
            game.isOnBattlefield("Grizzly Bears") shouldBe true
        }

        test("a controlled permanent owned by the opponent returns to its owner's hand") {
            val game = setup().withCardOnBattlefield(2, "Grizzly Bears").build()
            val bears = game.findPermanent("Grizzly Bears")!!
            game.state = game.state.updateEntity(bears) { it.with(ControllerComponent(game.player1Id)) }
            game.castSkyfisher()
            game.selectCards(listOf(bears)).error shouldBe null
            game.finish()
            game.isInHand(2, "Grizzly Bears") shouldBe true
            game.isInHand(1, "Grizzly Bears") shouldBe false
        }
    }
}
