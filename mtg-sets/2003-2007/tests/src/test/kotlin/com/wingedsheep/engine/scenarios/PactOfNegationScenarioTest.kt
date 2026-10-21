package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.SelectManaSourcesDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Exact mechanics bank for the Manual Phase-2 Pact dependency. */
class PactOfNegationScenarioTest : ScenarioTestBase() {

    private fun TestGame.castPactAtCurrentSpell() {
        val target = state.stack.last()
        passPriority().error shouldBe null
        val pact = findCardsInHand(1, "Pact of Negation").single()
        execute(CastSpell(player1Id, pact, listOf(ChosenTarget.Spell(target)))).error shouldBe null
        resolveStack()
    }

    private fun TestGame.toPactControllerNextUpkeep() {
        passUntilPhase(Phase.ENDING, Step.END)
        passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
        resolveStack()
        getPendingDecision().shouldBeInstanceOf<YesNoDecision>()
    }

    init {
        test("Pact counters a real spell and schedules exactly one next-upkeep debt") {
            val game = scenario()
                .withPlayers()
                .withActivePlayer(2)
                .withCardInHand(2, "Grizzly Bears")
                .withLandsOnBattlefield(2, "Forest", 2)
                .withCardInHand(1, "Pact of Negation")
                .build()

            game.castSpell(2, "Grizzly Bears").error shouldBe null
            game.castPactAtCurrentSpell()

            withClue("The target spell was countered") {
                game.isInGraveyard(2, "Grizzly Bears") shouldBe true
            }
            withClue("The Pact itself resolved") {
                game.isInGraveyard(1, "Pact of Negation") shouldBe true
            }
            withClue("Exactly one delayed next-upkeep debt remains") {
                game.state.delayedTriggers.size shouldBe 1
            }
        }

        test("Pact next-upkeep debt can be paid with five Islands") {
            val game = scenario()
                .withPlayers()
                .withActivePlayer(2)
                .withCardInHand(2, "Grizzly Bears")
                .withLandsOnBattlefield(2, "Forest", 2)
                .withCardInHand(1, "Pact of Negation")
                .withLandsOnBattlefield(1, "Island", 5)
                .build()

            game.castSpell(2, "Grizzly Bears").error shouldBe null
            game.castPactAtCurrentSpell()
            game.toPactControllerNextUpkeep()

            game.answerYesNo(true).error shouldBe null
            val payment = game.getPendingDecision()
            if (payment != null) {
                payment.shouldBeInstanceOf<SelectManaSourcesDecision>()
                game.submitManaSourcesAutoPay().error shouldBe null
            }
            game.resolveStack()

            withClue("Paying the Pact debt does not lose the game") {
                game.state.gameOver shouldBe false
            }
            withClue("The one-shot debt has been consumed") {
                game.state.delayedTriggers.size shouldBe 0
            }
        }

        test("Pact unpaid next-upkeep debt loses the controller") {
            val game = scenario()
                .withPlayers()
                .withActivePlayer(2)
                .withCardInHand(2, "Grizzly Bears")
                .withLandsOnBattlefield(2, "Forest", 2)
                .withCardInHand(1, "Pact of Negation")
                .build()

            game.castSpell(2, "Grizzly Bears").error shouldBe null
            game.castPactAtCurrentSpell()
            // There is no legal payment: the Gate.MayPay contract must skip the
            // impossible Yes/No prompt and execute its lose-game branch.
            game.passUntilPhase(Phase.ENDING, Step.END)
            game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
            game.resolveStack()

            withClue("An unpayable Pact debt cannot offer a fictitious payment") {
                game.getPendingDecision() shouldBe null
            }
            withClue("Unpayable Pact debt automatically eliminates the controller") {
                game.state.gameOver shouldBe true
                game.state.winnerId shouldBe game.player2Id
                game.state.delayedTriggers.isEmpty() shouldBe true
            }
        }
    }
}
