package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class DawnsReflectionScenarioTest : ScenarioTestBase() {
    init {
        test("one enchanted Forest produces its green plus two freely chosen colors") {
            val game = scenario()
                .withPlayers("Player", "Opponent")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardAttachedTo(1, "Dawn's Reflection", "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val solver = ManaSolver(cardRegistry)

            solver.canPay(game.state, game.player1Id, ManaCost.parse("{G}{U}{R}")) shouldBe true
            solver.canPay(game.state, game.player1Id, ManaCost.parse("{G}{U}{R}{B}")) shouldBe false
        }
    }
}
