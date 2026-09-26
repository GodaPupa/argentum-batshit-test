package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mh3.cards.EvisceratorsInsight
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.GameRng
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class EvisceratorsInsightScenarioTest : FunSpec({
    fun driver() = GameTestDriver().apply {
        registerCards(TestCards.all + EvisceratorsInsight)
        initMirrorMatch(Deck.of("Swamp" to 40), skipMulligans = true)
        // Initial shuffle is of identical basic lands; pin subsequent fixture RNG explicitly.
        replaceState(state.copy(rng = GameRng.seeded(2026092604L)))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }

    for (artifact in listOf(false, true)) {
        test("normal cast sacrifices a ${if (artifact) "noncreature artifact" else "creature"} as a cost before drawing") {
            val d = driver()
            val me = d.activePlayer!!
            val fodder = if (artifact) d.putPermanentOnBattlefield(me, "Sol Ring")
                else d.putCreatureOnBattlefield(me, "Grizzly Bears")
            val spell = d.putCardInHand(me, "Eviscerator's Insight")
            val before = d.getHandSize(me)
            d.giveMana(me, Color.BLACK, 2)
            d.submit(CastSpell(
                me, spell, paymentStrategy = PaymentStrategy.FromPool,
                additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(fodder))
            )).isSuccess shouldBe true
            d.state.getBattlefield().contains(fodder) shouldBe false
            d.getGraveyard(me).contains(fodder) shouldBe true
            d.getHandSize(me) shouldBe before - 1
            d.bothPass()
            d.getHandSize(me) shouldBe before + 1
            d.getGraveyard(me).contains(spell) shouldBe true
        }
    }

    test("flashback requires a new sacrifice and draws two before exile") {
        val d = driver()
        val me = d.activePlayer!!
        val spell = d.putCardInGraveyard(me, "Eviscerator's Insight")
        val fodder = d.putCreatureOnBattlefield(me, "Grizzly Bears")
        val before = d.getHandSize(me)
        d.giveMana(me, Color.BLACK, 5)
        d.submit(CastSpell(
            me, spell, paymentStrategy = PaymentStrategy.FromPool,
            useAlternativeCost = true, alternativeCostType = AlternativeCostType.FLASHBACK,
            additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(fodder))
        )).isSuccess shouldBe true
        d.state.getBattlefield().contains(fodder) shouldBe false
        d.getHandSize(me) shouldBe before
        d.bothPass()
        d.getHandSize(me) shouldBe before + 2
        d.getExile(me).contains(spell) shouldBe true
        d.getGraveyard(me).contains(spell) shouldBe false
    }

    test("an ordinary land cannot pay the sacrifice cost even for flashback") {
        val d = driver()
        val me = d.activePlayer!!
        val spell = d.putCardInGraveyard(me, "Eviscerator's Insight")
        val land = d.putLandOnBattlefield(me, "Swamp")
        d.giveMana(me, Color.BLACK, 5)
        d.submit(CastSpell(
            me, spell, paymentStrategy = PaymentStrategy.FromPool,
            useAlternativeCost = true, alternativeCostType = AlternativeCostType.FLASHBACK,
            additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(land))
        )).isSuccess shouldBe false
        d.state.getBattlefield().contains(land) shouldBe true
        d.getGraveyard(me).contains(spell) shouldBe true
    }

    test("countering a flashback spell keeps its cost paid and exiles it without drawing") {
        val d = driver()
        val me = d.activePlayer!!
        val opponent = d.getOpponent(me)
        val spell = d.putCardInGraveyard(me, "Eviscerator's Insight")
        val fodder = d.putCreatureOnBattlefield(me, "Grizzly Bears")
        val before = d.getHandSize(me)
        d.giveMana(me, Color.BLACK, 5)
        d.submit(CastSpell(
            me, spell, paymentStrategy = PaymentStrategy.FromPool,
            useAlternativeCost = true, alternativeCostType = AlternativeCostType.FLASHBACK,
            additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(fodder))
        )).isSuccess shouldBe true
        val stackSpell = d.getTopOfStack()!!
        if (d.state.priorityPlayerId != opponent) d.passPriority(me)
        val counter = d.putCardInHand(opponent, "Counterspell")
        d.giveMana(opponent, Color.BLUE, 2)
        d.castSpellWithTargets(opponent, counter, listOf(ChosenTarget.Spell(stackSpell))).isSuccess shouldBe true
        d.bothPass()
        d.getHandSize(me) shouldBe before
        d.getGraveyard(me).contains(fodder) shouldBe true
        d.getExile(me).contains(spell) shouldBe true
    }
})
