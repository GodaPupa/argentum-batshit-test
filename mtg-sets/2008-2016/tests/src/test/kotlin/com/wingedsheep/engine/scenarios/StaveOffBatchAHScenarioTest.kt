package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ChooseColorDecision
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class StaveOffBatchAHScenarioTest : ScenarioTestBase() {
    init {
        context("Stave Off Batch AH") {
            test("chosen-color protection is selected on resolution and expires at end of turn") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Plains", 1)
                    .withCardInHand(1, "Stave Off")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                game.castSpell(1, "Stave Off", bears).error shouldBe null
                game.resolveStack()

                val decision = game.getPendingDecision()
                decision.shouldBeInstanceOf<ChooseColorDecision>()
                game.submitDecision(ColorChosenResponse(decision.id, Color.RED))

                game.state.projectedState.hasKeyword(bears, "PROTECTION_FROM_RED") shouldBe true
                game.state.projectedState.hasKeyword(bears, "PROTECTION_FROM_BLUE") shouldBe false

                game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
                game.state.projectedState.hasKeyword(bears, "PROTECTION_FROM_RED") shouldBe false
            }
        }
    }
}
