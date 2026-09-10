package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/**
 * Scenario tests for four Tarkir: Dragonstorm commons/uncommons that compose existing
 * SDK primitives:
 *
 * - Ainok Wayfarer — ETB mill 3, may take a land into hand, else +1/+1 counter
 * - Snowmelt Stag — "during your turn" base power/toughness 5/2 conditional static
 * - Iridescent Tiger — ETB "if you cast it, add {W}{U}{B}{R}{G}"
 * - Salt Road Packbeast — Affinity for creatures + ETB draw
 */
class TarkirDragonstormCommonsScenarioTest : ScenarioTestBase() {

    init {
        context("Ainok Wayfarer") {

            test("mills three and takes a land into hand when one is available") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardInHand(1, "Ainok Wayfarer")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    // Top three of library: two nonland + a Mountain to grab.
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Ainok Wayfarer")
                game.resolveStack() // enters → ETB → mill 3 → pause to choose a land

                val mountainId = game.findCardsInGraveyard(1, "Mountain").first()
                game.selectCards(listOf(mountainId))

                // The Mountain is now in hand; the other two milled cards stay in the graveyard.
                game.isInHand(1, "Mountain") shouldBe true
                game.findCardsInGraveyard(1, "Grizzly Bears").size shouldBe 2

                // No +1/+1 counter, since a land was taken.
                val wayfarer = game.findPermanent("Ainok Wayfarer")!!
                val counters = game.state.getEntity(wayfarer)?.get<CountersComponent>()
                (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 0
            }

            test("gets a +1/+1 counter when no land is taken") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardInHand(1, "Ainok Wayfarer")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    // No lands among the top three → "if you don't" branch fires.
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withCardInLibrary(1, "Grizzly Bears")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Ainok Wayfarer")
                game.resolveStack() // enters → mill 3 → no land to take

                // All three milled cards are in the graveyard.
                game.findCardsInGraveyard(1, "Grizzly Bears").size shouldBe 3

                val wayfarer = game.findPermanent("Ainok Wayfarer")!!
                val counters = game.state.getEntity(wayfarer)?.get<CountersComponent>()
                (counters?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0) shouldBe 1
            }
        }

        context("Snowmelt Stag") {

            test("has base 5/2 during its controller's turn and base 2/5 otherwise") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardOnBattlefield(1, "Snowmelt Stag")
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val stag = game.findPermanent("Snowmelt Stag")!!

                // During your turn: base power/toughness 5/2.
                game.state.projectedState.getPower(stag) shouldBe 5
                game.state.projectedState.getToughness(stag) shouldBe 2

                // Advance past player 1's main phase, then into the opponent's turn —
                // base reverts to the printed 2/5 once it's no longer the controller's turn.
                game.passUntilPhase(Phase.ENDING, Step.END)
                game.passUntilPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                game.state.activePlayerId shouldBe game.player2Id
                game.state.projectedState.getPower(stag) shouldBe 2
                game.state.projectedState.getToughness(stag) shouldBe 5
            }
        }

        context("Iridescent Tiger") {

            test("adds {W}{U}{B}{R}{G} when cast") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardInHand(1, "Iridescent Tiger")
                    .withLandsOnBattlefield(1, "Mountain", 5)
                    .withCardInLibrary(1, "Plains")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                game.castSpell(1, "Iridescent Tiger").error shouldBe null
                game.resolveStack() // enters → ETB adds WUBRG

                val pool = game.state.getEntity(game.player1Id)?.get<ManaPoolComponent>()!!
                pool.white shouldBe 1
                pool.blue shouldBe 1
                pool.black shouldBe 1
                pool.red shouldBe 1
                pool.green shouldBe 1
            }
        }

        context("Salt Road Packbeast") {

            test("affinity for creatures reduces the generic cost and ETB draws a card") {
                val game = scenario()
                    .withPlayers("Player1", "Opponent")
                    .withCardInHand(1, "Salt Road Packbeast")
                    // Three creatures → affinity shaves {3} off {5}{W}, so {2}{W} = 3 lands.
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Plains", 3)
                    .withCardInLibrary(1, "Mountain")
                    .withCardInLibrary(2, "Plains")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val handBefore = game.findCardsInHand(1, "Mountain").size

                game.castSpell(1, "Salt Road Packbeast").error shouldBe null
                game.resolveStack() // enters → ETB draw a card

                game.isOnBattlefield("Salt Road Packbeast") shouldBe true
                // The top-of-library Mountain was drawn by the ETB.
                game.findCardsInHand(1, "Mountain").size shouldBe handBefore + 1
            }
        }
    }
}
