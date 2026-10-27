package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Diagnostic only: this fixture is never added to the production card catalog. */
class ActivatedPhyrexianPaymentDiagnosticTest : FunSpec({
    val fixture = card("Manual Activated Phyrexian Payment Fixture") {
        manaCost = "{2}"
        typeLine = "Artifact Creature — Construct"
        power = 0
        toughness = 4
        activatedAbility {
            cost = Costs.Mana("{U/P}")
            effect = Effects.GainLife(2)
        }
    }
    fun driver() = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(fixture))
        it.initMirrorMatch(Deck.of("Island" to 40))
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun blue(d: GameTestDriver) = d.state.getEntity(d.player1)?.get<ManaPoolComponent>()?.blue ?: 0

    test("APD blue mana pays once and the fixture resolves") {
        val d = driver()
        val id = d.putCreatureOnBattlefield(d.player1, fixture.name)
        d.giveMana(d.player1, Color.BLUE, 1)
        val result = d.submit(ActivateAbility(d.player1, id, fixture.activatedAbilities.single().id,
            paymentStrategy = PaymentStrategy.Explicit(emptyList())))
        result.error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 20
        blue(d) shouldBe 0
        d.state.stack.size shouldBe 1
        d.bothPass().error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 21
        d.state.stack.size shouldBe 0
    }

    test("APD explicit life payment works without blue mana") {
        val d = driver()
        val id = d.putCreatureOnBattlefield(d.player1, fixture.name)
        val result = d.submit(ActivateAbility(d.player1, id, fixture.activatedAbilities.single().id,
            paymentStrategy = PaymentStrategy.Explicit(emptyList(), listOf(Color.BLUE))))
        result.error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 18
        blue(d) shouldBe 0
        d.state.stack.size shouldBe 1
        d.bothPass().error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 19
    }

    test("APD chosen life payment preserves available blue mana") {
        val d = driver()
        val id = d.putCreatureOnBattlefield(d.player1, fixture.name)
        d.giveMana(d.player1, Color.BLUE, 1)
        val result = d.submit(ActivateAbility(d.player1, id, fixture.activatedAbilities.single().id,
            paymentStrategy = PaymentStrategy.Explicit(emptyList(), listOf(Color.BLUE))))
        result.error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 18
        blue(d) shouldBe 1
        d.state.stack.size shouldBe 1
        d.bothPass().error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 19
    }

    test("APD absent payment is rejected without changing state") {
        val d = driver()
        val id = d.putCreatureOnBattlefield(d.player1, fixture.name)
        val before = d.state
        val result = d.submit(ActivateAbility(d.player1, id, fixture.activatedAbilities.single().id,
            paymentStrategy = PaymentStrategy.Explicit(emptyList())))
        result.isSuccess shouldBe false
        result.newState shouldBe before
        result.events shouldBe emptyList()
        d.state shouldBe before
        d.getLifeTotal(d.player1) shouldBe 20
        blue(d) shouldBe 0
    }
})
