package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Scenario tests for Makeshift Munitions. */
class MakeshiftMunitionsScenarioTest : ScenarioTestBase() {

    private fun activation(game: TestGame, sacrifice: com.wingedsheep.sdk.model.EntityId) =
        ActivateAbility(
            playerId = game.player1Id,
            sourceId = game.findPermanent("Makeshift Munitions")!!,
            abilityId = cardRegistry.getCard("Makeshift Munitions")!!.script.activatedAbilities.single().id,
            targets = listOf(ChosenTarget.Player(game.player2Id)),
            costPayment = AdditionalCostPayment(sacrificedPermanents = listOf(sacrifice)),
        )

    init {
        context("Makeshift Munitions — sacrifice costs") {
            test("a creature can be sacrificed for one damage") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Makeshift Munitions")
                    .withCardOnBattlefield(1, "Grizzly Bears")
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val bears = game.findPermanent("Grizzly Bears")!!
                game.execute(activation(game, bears)).error shouldBe null
                withClue("the creature is sacrificed while paying the activation cost") {
                    game.findPermanent("Grizzly Bears") shouldBe null
                    game.getLifeTotal(2) shouldBe 20
                }
                game.resolveStack()
                game.getLifeTotal(2) shouldBe 19
            }

            test("an artifact Treasure can be sacrificed for one damage") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Makeshift Munitions")
                    .withCardOnBattlefield(1, "Treasure", isToken = true)
                    .withLandsOnBattlefield(1, "Mountain", 1)
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val treasure = game.findPermanent("Treasure")!!
                game.execute(activation(game, treasure)).error shouldBe null
                game.findPermanent("Treasure") shouldBe null
                game.resolveStack()
                game.getLifeTotal(2) shouldBe 19
            }

            test("one Treasure cannot both pay the mana and be sacrificed") {
                val game = scenario()
                    .withPlayers("Player1", "Player2")
                    .withCardOnBattlefield(1, "Makeshift Munitions")
                    .withCardOnBattlefield(1, "Treasure", isToken = true)
                    .withLifeTotal(2, 20)
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()

                val treasure = game.findPermanent("Treasure")!!
                val action = activation(game, treasure).copy(
                    paymentStrategy = PaymentStrategy.Explicit(listOf(treasure))
                )
                val result = game.execute(action)

                withClue("the engine must reject double-spending the same Treasure") {
                    result.error shouldNotBe null
                    game.findPermanent("Treasure") shouldBe treasure
                    game.getLifeTotal(2) shouldBe 20
                    game.state.stack.size shouldBe 0
                }
            }
        }
    }
}
