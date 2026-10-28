package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.ReplacementEffectSourceComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.*
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.*
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.ReplaceLifePaymentWithLibraryExile
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/** Bounded payment behavior through the real ActionProcessor; unregistered test fixtures. */
class ActivatedPhyrexianPaymentBoundaryTest : FunSpec({
    fun fixture(costValue: AbilityCost = Costs.Mana("{U/P}"), targeted: Boolean = false) = card("Manual Payment Boundary Fixture") {
        manaCost = "{2}"
        typeLine = "Artifact Creature — Construct"
        power = 0
        toughness = 4
        activatedAbility {
            cost = costValue
            if (targeted) target("creature", Targets.Creature)
            effect = Effects.GainLife(1)
        }
    }
    fun driver(f: CardDefinition) = GameTestDriver().also {
        it.registerCards(TestCards.all + listOf(f))
        it.initMirrorMatch(Deck.of("Island" to 40))
        it.passPriorityUntil(Step.PRECOMBAT_MAIN)
        it.putCreatureOnBattlefield(it.player1, f.name)
    }
    fun action(d: GameTestDriver, f: CardDefinition, choices: List<Color> = listOf(Color.BLUE), repeat: Int = 1): ActivateAbility {
        val id = d.state.getBattlefield().single()
        return ActivateAbility(d.player1, id, f.activatedAbilities.single().id,
            paymentStrategy = PaymentStrategy.Explicit(emptyList(), choices), repeatCount = repeat)
    }
    fun blue(d: GameTestDriver) = d.state.getEntity(d.player1)?.get<ManaPoolComponent>()?.blue ?: 0
    fun unchanged(d: GameTestDriver, a: ActivateAbility) {
        val before = d.state
        val r = d.submit(a)
        r.isSuccess shouldBe false
        r.newState shouldBe before
        r.events shouldBe emptyList()
        d.state shouldBe before
    }
    for ((label, cost, choices) in listOf(
        Triple("wrong-color", Costs.Mana("{U/P}"), listOf(Color.RED)),
        Triple("extra-pip", Costs.Mana("{U/P}"), listOf(Color.BLUE, Color.BLUE)),
        Triple("non-phyrexian", Costs.Mana("{U}"), listOf(Color.BLUE)),
        Triple("free", Costs.Free, listOf(Color.BLUE))
    )) test("APR rejects invalid selection $label") {
        val f = fixture(cost); val d = driver(f); d.giveMana(d.player1, Color.BLUE, 2)
        unchanged(d, action(d, f, choices))
    }
    for (life in listOf(-1, 0, 1)) test("APR insufficient life $life") {
        val f = fixture(); val d = driver(f); d.setLifeTotal(d.player1, life)
        unchanged(d, action(d, f))
    }
    test("APR exactly two life is payable before state based loss") {
        val f = fixture(); val d = driver(f); d.setLifeTotal(d.player1, 2)
        val r = d.submit(action(d, f)); r.error shouldBe null
        val e = r.events.filterIsInstance<LifeChangedEvent>().single { it.reason == LifeChangeReason.PAYMENT }
        e.oldLife shouldBe 2; e.newLife shouldBe 0
        d.getLifeTotal(d.player1) shouldBe 0
    }
    test("APR mixed generic colored and multiple Phyrexian pips") {
        val f = fixture(Costs.Mana("{2}{U}{U/P}{B/P}")); val d = driver(f)
        d.giveMana(d.player1, Color.GREEN, 2); d.giveMana(d.player1, Color.BLUE, 1)
        val r = d.submit(action(d, f, listOf(Color.BLUE, Color.BLACK)))
        r.error shouldBe null; d.getLifeTotal(d.player1) shouldBe 16
        blue(d) shouldBe 0
        d.state.getEntity(d.player1)!!.get<ManaPoolComponent>()!!.green shouldBe 0
        d.state.stack.size shouldBe 1
    }
    test("APR repeated same color pips are a multiset") {
        val f = fixture(Costs.Mana("{U/P}{U/P}")); val d = driver(f)
        val r = d.submit(action(d, f, listOf(Color.BLUE, Color.BLUE)))
        r.error shouldBe null; d.getLifeTotal(d.player1) shouldBe 16
        r.events.filterIsInstance<LifeChangedEvent>().single().newLife shouldBe 16
    }
    test("APR unselected Phyrexian pip still costs mana") {
        val f = fixture(Costs.Mana("{U/P}{B/P}")); val d = driver(f)
        d.giveMana(d.player1, Color.BLACK, 1)
        val r = d.submit(action(d, f)); r.error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 18
        d.state.getEntity(d.player1)!!.get<ManaPoolComponent>()!!.black shouldBe 0
    }
    test("APR insufficient remaining mana rolls back all state and events") {
        val f = fixture(Costs.Mana("{2}{U/P}")); val d = driver(f)
        d.giveMana(d.player1, Color.GREEN, 1)
        unchanged(d, action(d, f))
    }
    test("APR nested life costs cannot overdraw") {
        val f = fixture(Costs.Composite(Costs.Mana("{U/P}"), Costs.Composite(Costs.PayLife(1), Costs.PayLife(2))))
        val d = driver(f); d.setLifeTotal(d.player1, 4)
        unchanged(d, action(d, f))
    }
    test("APR chosen X life is included in affordability") {
        val f = fixture(Costs.Composite(Costs.Mana("{U/P}"), Costs.PayXLife))
        val d = driver(f); d.setLifeTotal(d.player1, 4)
        unchanged(d, action(d, f).copy(xValue = 3))
    }
    test("APR composite life payment charges both costs exactly once") {
        val f = fixture(Costs.Composite(Costs.Mana("{U/P}"), Costs.PayLife(3)))
        val d = driver(f)
        val r = d.submit(action(d, f)); r.error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 15
        r.events.filterIsInstance<LifeChangedEvent>().map { it.oldLife to it.newLife } shouldBe listOf(20 to 18, 18 to 15)
    }
    test("APR invalid target preserves payment routing and stack") {
        val f = fixture(targeted = true); val d = driver(f)
        unchanged(d, action(d, f).copy(targets = listOf(ChosenTarget.Permanent(EntityId.generate()))))
    }
    test("APR unavailable counter cost preserves all state and events") {
        val f = fixture(Costs.Composite(Costs.Mana("{U/P}"), Costs.RemoveCounters()))
        val d = driver(f)
        unchanged(d, action(d, f))
    }
    for ((label, strategy) in listOf("auto" to PaymentStrategy.AutoPay, "pool" to PaymentStrategy.FromPool)) {
        test("APR default $label spends blue without life") {
            val f = fixture(); val d = driver(f); d.giveMana(d.player1, Color.BLUE, 1)
            val r = d.submit(action(d, f).copy(paymentStrategy = strategy)); r.error shouldBe null
            d.getLifeTotal(d.player1) shouldBe 20; blue(d) shouldBe 0
            r.events.filterIsInstance<LifeChangedEvent>() shouldBe emptyList()
        }
        test("APR default $label does not silently choose life") {
            val f = fixture(); val d = driver(f)
            unchanged(d, action(d, f).copy(paymentStrategy = strategy))
        }
    }
    test("APR separate activations each charge once and resolve once") {
        val f = fixture(); val d = driver(f)
        repeat(2) {
            val r = d.submit(action(d, f)); r.error shouldBe null
            r.events.filterIsInstance<LifeChangedEvent>().size shouldBe 1
            d.bothPass().error shouldBe null
        }
        d.getLifeTotal(d.player1) shouldBe 18; d.state.stack.size shouldBe 0
    }
    test("APR repeated activations stop before unaffordable payment") {
        val f = fixture(); val d = driver(f); d.setLifeTotal(d.player1, 5)
        val r = d.submit(action(d, f, repeat = 3)); r.error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 1; d.state.stack.size shouldBe 2
        r.events.filterIsInstance<LifeChangedEvent>().map { it.newLife } shouldBe listOf(3, 1)
    }
    test("APR failed repeat restores newly tapped mana source") {
        val f = fixture(Costs.Mana("{1}{U/P}")); val d = driver(f); d.setLifeTotal(d.player1, 3)
        val a = action(d, f, repeat = 2)
        d.giveMana(d.player1, Color.GREEN, 1)
        val island = d.putLandOnBattlefield(d.player1, "Island")
        val r = d.submit(a); r.error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 1; d.state.stack.size shouldBe 1
        d.state.getEntity(island)!!.has<com.wingedsheep.engine.state.components.battlefield.TappedComponent>() shouldBe false
        r.events.filterIsInstance<LifeChangedEvent>().size shouldBe 1
    }
    for (shallow in listOf(false, true)) test("APR life replacement shallow=$shallow") {
        val f = fixture(); val d = driver(f); val a = action(d, f)
        val source = a.sourceId
        var s = d.state.updateEntity(source) { it.with(ReplacementEffectSourceComponent(listOf(ReplaceLifePaymentWithLibraryExile()))) }
        val key = ZoneKey(d.player1, Zone.LIBRARY)
        if (shallow) s = s.copy(zones = s.zones + (key to s.getZone(key).take(1)))
        d.replaceState(s)
        val originalLibrary = d.state.getZone(key)
        val r = d.submit(a); r.error shouldBe null
        d.getLifeTotal(d.player1) shouldBe if (shallow) 18 else 20
        if (shallow) {
            r.events.filterIsInstance<LifeChangedEvent>().single().reason shouldBe LifeChangeReason.PAYMENT
            d.state.getZone(key) shouldBe originalLibrary
        } else {
            d.state.getZone(key) shouldBe originalLibrary.drop(2)
            d.state.getZone(ZoneKey(d.player1, Zone.EXILE)) shouldBe originalLibrary.take(2)
            r.events.filterIsInstance<LifeChangedEvent>() shouldBe emptyList()
            r.events.filterIsInstance<ZoneChangeEvent>().count { it.toZone == Zone.EXILE } shouldBe 2
        }
    }
    test("APR replacement does not authorize unpayable life") {
        val f = fixture(); val d = driver(f); val a = action(d, f)
        d.replaceState(d.state.updateEntity(a.sourceId) { it.with(ReplacementEffectSourceComponent(listOf(ReplaceLifePaymentWithLibraryExile()))) })
        d.setLifeTotal(d.player1, 1)
        unchanged(d, a)
    }
    test("APR action serialization preserves explicit pip multiplicity") {
        val f = fixture(Costs.Mana("{U/P}{U/P}")); val d = driver(f)
        val a = action(d, f, listOf(Color.BLUE, Color.BLUE))
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val decoded = json.decodeFromString(GameAction.serializer(), json.encodeToString(GameAction.serializer(), a))
        decoded shouldBe a
        val r = d.submit(decoded); r.error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 16
    }
    test("APR repeated X life costs are paid on every activation") {
        val f = fixture(Costs.Composite(Costs.Mana("{U/P}"), Costs.PayXLife)); val d = driver(f)
        val r = d.submit(action(d, f, repeat = 2).copy(xValue = 3)); r.error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 10; d.state.stack.size shouldBe 2
        r.events.filterIsInstance<LifeChangedEvent>().map { it.newLife } shouldBe listOf(18, 15, 13, 10)
    }
    for (mode in listOf("valid", "invalid-color", "insufficient-life")) test("APR direct resumed execution $mode") {
        val f = fixture(); val d = driver(f)
        val services = EngineServices(d.cardRegistry)
        val handler = com.wingedsheep.engine.handlers.actions.ability.ActivateAbilityHandler(
            d.cardRegistry, services.turnManager, services.costHandler, services.manaSolver,
            services.alternativePaymentHandler, services.effectExecutorRegistry, services.stackResolver,
            services.targetValidator, services.conditionEvaluator, services.triggerDetector,
            services.triggerProcessor, services.castPermissionUtils, services.manaAbilitySideEffectExecutor,
            services.sbaChecker)
        if (mode == "insufficient-life") d.setLifeTotal(d.player1, 1)
        val a = action(d, f, listOf(if (mode == "invalid-color") Color.RED else Color.BLUE))
        val before = d.state
        val r = handler.execute(before, a)
        if (mode == "valid") {
            r.error shouldBe null; r.state.lifeTotal(d.player1) shouldBe 18
            r.state.stack.size shouldBe 1
        } else {
            r.isSuccess shouldBe false; r.state shouldBe before; r.events shouldBe emptyList()
        }
    }
})
