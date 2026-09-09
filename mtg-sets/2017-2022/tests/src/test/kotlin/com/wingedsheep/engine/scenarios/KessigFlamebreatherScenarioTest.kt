package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.battlefield.PreparedSpellCopyComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario test for Kessig Flamebreather (VOW #164) — {1}{R} Creature — Human Shaman, 1/3.
 *
 *   Whenever you cast a noncreature spell, this creature deals 1 damage to each opponent.
 *
 * Exercises the noncreature-cast trigger: casting an instant (Lightning Bolt) triggers Kessig
 * Flamebreather's 1 damage to each opponent, on top of the Bolt's own 3 damage. Also verifies the
 * trigger does NOT fire off a creature spell.
 */
class KessigFlamebreatherScenarioTest : ScenarioTestBase() {

    init {
        context("Kessig Flamebreather — noncreature-cast damage trigger") {

            test("casting a noncreature spell deals 1 damage to each opponent") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Kessig Flamebreather", summoningSickness = false)
                    .withCardInHand(1, "Lightning Bolt")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpellTargetingPlayer(1, "Lightning Bolt", 2).error shouldBe null
                game.resolveStack()

                withClue("Kessig Flamebreather's 1 + Lightning Bolt's 3 = 4 damage (20 -> 16)") {
                    game.getLifeTotal(2) shouldBe 16
                }
            }

            test("casting a creature spell does not trigger the damage") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Kessig Flamebreather", summoningSickness = false)
                    .withCardInHand(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Grizzly Bears").error shouldBe null
                game.resolveStack()

                withClue("a creature spell does not trigger Kessig Flamebreather") {
                    game.getLifeTotal(2) shouldBe 20
                }
            }

            test("casting Goblin Glasswright's Craft with Pride copy triggers Flamebreather") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Kessig Flamebreather", summoningSickness = false)
                    .withCardInHand(1, "Goblin Glasswright")
                    .withLandsOnBattlefield(1, "Mountain", 3)
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Goblin Glasswright").error shouldBe null
                game.resolveStack()
                withClue("casting the creature itself should not trigger Flamebreather") {
                    game.getLifeTotal(2) shouldBe 20
                }

                val copy = game.state.getExile(game.player1Id).single { id ->
                    game.state.getEntity(id)?.get<CardComponent>()?.name == "Goblin Glasswright" &&
                        game.state.getEntity(id)?.get<PreparedSpellCopyComponent>() != null
                }
                val craft = game.getLegalActions(1).single { legal ->
                    val action = legal.action
                    action is CastSpell && action.cardId == copy
                }.action as CastSpell
                game.execute(craft).error shouldBe null

                // Resolve only the Flamebreather trigger. Craft itself is still waiting below it.
                game.passPriority().error shouldBe null
                game.passPriority().error shouldBe null
                withClue("the noncreature-copy cast deals exactly one before Craft resolves") {
                    game.getLifeTotal(2) shouldBe 19
                    game.findPermanent("Treasure") shouldBe null
                }

                game.resolveStack()
                game.findPermanents("Treasure").size shouldBe 1
                game.getLifeTotal(2) shouldBe 19
            }
        }
    }
}
