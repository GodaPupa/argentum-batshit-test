package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe

/** Exact-card qualification for Faerie Macabre (SHM #66). */
class FaerieMacabreScenarioTest : ScenarioTestBase() {

    private fun TestGame.activate(targets: List<ChosenTarget>) =
        execute(
            ActivateAbility(
                playerId = player1Id,
                sourceId = findCardsInHand(1, "Faerie Macabre").single(),
                abilityId = cardRegistry.getCard("Faerie Macabre")!!.activatedAbilities.single().id,
                targets = targets,
            )
        )

    init {
        test("discard-from-hand ability exiles two targeted cards from different graveyards") {
            val game = scenario()
                .withPlayers("Macabre controller", "Opponent")
                .withCardInHand(1, "Faerie Macabre")
                .withCardInGraveyard(1, "Grizzly Bears")
                .withCardInGraveyard(2, "Hill Giant")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val own = game.findCardsInGraveyard(1, "Grizzly Bears").single()
            val opposing = game.findCardsInGraveyard(2, "Hill Giant").single()

            game.activate(
                listOf(
                    ChosenTarget.Card(own, game.player1Id, Zone.GRAVEYARD),
                    ChosenTarget.Card(opposing, game.player2Id, Zone.GRAVEYARD),
                )
            ).error shouldBe null

            // Discarding Faerie Macabre is the activation cost.
            game.isInHand(1, "Faerie Macabre") shouldBe false
            game.isInGraveyard(1, "Faerie Macabre") shouldBe true

            game.resolveStack()

            game.isInGraveyard(1, "Grizzly Bears") shouldBe false
            game.isInExile(1, "Grizzly Bears") shouldBe true
            game.isInGraveyard(2, "Hill Giant") shouldBe false
            game.isInExile(2, "Hill Giant") shouldBe true
        }

        test("ability may choose exactly one graveyard card") {
            val game = scenario()
                .withPlayers("Macabre controller", "Opponent")
                .withCardInHand(1, "Faerie Macabre")
                .withCardInGraveyard(2, "Hill Giant")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val opposing = game.findCardsInGraveyard(2, "Hill Giant").single()
            game.activate(
                listOf(ChosenTarget.Card(opposing, game.player2Id, Zone.GRAVEYARD))
            ).error shouldBe null
            game.resolveStack()

            game.isInExile(2, "Hill Giant") shouldBe true
            game.isInGraveyard(1, "Faerie Macabre") shouldBe true
        }

        test("ability may choose zero targets and still pays discard-self cost") {
            val game = scenario()
                .withPlayers("Macabre controller", "Opponent")
                .withCardInHand(1, "Faerie Macabre")
                .withCardInGraveyard(2, "Hill Giant")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.activate(emptyList()).error shouldBe null
            game.resolveStack()

            game.isInGraveyard(1, "Faerie Macabre") shouldBe true
            game.isInGraveyard(2, "Hill Giant") shouldBe true
        }
    }
}
