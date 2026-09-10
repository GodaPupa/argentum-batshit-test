package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

class RefurbishedFamiliarScenarioTest : ScenarioTestBase() {
    private val costs by lazy { CostCalculator(cardRegistry) }

    init {
        test("artifact lands count for affinity and the black pip remains") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardInHand(1, "Refurbished Familiar")
                .withCardOnBattlefield(1, "Vault of Whispers")
                .withCardOnBattlefield(1, "Great Furnace")
                .withCardOnBattlefield(1, "Bonesplitter")
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val cost = costs.calculateEffectiveCost(game.state, cardRegistry.requireCard("Refurbished Familiar"), game.player1Id)
            cost.genericAmount shouldBe 0
            cost.colorCount.values.sum() shouldBe 1
        }

        test("ETB makes an opponent discard when possible") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardInHand(1, "Refurbished Familiar").withLandsOnBattlefield(1, "Swamp", 4)
                .withCardInHand(2, "Forest").withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Refurbished Familiar").error shouldBe null
            game.resolveStack()
            game.handSize(2) shouldBe 0
        }

        test("ETB draws for an empty-handed opponent") {
            val game = scenario().withPlayers("P1", "P2")
                .withCardInHand(1, "Refurbished Familiar").withLandsOnBattlefield(1, "Swamp", 4)
                .withCardInLibrary(1, "Forest").withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            game.castSpell(1, "Refurbished Familiar").error shouldBe null
            game.resolveStack()
            game.handSize(1) shouldBe 1
        }
    }
}
