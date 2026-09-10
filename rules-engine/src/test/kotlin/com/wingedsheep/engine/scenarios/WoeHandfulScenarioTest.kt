package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for a handful of Wilds of Eldraine cards implemented together:
 *
 *  - Regal Bunnicorn ({1}{W}) — CDA P/T = number of nonland permanents you control.
 *  - Skewer Slinger ({1}{R} 1/3, Reach) — pings the combat partner for 1 when it blocks or
 *    becomes blocked.
 *  - Monstrous Rage ({R} instant) — +2/+0 to a creature and creates a Monster Role token
 *    (which itself grants +1/+1 and trample).
 *  - Stormkeld Prowler ({1}{U} 2/1) — two +1/+1 counters whenever you cast a spell with mana
 *    value 5 or greater.
 */
class WoeHandfulScenarioTest : ScenarioTestBase() {

    init {
        context("Regal Bunnicorn — CDA counts nonland permanents you control") {
            test("power and toughness equal the number of nonland permanents you control") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Regal Bunnicorn", summoningSickness = false)
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withLandsOnBattlefield(1, "Forest", 3)
                    .withCardOnBattlefield(2, "Grizzly Bears", summoningSickness = false)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bunnicorn = game.findPermanent("Regal Bunnicorn")!!

                withClue("3 nonland permanents you control (Bunnicorn + 2 Bears); lands and the opponent's Bear don't count") {
                    game.state.projectedState.getPower(bunnicorn) shouldBe 3
                    game.state.projectedState.getToughness(bunnicorn) shouldBe 3
                }
            }
        }

        context("Monstrous Rage — pump plus a Monster Role token") {
            test("target gets +2/+0 from the spell and +1/+1 and trample from the Monster Role") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Grizzly Bears", summoningSickness = false)
                    .withCardInHand(1, "Monstrous Rage")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bear = game.findPermanent("Grizzly Bears")!!

                game.castSpell(1, "Monstrous Rage", bear)
                game.resolveStack()

                withClue("2/2 Bear -> +2/+0 (spell) + +1/+1 (Monster Role) = 5/3") {
                    game.state.projectedState.getPower(bear) shouldBe 5
                    game.state.projectedState.getToughness(bear) shouldBe 3
                }
                withClue("The Monster Role grants trample") {
                    game.state.projectedState.hasKeyword(bear, Keyword.TRAMPLE) shouldBe true
                }
                withClue("A Monster Role token was created") {
                    (game.findPermanent("Monster Role") != null) shouldBe true
                }
            }
        }

        context("Stormkeld Prowler — counters on casting an expensive spell") {
            test("casting a mana value 5+ spell adds two +1/+1 counters") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Stormkeld Prowler", summoningSickness = false)
                    .withCardInHand(1, "Craw Wurm")
                    .withLandsOnBattlefield(1, "Forest", 6)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val prowler = game.findPermanent("Stormkeld Prowler")!!

                game.castSpell(1, "Craw Wurm")
                game.resolveStack()

                withClue("Craw Wurm is mana value 6 (>= 5) -> two +1/+1 counters on the Prowler") {
                    game.state.getEntity(prowler)?.get<CountersComponent>()
                        ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 2
                }
                withClue("2/1 base + two +1/+1 counters = 4/3") {
                    game.state.projectedState.getPower(prowler) shouldBe 4
                    game.state.projectedState.getToughness(prowler) shouldBe 3
                }
            }
        }

        context("Skewer Slinger — pings its combat partner") {
            test("blocking a creature deals it 1 damage on top of combat damage") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Skewer Slinger", summoningSickness = false)
                    .withCardOnBattlefield(2, "Grizzly Bears", summoningSickness = false)
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(2, "Forest")
                    .withActivePlayer(2)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                // Player 2's 2/2 Bear attacks; Player 1's 1/3 Skewer Slinger blocks it.
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_ATTACKERS)
                game.declareAttackers(mapOf("Grizzly Bears" to 1)).error shouldBe null
                game.passUntilPhase(Phase.COMBAT, Step.DECLARE_BLOCKERS)
                game.declareBlockers(mapOf("Skewer Slinger" to listOf("Grizzly Bears"))).error shouldBe null

                // Pass priority so the "deals 1 damage to that creature" trigger resolves, then
                // continue through the combat damage step (which deals the remaining combat damage).
                game.passUntilPhase(Phase.POSTCOMBAT_MAIN, Step.POSTCOMBAT_MAIN)

                withClue("Bear took 1 (trigger) + 1 (Skewer's combat power) = 2 -> dies; without the trigger it would survive") {
                    game.findPermanent("Grizzly Bears") shouldBe null
                }
                withClue("Skewer Slinger (1/3) survives the Bear's 2 combat damage") {
                    (game.findPermanent("Skewer Slinger") != null) shouldBe true
                }
            }
        }
    }
}
