package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.mechanics.stack.StackResolver
import com.wingedsheep.engine.state.components.identity.CopyOfComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.mh2.cards.LoseFocus
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class LoseFocusScenarioTest : FunSpec({
    val probe = card("Lose Focus Target Probe") {
        manaCost = "{0}"; typeLine = "Instant"
        spell { effect = Effects.DrawCards(1) }
    }
    fun driver() = GameTestDriver().apply {
        registerCards(TestCards.all); registerCard(probe); registerCard(LoseFocus)
        initMirrorMatch(Deck.of("Forest" to 40)); passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun GameTestDriver.targetSpell(): EntityId {
        val p = activePlayer!!; val id = putCardInHand(p, probe.name)
        submit(CastSpell(p, id, paymentStrategy = PaymentStrategy.FromPool)).error shouldBe null
        return id
    }
    fun GameTestDriver.focus(target: EntityId, count: Int): EntityId {
        val p = activePlayer!!; giveMana(p, Color.BLUE, 10)
        val id = putCardInHand(p, "Lose Focus")
        submit(CastSpell(p, id, targets = listOf(ChosenTarget.Spell(target)), paymentStrategy = PaymentStrategy.FromPool,
            declaredCostSlot = if (count > 0) ChoiceSlot.REPLICATED else null,
            optionalCostCounts = if (count > 0) mapOf(ChoiceSlot.REPLICATED to count) else emptyMap()
        )).error shouldBe null
        return id
    }
    for (count in listOf(0, 1, 3)) {
        test("$count replicate payments cost exactly two plus $count mana and form $count copies") {
            val g = driver(); val p = g.activePlayer!!; val target = g.targetSpell()
            g.focus(target, count)
            g.state.getEntity(p)!!.get<ManaPoolComponent>()!!.total shouldBe 8 - count
            if (count > 0) {
                g.bothPass().error shouldBe null
                repeat(count) { g.submitTargetSelection(p, listOf(target)).error shouldBe null }
            }
            g.state.stack.count { g.state.getEntity(it)?.has<CopyOfComponent>() == true } shouldBe count
            g.state.stack.size shouldBe count + 2
        }
    }
    test("copies can choose a new spell target while the original keeps its target") {
        val g = driver(); val p = g.activePlayer!!
        val first = g.targetSpell(); val second = g.targetSpell(); val original = g.focus(first, 1)
        g.bothPass().error shouldBe null
        g.submitTargetSelection(p, listOf(second)).error shouldBe null
        val copy = g.state.stack.single { g.state.getEntity(it)?.has<CopyOfComponent>() == true }
        g.state.getEntity(copy)!!.get<TargetsComponent>()!!.targets shouldBe listOf(ChosenTarget.Spell(second))
        g.state.getEntity(original)!!.get<TargetsComponent>()!!.targets shouldBe listOf(ChosenTarget.Spell(first))
    }
    test("replicate still forms and resolves copies when the original is genuinely countered") {
        val g = driver(); val p = g.activePlayer!!; val target = g.targetSpell(); val original = g.focus(target, 2)
        g.replaceState(StackResolver(cardRegistry = g.cardRegistry).counterSpell(g.state, original).newState)
        (original in g.state.stack) shouldBe false
        g.bothPass().error shouldBe null
        repeat(2) { g.submitTargetSelection(p, listOf(target)).error shouldBe null }
        g.state.stack.count { g.state.getEntity(it)?.has<CopyOfComponent>() == true } shouldBe 2
        g.bothPass().error shouldBe null
        if (g.pendingDecision != null) g.submitManaAutoPayOrDecline(p, false).error shouldBe null
        (target in g.state.stack) shouldBe false
        g.bothPass().error shouldBe null
        g.state.stack.size shouldBe 0
    }
    test("paying two preserves the target spell") {
        val g = driver(); val p = g.activePlayer!!; val target = g.targetSpell()
        g.focus(target, 0); g.bothPass().error shouldBe null
        g.submitManaAutoPayOrDecline(p, true).error shouldBe null
        (target in g.state.stack) shouldBe true
        g.state.getEntity(p)!!.get<ManaPoolComponent>()!!.total shouldBe 6
    }
    test("declining two counters the target spell") {
        val g = driver(); val p = g.activePlayer!!; val target = g.targetSpell()
        g.focus(target, 0); g.bothPass().error shouldBe null
        g.submitManaAutoPayOrDecline(p, false).error shouldBe null
        (target in g.state.stack) shouldBe false
    }
})
