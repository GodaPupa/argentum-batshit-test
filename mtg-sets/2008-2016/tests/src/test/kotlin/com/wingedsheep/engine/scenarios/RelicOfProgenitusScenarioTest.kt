package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/** Exact-card qualification for Relic of Progenitus (ALA #218). */
class RelicOfProgenitusScenarioTest : ScenarioTestBase() {

    private fun TestGame.activate(index: Int, targets: List<ChosenTarget> = emptyList()) =
        execute(
            ActivateAbility(
                playerId = player1Id,
                sourceId = findPermanent("Relic of Progenitus")
                    ?: error("Relic is not on the battlefield"),
                abilityId = cardRegistry.getCard("Relic of Progenitus")!!.activatedAbilities[index].id,
                targets = targets,
                paymentStrategy = PaymentStrategy.AutoPay,
            )
        )

    init {
        test("tap ability makes the targeted player choose the card exiled from their graveyard") {
            val game = scenario()
                .withPlayers("Relic controller", "Targeted player")
                .withCardOnBattlefield(1, "Relic of Progenitus")
                .withCardInGraveyard(2, "Grizzly Bears")
                .withCardInGraveyard(2, "Centaur Courser")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            game.activate(0, listOf(ChosenTarget.Player(game.player2Id))).error shouldBe null
            game.resolveStack()

            val decision = game.getPendingDecision().shouldNotBeNull()
            decision.playerId shouldBe game.player2Id

            val courser = game.findCardsInGraveyard(2, "Centaur Courser").single()
            game.selectCards(listOf(courser))

            game.isInExile(2, "Centaur Courser") shouldBe true
            game.isInGraveyard(2, "Grizzly Bears") shouldBe true
        }

        test("second ability exiles Relic as a cost then exiles every graveyard and draws") {
            val game = scenario()
                .withPlayers("Relic controller", "Opponent")
                .withCardOnBattlefield(1, "Relic of Progenitus")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInLibrary(1, "Hill Giant")
                .withCardInGraveyard(1, "Grizzly Bears")
                .withCardInGraveyard(2, "Centaur Courser")
                .withCardInGraveyard(2, "Ornithopter")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val handBefore = game.handSize(1)
            game.activate(1).error shouldBe null

            // Exiling the source is a cost, so Relic is gone before its ability resolves.
            game.isOnBattlefield("Relic of Progenitus") shouldBe false
            game.isInExile(1, "Relic of Progenitus") shouldBe true

            game.resolveStack()

            game.graveyardSize(1) shouldBe 0
            game.graveyardSize(2) shouldBe 0
            game.isInExile(1, "Grizzly Bears") shouldBe true
            game.isInExile(2, "Centaur Courser") shouldBe true
            game.isInExile(2, "Ornithopter") shouldBe true
            game.handSize(1) shouldBe handBefore + 1
        }

        test("second ability is legal with empty graveyards and still draws") {
            val game = scenario()
                .withPlayers("Relic controller", "Opponent")
                .withCardOnBattlefield(1, "Relic of Progenitus")
                .withLandsOnBattlefield(1, "Forest", 1)
                .withCardInLibrary(1, "Hill Giant")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val handBefore = game.handSize(1)
            game.activate(1).error shouldBe null
            game.resolveStack()

            game.isInExile(1, "Relic of Progenitus") shouldBe true
            game.handSize(1) shouldBe handBefore + 1
            game.graveyardSize(1) shouldBe 0
            game.graveyardSize(2) shouldBe 0
        }
    }
}
