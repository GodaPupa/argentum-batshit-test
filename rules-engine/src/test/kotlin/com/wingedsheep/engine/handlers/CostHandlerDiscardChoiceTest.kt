package com.wingedsheep.engine.handlers

import com.wingedsheep.engine.core.CardsDiscardedEvent
import com.wingedsheep.engine.mechanics.mana.ManaPool
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.costs.CostAtom
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Selected discard costs must spend distinct, eligible cards from the payer's current hand. */
class CostHandlerDiscardChoiceTest : FunSpec({
    fun driver(): GameTestDriver = GameTestDriver().also {
        it.registerCards(TestCards.all)
        it.initMirrorMatch(deck = Deck.of("Forest" to 20))
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("discard two rejects one card repeated and accepts two distinct hand cards") {
        val driver = driver()
        val me = driver.activePlayer!!
        val first = driver.putCardInHand(me, "Grizzly Bears")
        val second = driver.putCardInHand(me, "Grizzly Bears")
        val before = driver.state
        val cost = AbilityCost.Atom(CostAtom.Discard(count = 2))
        val handler = CostHandler()
        val rejected = handler.payAbilityCost(before, cost, first, me, ManaPool(),
            CostPaymentChoices(discardChoices = listOf(first, first)))
        rejected.success shouldBe false
        rejected.events.isEmpty() shouldBe true
        driver.state shouldBe before

        val paid = handler.payAbilityCost(before, cost, first, me, ManaPool(),
            CostPaymentChoices(discardChoices = listOf(first, second)))
        paid.success shouldBe true
        paid.newState!!.getZone(ZoneKey(me, Zone.GRAVEYARD)).toSet() shouldBe setOf(first, second)
        paid.events.filterIsInstance<CardsDiscardedEvent>().flatMap { it.cardIds }.toSet() shouldBe setOf(first, second)
    }

    test("discard land rejects an owned nonland and accepts an eligible owned land") {
        val driver = driver()
        val me = driver.activePlayer!!
        val nonland = driver.putCardInHand(me, "Grizzly Bears")
        val land = driver.putCardInHand(me, "Forest")
        val before = driver.state
        val cost = AbilityCost.Atom(CostAtom.Discard(filter = GameObjectFilter.Land))
        val handler = CostHandler()
        handler.canPayAbilityCost(before, cost, nonland, me, ManaPool()) shouldBe true
        val rejected = handler.payAbilityCost(before, cost, nonland, me, ManaPool(),
            CostPaymentChoices(discardChoices = listOf(nonland)))
        rejected.success shouldBe false
        rejected.events.isEmpty() shouldBe true
        driver.state shouldBe before

        val paid = handler.payAbilityCost(before, cost, nonland, me, ManaPool(),
            CostPaymentChoices(discardChoices = listOf(land)))
        paid.success shouldBe true
        paid.newState!!.getZone(ZoneKey(me, Zone.GRAVEYARD)) shouldBe listOf(land)
        (nonland in paid.newState!!.getZone(ZoneKey(me, Zone.HAND))) shouldBe true
    }
})
