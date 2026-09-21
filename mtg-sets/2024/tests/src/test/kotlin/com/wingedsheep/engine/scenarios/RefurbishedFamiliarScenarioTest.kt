package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/** Covers affinity's colored floor and both Refurbished Familiar ETB outcomes. */
class RefurbishedFamiliarScenarioTest : ScenarioTestBase() {
    private val costCalculator by lazy { CostCalculator(cardRegistry) }

    init {
        test("three artifacts reduce the cost to black and an opponent with a card discards") {
            val game = scenario()
                .withPlayers("Controller", "Opponent")
                .withCardInHand(1, "Refurbished Familiar")
                .withLandsOnBattlefield(1, "Swamp", 1)
                .withCardOnBattlefield(1, "Ornithopter")
                .withCardOnBattlefield(1, "Bonesplitter")
                .withCardOnBattlefield(1, "Millstone")
                .withCardInHand(2, "Lightning Bolt")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val effective = costCalculator.calculateEffectiveCost(
                game.state,
                cardRegistry.requireCard("Refurbished Familiar"),
                game.player1Id,
            )
            effective.genericAmount shouldBe 0
            withClue("affinity never removes the colored pip") {
                effective.colorCount[Color.BLACK] shouldBe 1
            }

            game.castSpell(1, "Refurbished Familiar").error shouldBe null
            // First resolution puts the creature onto the battlefield; the ETB trigger is then
            // placed on the stack. Resolve that trigger before inspecting its discard decision.
            game.resolveStack()
            game.resolveStack()
            val discard = game.getPendingDecision() as SelectCardsDecision
            game.submitDecision(
                CardsSelectedResponse(discard.id, discard.options.take(1))
            )
            game.resolveStack()

            game.handSize(2) shouldBe 0
            withClue("the controller draws nothing when the opponent could discard") {
                game.handSize(1) shouldBe 0
            }
        }

        test("an empty-handed opponent causes one draw") {
            val game = scenario()
                .withPlayers("Controller", "Opponent")
                .withCardInHand(1, "Refurbished Familiar")
                .withLandsOnBattlefield(1, "Swamp", 4)
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.castSpell(1, "Refurbished Familiar").error shouldBe null
            game.resolveStack()
            game.resolveStack()

            game.handSize(1) shouldBe 1
        }
    }
}
