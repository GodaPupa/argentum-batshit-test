package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldNotBeNull

class IndustrialWasteGate11RnaScenarioTest : ScenarioTestBase() {
    init {
        test("Saruli Caretaker extra tap cost excludes the source already paying tap") {
            val game = scenario()
                .withPlayers("Spy", "Industrial")
                .withCardOnBattlefield(1, "Saruli Caretaker")
                .withCardOnBattlefield(1, "Gatecreeper Vine")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val saruli = game.findPermanent("Saruli Caretaker")!!
            val vine = game.findPermanent("Gatecreeper Vine")!!

            val action = game.getLegalActions(1).single { legal ->
                (legal.action as? ActivateAbility)?.sourceId == saruli
            }
            action.affordable shouldBe true

            val cost = action.additionalCostInfo.shouldNotBeNull()
            cost.costType shouldBe "TapPermanents"
            cost.validTapTargets shouldContain vine
            cost.validTapTargets shouldNotContain saruli
        }
    }
}
