package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class UtromMonitorScenarioTest : ScenarioTestBase() {
    private val costs by lazy { CostCalculator(cardRegistry) }

    init {
        test("artifact lands and ordinary artifacts reduce only generic mana") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardInHand(1, "Utrom Monitor")
                .withCardOnBattlefield(1, "Seat of the Synod")
                .withCardOnBattlefield(1, "Vault of Whispers")
                .withCardOnBattlefield(1, "Bonesplitter")
                .withCardOnBattlefield(1, "Ornithopter")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val definition = cardRegistry.requireCard("Utrom Monitor")
            val cost = costs.calculateEffectiveCost(game.state, definition, game.player1Id)
            cost.genericAmount shouldBe 0
            cost.colorCount.values.sum() shouldBe 1
            definition.keywords.contains(Keyword.FLYING) shouldBe true
        }
    }
}
