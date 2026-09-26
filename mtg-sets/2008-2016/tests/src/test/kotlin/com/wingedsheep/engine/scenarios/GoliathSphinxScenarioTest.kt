package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class GoliathSphinxScenarioTest : ScenarioTestBase() {
    init {
        test("hardcast Goliath enters with summoning sickness") {
            val game = scenario().withPlayers("Sphinx", "Opponent")
                .withCardInHand(1, "Goliath Sphinx")
                .withLandsOnBattlefield(1, "Island", 7)
                .withCardInLibrary(1, "Island").withCardInLibrary(2, "Island")
                .build()
            game.castSpell(1, "Goliath Sphinx").error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            val sphinx = game.findPermanent("Goliath Sphinx")!!
            game.state.getEntity(sphinx)!!.has<SummoningSicknessComponent>() shouldBe true
        }

        test("unblocked Goliath deals eight combat damage") {
            val game = scenario().withPlayers("Sphinx", "Opponent")
                .withCardOnBattlefield(1, "Goliath Sphinx", summoningSickness = false)
                .withCardInLibrary(1, "Island").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .build()
            game.declareAttackers(mapOf("Goliath Sphinx" to 2)).error shouldBe null
            game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
            game.getLifeTotal(2) shouldBe 12
        }

        test("a ground creature cannot block Goliath's flying attack") {
            val game = scenario().withPlayers("Sphinx", "Opponent")
                .withCardOnBattlefield(1, "Goliath Sphinx", summoningSickness = false)
                .withCardOnBattlefield(2, "Grizzly Bears", summoningSickness = false)
                .withCardInLibrary(1, "Island").withCardInLibrary(2, "Island")
                .withActivePlayer(1).inPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                .build()
            game.declareAttackers(mapOf("Goliath Sphinx" to 2)).error shouldBe null
            game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
            game.declareBlockers(mapOf("Grizzly Bears" to listOf("Goliath Sphinx"))).isSuccess shouldBe false
            game.declareNoBlockers().error shouldBe null
            game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)
            game.getLifeTotal(2) shouldBe 12
        }
    }
}
