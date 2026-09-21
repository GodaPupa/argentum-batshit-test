package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/** Verifies Utrom Monitor's actual TMC text: affinity plus flying, with no Cycling. */
class UtromMonitorScenarioTest : ScenarioTestBase() {
    private val costCalculator by lazy { CostCalculator(cardRegistry) }

    init {
        test("four artifacts reduce the spell to its blue floor") {
            var builder = scenario()
                .withPlayers("Controller", "Opponent")
                .withCardInHand(1, "Utrom Monitor")
                .withLandsOnBattlefield(1, "Island", 1)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            repeat(4) { builder = builder.withCardOnBattlefield(1, "Bonesplitter") }
            val game = builder.build()

            val effective = costCalculator.calculateEffectiveCost(
                game.state,
                cardRegistry.requireCard("Utrom Monitor"),
                game.player1Id,
            )
            effective.genericAmount shouldBe 0
            withClue("affinity cannot reduce {U}") {
                effective.colorCount[Color.BLUE] shouldBe 1
            }

            game.castSpell(1, "Utrom Monitor").error shouldBe null
            game.resolveStack()
            game.isOnBattlefield("Utrom Monitor") shouldBe true
        }
    }
}
