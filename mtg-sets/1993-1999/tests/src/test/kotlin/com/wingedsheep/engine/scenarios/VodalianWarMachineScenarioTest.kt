package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.chooseTriggerOrderInListedOrder
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.fem.cards.VodalianWarMachine
import com.wingedsheep.mtg.sets.definitions.lea.cards.Terror
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * Tests for Vodalian War Machine (Fallen Empires).
 *
 * The clause worth pinning is the death trigger: "destroy all Merfolk tapped this turn to pay for
 * its abilities" has to remember *which* Merfolk paid, across activations of either ability, and
 * must leave alone a Merfolk that was tapped for something else.
 */
class VodalianWarMachineScenarioTest : FunSpec({

    val attackAbility = VodalianWarMachine.activatedAbilities[0].id
    val pumpAbility = VodalianWarMachine.activatedAbilities[1].id

    fun createDriver(): GameTestDriver {
        val driver = GameTestDriver()
        driver.registerCards(TestCards.all)
        driver.registerCard(VodalianWarMachine)
        driver.registerCard(Terror)
        return driver
    }

    test("the Merfolk that paid are destroyed when the War Machine dies; a bystander is not") {
        val driver = createDriver()
        driver.initMirrorMatch(deck = Deck.of("Island" to 40), startingLife = 20)

        val alice = driver.activePlayer!!
        driver.passPriorityUntil(Step.PRECOMBAT_MAIN)

        val machine = driver.putCreatureOnBattlefield(alice, "Vodalian War Machine")
        driver.removeSummoningSickness(machine)
        val payer1 = driver.putCreatureOnBattlefield(alice, "Vodalian Soldiers")
        val payer2 = driver.putCreatureOnBattlefield(alice, "Vodalian Soldiers")
        val bystander = driver.putCreatureOnBattlefield(alice, "Vodalian Soldiers")
        listOf(payer1, payer2, bystander).forEach { driver.removeSummoningSickness(it) }

        driver.submitSuccess(
            ActivateAbility(
                playerId = alice, sourceId = machine, abilityId = attackAbility,
                costPayment = com.wingedsheep.sdk.scripting.AdditionalCostPayment(
                    tappedPermanents = listOf(payer1)
                )
            )
        )
        driver.submitSuccess(
            ActivateAbility(
                playerId = alice, sourceId = machine, abilityId = pumpAbility,
                costPayment = com.wingedsheep.sdk.scripting.AdditionalCostPayment(
                    tappedPermanents = listOf(payer2)
                )
            )
        )
        driver.bothPass().isSuccess shouldBe true
        driver.bothPass().isSuccess shouldBe true

        // Resolve an actual destruction spell. An injected lethal toughness state followed by a
        // single pass only hands priority to the other player; it does not establish this death
        // and its trigger-placement boundary. Terror's accepted resolution supplies both.
        val terror = driver.putCardInHand(alice, "Terror")
        driver.giveMana(alice, Color.BLACK, 2)
        driver.castSpell(alice, terror, listOf(machine)).isSuccess shouldBe true
        val death = driver.bothPass()
        death.error shouldBe null
        death.isPaused shouldBe true
        death.events.filterIsInstance<ZoneChangeEvent>().any {
            it.entityId == machine && it.fromZone == Zone.BATTLEFIELD && it.toZone == Zone.GRAVEYARD
        } shouldBe true
        driver.chooseTriggerOrderInListedOrder()
        driver.passPriorityUntil(Step.END)

        withClue("the War Machine actually died") {
            driver.state.getBattlefield().contains(machine) shouldBe false
        }
        withClue("both Merfolk that paid were destroyed") {
            driver.state.getBattlefield().contains(payer1) shouldBe false
            driver.state.getBattlefield().contains(payer2) shouldBe false
        }
        withClue("the Merfolk that paid for nothing survives") {
            driver.state.getBattlefield().contains(bystander) shouldBe true
        }
    }
})
