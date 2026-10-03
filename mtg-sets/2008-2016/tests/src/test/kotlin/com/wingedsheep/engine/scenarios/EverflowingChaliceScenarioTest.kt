package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseNumberDecision
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.NumberChosenResponse
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.mtg.sets.definitions.wwk.cards.EverflowingChalice
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.CostModification
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.SpellCostTarget
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class EverflowingChaliceScenarioTest : FunSpec({
    val reducer = card("Chalice Test Reducer") {
        manaCost = "{0}"
        typeLine = "Enchantment"
        staticAbility { ability = ModifySpellCost(
            target = SpellCostTarget.YouCast(GameObjectFilter.Artifact),
            modification = CostModification.ReduceGeneric(1)
        ) }
    }
    fun game(): GameTestDriver = GameTestDriver().apply {
        registerCards(TestCards.all)
        registerCard(EverflowingChalice)
        initMirrorMatch(Deck.of("Forest" to 40))
        passPriorityUntil(Step.PRECOMBAT_MAIN)
    }
    fun GameTestDriver.mana(): Int = state.getEntity(activePlayer!!)?.get<ManaPoolComponent>()?.colorless ?: 0
    fun GameTestDriver.charge(id: EntityId): Int = state.getEntity(id)?.get<CountersComponent>()?.getCount(CounterType.CHARGE) ?: 0
    fun GameTestDriver.cast(count: Int, mana: Int = count * 2): EntityId {
        val player = activePlayer!!
        giveColorlessMana(player, mana)
        val id = putCardInHand(player, EverflowingChalice.name)
        submit(CastSpell(player, id, declaredCostSlot = ChoiceSlot.KICKED.takeIf { count > 0 },
            optionalCostCounts = mapOf(ChoiceSlot.KICKED to count),
            paymentStrategy = PaymentStrategy.FromPool)).error shouldBe null
        return id
    }
    fun GameTestDriver.tap(id: EntityId) {
        submit(ActivateAbility(activePlayer!!, id, EverflowingChalice.script.activatedAbilities.single().id)).error shouldBe null
    }

    test("zero kicks enters without charge counters and taps for zero") {
        val game = game()
        val id = game.cast(0)
        game.bothPass()
        game.charge(id) shouldBe 0
        game.tap(id)
        game.mana() shouldBe 0
        game.state.getEntity(id)?.has<TappedComponent>() shouldBe true
    }

    test("one kick pays two and enters with one counter producing one mana") {
        val game = game()
        val id = game.cast(1)
        game.mana() shouldBe 0
        game.bothPass()
        game.charge(id) shouldBe 1
        game.tap(id)
        game.mana() shouldBe 1
    }

    test("three kicks pay six and produce three charge counters") {
        val game = game()
        val id = game.cast(3)
        game.mana() shouldBe 0
        game.bothPass()
        game.charge(id) shouldBe 3
        game.tap(id)
        game.mana() shouldBe 3
    }

    test("repetition choice precedes payment and may select multiple kicks") {
        val game = game()
        val player = game.activePlayer!!
        game.giveColorlessMana(player, 8)
        val id = game.putCardInHand(player, EverflowingChalice.name)
        game.submit(CastSpell(player, id, declaredCostSlot = ChoiceSlot.KICKED,
            paymentStrategy = PaymentStrategy.FromPool)).error shouldBe null
        game.mana() shouldBe 8
        val question = game.pendingDecision.shouldBeInstanceOf<ChooseNumberDecision>()
        game.submitDecision(player, NumberChosenResponse(question.id, 4)).error shouldBe null
        game.mana() shouldBe 0
        game.bothPass()
        game.charge(id) shouldBe 4
    }

    test("mana ability reads current charge counters and does not consume them") {
        val game = game()
        val id = game.cast(1)
        game.bothPass()
        game.replaceState(game.state.updateEntity(id) { it.with(CountersComponent(mapOf(CounterType.CHARGE to 5))) })
        game.tap(id)
        game.mana() shouldBe 5
        game.charge(id) shouldBe 5
        game.untapPermanent(id)
        game.tap(id)
        game.mana() shouldBe 10
        game.charge(id) shouldBe 5
    }

    test("copy of the spell retains announced kicker multiplicity") {
        val game = game()
        val original = game.cast(3)
        val copied = EngineServices(game.cardRegistry).stackResolver.putSpellCopy(game.state, original)
        copied.error shouldBe null
        game.replaceState(copied.newState)
        val copy = game.state.stack.last()
        game.bothPass()
        game.charge(copy) shouldBe 3
        game.bothPass()
        game.charge(original) shouldBe 3
    }

    test("returning to hand then recasting without kicker loses old cast choices") {
        val game = game()
        val id = game.cast(3)
        game.bothPass()
        val moved = ZoneTransitionService.moveToZone(game.state, id, Zone.HAND)
        game.replaceState(moved.state)
        game.submit(CastSpell(game.activePlayer!!, id, paymentStrategy = PaymentStrategy.FromPool)).error shouldBe null
        game.bothPass()
        game.charge(id) shouldBe 0
    }

    test("ordinary battlefield placement does not invent a paid kicker") {
        val game = game()
        val id = game.putPermanentOnBattlefield(game.activePlayer!!, EverflowingChalice.name)
        game.charge(id) shouldBe 0
        game.tap(id)
        game.mana() shouldBe 0
    }
    test("one generic reduction applies to the combined cost rather than each kick") {
        val game = game()
        game.registerCard(reducer)
        game.putPermanentOnBattlefield(game.activePlayer!!, reducer.name)
        val id = game.cast(3, mana = 5)
        game.mana() shouldBe 0
        game.bothPass()
        game.charge(id) shouldBe 3
    }

    test("the mana solver can use a charged Chalice to pay a later multikicker cast") {
        val game = game()
        val first = game.cast(3)
        game.bothPass()
        val player = game.activePlayer!!
        val second = game.putCardInHand(player, EverflowingChalice.name)
        game.submit(CastSpell(player, second, declaredCostSlot = ChoiceSlot.KICKED,
            optionalCostCounts = mapOf(ChoiceSlot.KICKED to 1), paymentStrategy = PaymentStrategy.AutoPay)).error shouldBe null
        game.state.getEntity(first)?.has<TappedComponent>() shouldBe true
        game.mana() shouldBe 1
        game.bothPass()
        game.charge(second) shouldBe 1
    }

})
