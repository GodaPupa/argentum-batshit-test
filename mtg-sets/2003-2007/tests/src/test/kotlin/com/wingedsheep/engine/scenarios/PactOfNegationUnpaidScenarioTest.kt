package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import com.wingedsheep.engine.core.YesNoDecision

/** Changed-fixture successor for only the previously INCOMPLETE unpaid Pact path. */
class PactOfNegationUnpaidScenarioTest : ScenarioTestBase() {
    init {
        test("payable Pact debt explicitly declined at next upkeep loses the controller") {
            val game = scenario()
                .withPlayers()
                .withActivePlayer(2)
                .withCardInHand(2, "Grizzly Bears")
                .withLandsOnBattlefield(2, "Forest", 2)
                .withCardInHand(1, "Pact of Negation")
                .withLandsOnBattlefield(1, "Island", 5)
                .build()

            game.castSpell(2, "Grizzly Bears").error shouldBe null
            val target = game.state.stack.last()
            game.passPriority().error shouldBe null
            val pact = game.findCardsInHand(1, "Pact of Negation").single()
            game.execute(CastSpell(game.player1Id, pact, listOf(ChosenTarget.Spell(target)))).error shouldBe null
            game.resolveStack()

            game.passUntilPhase(Phase.ENDING, Step.END)
            game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
            game.resolveStack()
            game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>()

            game.answerYesNo(false).error shouldBe null
            game.resolveStack()

            withClue("Declining a payable Pact debt loses the controller") {
                game.state.gameOver shouldBe true
                game.state.winnerId shouldBe game.player2Id
            }
        }
    }
}
