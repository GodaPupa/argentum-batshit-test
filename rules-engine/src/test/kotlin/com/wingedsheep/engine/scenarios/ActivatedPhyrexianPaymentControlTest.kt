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

/** Deliberate mutation witnesses; never registered as production cards. */
class ActivatedPhyrexianPaymentControlTest : FunSpec({
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
    test("APC life payment without blue") {
        val f = fixture(); val d = driver(f)
        val r = d.submit(action(d, f))
        r.error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 18
    }
    test("APC choice preserves blue") {
        val f = fixture(); val d = driver(f); d.giveMana(d.player1, Color.BLUE, 1)
        val r = d.submit(action(d, f))
        r.error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 18
        blue(d) shouldBe 1
    }
    test("APC invalid color rejected") {
        val f = fixture(); val d = driver(f); d.giveMana(d.player1, Color.BLUE, 1)
        val before = d.state
        val r = d.submit(action(d, f, listOf(Color.RED)))
        r.isSuccess shouldBe false
        r.newState shouldBe before; r.events shouldBe emptyList()
    }
    test("APC insufficient life rejected") {
        val f = fixture(); val d = driver(f); d.setLifeTotal(d.player1, 1)
        val before = d.state
        val r = d.submit(action(d, f))
        r.isSuccess shouldBe false
        r.newState shouldBe before; r.events shouldBe emptyList()
    }
    test("APC payment emits one exact event") {
        val f = fixture(); val d = driver(f)
        val r = d.submit(action(d, f))
        r.error shouldBe null
        val paid = r.events.filterIsInstance<LifeChangedEvent>().filter { it.reason == LifeChangeReason.PAYMENT }
        paid.size shouldBe 1
        paid.single().playerId shouldBe d.player1
        paid.single().oldLife shouldBe 20
        paid.single().newLife shouldBe 18
    }
    test("APC repeated payment charges each activation") {
        val f = fixture(); val d = driver(f)
        val r = d.submit(action(d, f, repeat = 3))
        r.error shouldBe null
        d.getLifeTotal(d.player1) shouldBe 14
        d.state.stack.size shouldBe 3
    }
    test("APC aggregate life cost rejected") {
        val f = fixture(Costs.Composite(Costs.Mana("{U/P}"), Costs.PayLife(3)))
        val d = driver(f); d.setLifeTotal(d.player1, 4)
        val before = d.state
        val r = d.submit(action(d, f))
        r.isSuccess shouldBe false
        r.newState shouldBe before; r.events shouldBe emptyList()
    }
})
