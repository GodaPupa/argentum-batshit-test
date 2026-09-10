package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for four Tempest (TMP) red cards, all built from existing SDK primitives:
 *
 * - Mogg Fanatic — "Sacrifice this creature: It deals 1 damage to any target."
 *   ([com.wingedsheep.sdk.dsl.Costs.SacrificeSelf] + [com.wingedsheep.sdk.dsl.Effects.DealDamage]).
 * - Goblin Bombardment — "Sacrifice a creature: This enchantment deals 1 damage to any target."
 *   ([com.wingedsheep.sdk.dsl.Costs.Sacrifice] of a creature filter + DealDamage).
 * - Flowstone Giant — "{R}: This creature gets +2/-2 until end of turn." (ModifyStats on Self).
 * - Canyon Drake — "Flying; {1}, Discard a card at random: gets +2/+0 until end of turn."
 *   ([com.wingedsheep.sdk.dsl.Costs.DiscardAtRandom] + ModifyStats on Self).
 */
class TmpRedActivatedScenarioTest : ScenarioTestBase() {

    init {
        context("Mogg Fanatic — Sacrifice this creature: 1 damage to any target") {
            test("sacrifices itself and deals 1 damage to a player") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Mogg Fanatic", summoningSickness = false)
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val fanatic = game.findPermanent("Mogg Fanatic")!!
                val abilityId = cardRegistry.getCard("Mogg Fanatic")!!.activatedAbilities.first().id

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = fanatic,
                        abilityId = abilityId,
                        targets = listOf(ChosenTarget.Player(game.player2Id)),
                    )
                )
                withClue("Activating Mogg Fanatic should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                withClue("Mogg Fanatic is sacrificed as a cost") {
                    game.isOnBattlefield("Mogg Fanatic") shouldBe false
                }
                game.resolveStack()

                withClue("Player 2 takes 1 damage (20 -> 19)") {
                    game.getLifeTotal(2) shouldBe 19
                }
            }

            test("kills a 1-toughness creature") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Mogg Fanatic", summoningSickness = false)
                    .withCardOnBattlefield(2, "Goblin Bombardment") // unrelated enchantment, just board context
                    .withCardOnBattlefield(2, "Grizzly Bears") // a 2/2; 1 damage won't kill it
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val myFanatic = game.findPermanents("Mogg Fanatic")
                    .first { game.state.projectedState.getController(it) == game.player1Id }
                val bears = game.findPermanent("Grizzly Bears")!!
                val abilityId = cardRegistry.getCard("Mogg Fanatic")!!.activatedAbilities.first().id

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = myFanatic,
                        abilityId = abilityId,
                        targets = listOf(ChosenTarget.Permanent(bears)),
                    )
                )
                withClue("Activation should succeed: ${result.error}") { result.error shouldBe null }
                game.resolveStack()

                withClue("Grizzly Bears (2/2) survives 1 damage") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe true
                }
            }
        }

        context("Goblin Bombardment — Sacrifice a creature: 1 damage to any target") {
            test("sacrifices a chosen creature and deals 1 damage to a player") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Goblin Bombardment")
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bombardment = game.findPermanent("Goblin Bombardment")!!
                val bears = game.findPermanent("Grizzly Bears")!!
                val abilityId = cardRegistry.getCard("Goblin Bombardment")!!.activatedAbilities.first().id

                val result = game.execute(
                    ActivateAbility(
                        playerId = game.player1Id,
                        sourceId = bombardment,
                        abilityId = abilityId,
                        costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(bears)),
                        targets = listOf(ChosenTarget.Player(game.player2Id)),
                    )
                )
                withClue("Activating Goblin Bombardment should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                withClue("Grizzly Bears was sacrificed as a cost") {
                    game.isOnBattlefield("Grizzly Bears") shouldBe false
                }
                withClue("Goblin Bombardment remains (it is not sacrificed)") {
                    game.isOnBattlefield("Goblin Bombardment") shouldBe true
                }
                game.resolveStack()

                withClue("Player 2 takes 1 damage (20 -> 19)") {
                    game.getLifeTotal(2) shouldBe 19
                }
            }
        }

        context("Flowstone Giant — {R}: gets +2/-2 until end of turn") {
            test("pumping once makes the 3/3 a 5/1") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Flowstone Giant", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val giant = game.findPermanent("Flowstone Giant")!!
                val abilityId = cardRegistry.getCard("Flowstone Giant")!!.activatedAbilities.first().id

                val result = game.execute(
                    ActivateAbility(playerId = game.player1Id, sourceId = giant, abilityId = abilityId)
                )
                withClue("Activating Flowstone Giant should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                game.resolveStack()

                withClue("Power becomes 3+2 = 5") {
                    game.state.projectedState.getPower(giant) shouldBe 5
                }
                withClue("Toughness becomes 3-2 = 1") {
                    game.state.projectedState.getToughness(giant) shouldBe 1
                }
            }
        }

        context("Canyon Drake — Flying; {1}, Discard a card at random: gets +2/+0") {
            test("has flying and pumping discards a card at random while making it a 3/2") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Canyon Drake", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInHand(1, "Grizzly Bears") // the only random-discard candidate
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val drake = game.findPermanent("Canyon Drake")!!
                withClue("Canyon Drake has flying") {
                    game.state.projectedState.hasKeyword(drake, Keyword.FLYING) shouldBe true
                }

                val abilityId = cardRegistry.getCard("Canyon Drake")!!.activatedAbilities.first().id
                val result = game.execute(
                    ActivateAbility(playerId = game.player1Id, sourceId = drake, abilityId = abilityId)
                )
                withClue("Activating Canyon Drake should succeed: ${result.error}") {
                    result.error shouldBe null
                }
                withClue("The single hand card was discarded as the at-random cost") {
                    game.state.getHand(game.player1Id).size shouldBe 0
                }
                game.resolveStack()

                withClue("Power becomes 1+2 = 3") {
                    game.state.projectedState.getPower(drake) shouldBe 3
                }
                withClue("Toughness stays 2") {
                    game.state.projectedState.getToughness(drake) shouldBe 2
                }
            }
        }
    }
}
