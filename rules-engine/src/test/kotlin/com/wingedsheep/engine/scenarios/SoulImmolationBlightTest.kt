package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Scenario test for Soul Immolation (ECL #156) — {3}{R}{R} Sorcery.
 *
 * "As an additional cost to cast this spell, blight X. ... Soul Immolation deals X damage to each
 *  opponent and each creature they control."
 *
 * Phase 2 proof: the blight X is no longer read through the bespoke
 * `ContextProperty(ADDITIONAL_COST_BLIGHT_AMOUNT)` channel — both damage effects now read
 * `DynamicAmount.CastChoice(ChoiceSlot.BLIGHT_AMOUNT)`, the generic cast-choice slot reader, which
 * resolves the value declared at cast (falling back to the resolution context for a sorcery that
 * never becomes a permanent).
 */
class SoulImmolationBlightTest : FunSpec({

    fun GameTestDriver.resolveStack() {
        var safety = 0
        while (stackSize > 0 && !isPaused && safety < 20) {
            bothPass(); safety++
        }
    }

    test("blight 2 deals 2 to each opponent and each creature they control") {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.initMirrorMatch(deck = Deck.of("Mountain" to 40), startingLife = 20)
        val player = driver.activePlayer!!
        val opponent = driver.getOpponent(player)
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        // Own 2/2 to blight (greatest toughness 2 → X may be 2).
        val fodder = driver.putCreatureOnBattlefield(player, "Grizzly Bears")
        // Opponent's 2/2 — should take 2 and die.
        val enemy = driver.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        repeat(5) { driver.putLandOnBattlefield(player, "Mountain") }

        val soulImmolation = driver.putCardInHand(player, "Soul Immolation")
        driver.submitSuccess(
            CastSpell(
                playerId = player,
                cardId = soulImmolation,
                paymentStrategy = PaymentStrategy.AutoPay,
                additionalCostPayment = AdditionalCostPayment(
                    blightAmount = 2,
                    blightTargets = listOf(fodder)
                )
            )
        )
        driver.resolveStack()

        // X = 2 fed both the player-damage and the per-creature damage.
        driver.getLifeTotal(opponent) shouldBe 18
        driver.findPermanent(opponent, "Grizzly Bears") shouldBe null
    }
})
