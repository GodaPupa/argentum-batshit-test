package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.TappedEvent
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.nph.cards.Dispatch
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.GameRng
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DispatchScenarioTest : FunSpec({
    fun driver() = GameTestDriver().apply {
        registerCards(TestCards.all + Dispatch)
        initMirrorMatch(Deck.of("Plains" to 40), skipMulligans = true)
        // Initial shuffle is of identical basic lands; pin subsequent fixture RNG explicitly.
        replaceState(state.copy(rng = GameRng.seeded(2026092605L)))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    test("without metalcraft it taps and does not exile") {
        val d = driver()
        val me = d.activePlayer!!
        repeat(2) { d.putPermanentOnBattlefield(me, "Sol Ring") }
        val victim = d.putCreatureOnBattlefield(d.getOpponent(me), "Grizzly Bears")
        val spell = d.putCardInHand(me, "Dispatch")
        d.giveMana(me, Color.WHITE, 1)
        d.castSpell(me, spell, listOf(victim)).isSuccess shouldBe true
        d.bothPass()
        d.isTapped(victim) shouldBe true
        d.state.getBattlefield().contains(victim) shouldBe true
    }

    test("three artifacts produce a tap event before exile") {
        val d = driver()
        val me = d.activePlayer!!
        repeat(3) { d.putPermanentOnBattlefield(me, "Sol Ring") }
        val opponent = d.getOpponent(me)
        val victim = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val spell = d.putCardInHand(me, "Dispatch")
        d.giveMana(me, Color.WHITE, 1)
        d.castSpell(me, spell, listOf(victim)).isSuccess shouldBe true
        val result = d.bothPass()
        val tapIndex = result.events.indexOfFirst { it is TappedEvent && it.entityId == victim }
        val exileIndex = result.events.indexOfFirst {
            it is ZoneChangeEvent && it.entityId == victim && it.toZone == Zone.EXILE
        }
        (tapIndex >= 0 && exileIndex > tapIndex) shouldBe true
        d.getExile(opponent).contains(victim) shouldBe true
    }

    test("losing the third artifact in response prevents exile but still taps") {
        val d = driver()
        val me = d.activePlayer!!
        val opponent = d.getOpponent(me)
        val artifact = d.putPermanentOnBattlefield(me, "Sol Ring")
        repeat(2) { d.putPermanentOnBattlefield(me, "Sol Ring") }
        val victim = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        val spell = d.putCardInHand(me, "Dispatch")
        d.giveMana(me, Color.WHITE, 1)
        d.castSpell(me, spell, listOf(victim)).isSuccess shouldBe true
        if (d.state.priorityPlayerId != opponent) d.passPriority(me)
        val shatter = d.putCardInHand(opponent, "Shatter")
        d.giveMana(opponent, Color.RED, 2)
        d.castSpell(opponent, shatter, listOf(artifact)).isSuccess shouldBe true
        d.bothPass()
        d.bothPass()
        d.state.getBattlefield().contains(victim) shouldBe true
        d.isTapped(victim) shouldBe true
    }

    test("an already tapped target is still exiled with metalcraft") {
        val d = driver()
        val me = d.activePlayer!!
        repeat(3) { d.putPermanentOnBattlefield(me, "Sol Ring") }
        val opponent = d.getOpponent(me)
        val victim = d.putCreatureOnBattlefield(opponent, "Grizzly Bears")
        d.tapPermanent(victim)
        val spell = d.putCardInHand(me, "Dispatch")
        d.giveMana(me, Color.WHITE, 1)
        d.castSpell(me, spell, listOf(victim)).isSuccess shouldBe true
        d.bothPass()
        d.getExile(opponent).contains(victim) shouldBe true
    }
})
