package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.state.components.identity.CopyOfComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.KeywordAbility
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RepeatedSpellCopyTest : FunSpec({
    val probe = card("Repeated Copy Probe") {
        manaCost = "{1}"
        typeLine = "Instant"
        keywordAbility(KeywordAbility.replicate("{2}"))
        spell { effect = Effects.DrawCards(1) }
    }
    fun driver() = GameTestDriver().apply {
        registerCards(TestCards.all); registerCard(probe)
        initMirrorMatch(Deck.of("Forest" to 40)); passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    for (count in listOf(0, 1, 3)) {
        test("$count repeat payments produce exactly $count copies and preserve the paid count") {
            val g = driver(); val p = g.activePlayer!!
            g.giveColorlessMana(p, 10)
            val id = g.putCardInHand(p, probe.name)
            g.submit(CastSpell(p, id, paymentStrategy = PaymentStrategy.FromPool,
                declaredCostSlot = if (count > 0) ChoiceSlot.REPLICATED else null,
                optionalCostCounts = if (count > 0) mapOf(ChoiceSlot.REPLICATED to count) else emptyMap()
            )).error shouldBe null
            g.state.getEntity(p)!!.get<ManaPoolComponent>()!!.total shouldBe 9 - 2 * count
            if (count > 0) g.bothPass().error shouldBe null
            val copies = g.state.stack.filter { g.state.getEntity(it)?.has<CopyOfComponent>() == true }
            copies.size shouldBe count
            copies.forEach { g.state.getEntity(it)!!.get<SpellOnStackComponent>()!!.optionalCostCounts[ChoiceSlot.REPLICATED] shouldBe count }
            g.state.stack.size shouldBe count + 1
        }
    }
    test("genuinely countering the original does not prevent its replicate trigger creating copies") {
        val g = driver(); val p = g.activePlayer!!
        g.giveColorlessMana(p, 5)
        val id = g.putCardInHand(p, probe.name)
        g.submit(CastSpell(p, id, paymentStrategy = PaymentStrategy.FromPool,
            declaredCostSlot = ChoiceSlot.REPLICATED, optionalCostCounts = mapOf(ChoiceSlot.REPLICATED to 2)
        )).error shouldBe null
        g.replaceState(StackResolver(cardRegistry = g.cardRegistry).counterSpell(g.state, id).newState)
        (id in g.state.stack) shouldBe false
        g.bothPass().error shouldBe null
        g.state.stack.size shouldBe 2
        g.state.stack.all { g.state.getEntity(it)?.has<CopyOfComponent>() == true } shouldBe true
        repeat(2) { g.bothPass().error shouldBe null }
        g.state.stack.size shouldBe 0
    }
})
