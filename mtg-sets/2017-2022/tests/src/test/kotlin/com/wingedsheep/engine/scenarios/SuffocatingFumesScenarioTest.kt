package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class SuffocatingFumesScenarioTest : ScenarioTestBase() {
    init {
        test("only opposing creatures get minus one minus one") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withCardInHand(1, "Suffocating Fumes")
                .withCardOnBattlefield(1, "Grizzly Bears")
                .withCardOnBattlefield(2, "Grizzly Bears")
                .withLandsOnBattlefield(1, "Swamp", 3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val mine = game.findPermanent(1, "Grizzly Bears")!!
            val theirs = game.findPermanent(2, "Grizzly Bears")!!
            game.castSpell(1, "Suffocating Fumes").error shouldBe null
            game.resolveStack()

            game.state.projectedState.getToughness(mine) shouldBe 2
            game.state.projectedState.getToughness(theirs) shouldBe 1
        }
    }
}
