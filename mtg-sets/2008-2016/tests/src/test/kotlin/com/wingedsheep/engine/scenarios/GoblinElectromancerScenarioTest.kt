package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class GoblinElectromancerScenarioTest : ScenarioTestBase() {
    private fun costOf(game: TestGame, spellName: String, playerNumber: Int = 1) =
        CostCalculator(cardRegistry).calculateEffectiveCost(
            game.state,
            cardRegistry.requireCard(spellName),
            if (playerNumber == 1) game.player1Id else game.player2Id,
        )

    init {
        test("instant or sorcery generic cost is reduced by exactly one") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Lava Axe")
                .withCardOnBattlefield(1, "Goblin Electromancer")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cost = costOf(game, "Lava Axe")
            cost.genericAmount shouldBe 3
            cost.colorCount[Color.RED] shouldBe 1
        }

        test("colored pips survive generic reduction") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Cancel")
                .withCardOnBattlefield(1, "Goblin Electromancer")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cost = costOf(game, "Cancel")
            cost.genericAmount shouldBe 0
            cost.colorCount[Color.BLUE] shouldBe 2
        }

        test("creature spells are not reduced") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Serra Angel")
                .withCardOnBattlefield(1, "Goblin Electromancer")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            costOf(game, "Serra Angel").genericAmount shouldBe 3
        }

        test("an opponent's instant or sorcery is not reduced") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(2, "Lava Axe")
                .withCardOnBattlefield(1, "Goblin Electromancer")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            costOf(game, "Lava Axe", playerNumber = 2).genericAmount shouldBe 4
        }

        test("without Goblin Electromancer the spell keeps full cost") {
            val game = scenario()
                .withPlayers("Player1", "Player2")
                .withCardInHand(1, "Lava Axe")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            costOf(game, "Lava Axe").genericAmount shouldBe 4
        }
    }
}
