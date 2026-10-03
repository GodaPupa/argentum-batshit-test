package com.wingedsheep.engine.mechanics.mana

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.scripting.AbilityCost
import io.kotest.matchers.shouldBe

class PhyrexianAbilityPaymentTest : ScenarioTestBase() {
    private fun witness(name: String, mana: String, extraLife: Int = 0) = card(name) {
        manaCost = "{0}"; typeLine = "Artifact"
        activatedAbility {
            cost = if (extraLife == 0) Costs.Mana(mana) else AbilityCost.Composite(listOf(Costs.Mana(mana), Costs.PayLife(extraLife)))
            effect = Effects.GainLife(1)
        }
    }
    private val single = witness("One Phyrexian activation", "{U/P}")
    private val pair = witness("Two Phyrexian activation", "{U/P}{U/P}")
    private val colored = witness("Ordinary colored activation", "{U}")
    private val extra = witness("Combined life activation", "{U/P}", 3)
    init {
        listOf(single, pair, colored, extra).forEach(cardRegistry::register)
        test("elected life branch pays exactly once and leaves floating blue unspent") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, single.name).build()
            g.state = g.state.updateEntity(g.player1Id) { it.with(ManaPoolComponent(blue = 1)) }
            val result = g.execute(ActivateAbility(g.player1Id, g.findPermanent(single.name)!!, single.activatedAbilities.single().id,
                paymentStrategy = PaymentStrategy.Explicit(emptyList(), listOf(Color.BLUE))))
            result.error shouldBe null
            g.state.lifeTotal(g.player1Id) shouldBe 18
            g.state.getEntity(g.player1Id)!!.get<ManaPoolComponent>()!!.blue shouldBe 1
            result.events.filterIsInstance<LifeChangedEvent>().filter { it.reason == LifeChangeReason.PAYMENT }.size shouldBe 1
            g.state.stack.size shouldBe 1
        }
        test("two pips permit mixed mana and life election") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, pair.name).build()
            g.state = g.state.updateEntity(g.player1Id) { it.with(ManaPoolComponent(blue = 1)) }
            g.execute(ActivateAbility(g.player1Id, g.findPermanent(pair.name)!!, pair.activatedAbilities.single().id,
                paymentStrategy = PaymentStrategy.Explicit(emptyList(), listOf(Color.BLUE)))).error shouldBe null
            g.state.lifeTotal(g.player1Id) shouldBe 18
            g.state.getEntity(g.player1Id)!!.get<ManaPoolComponent>()!!.blue shouldBe 0
        }
        for (kind in listOf("insufficient life", "wrong color", "too many pips", "ordinary colored", "combined life")) {
            test("$kind election fails without paying or stacking") {
                val def = when(kind) { "ordinary colored" -> colored; "combined life" -> extra; else -> single }
                val g = scenario().withPlayers().withCardOnBattlefield(1, def.name).build()
                val life = when(kind) { "insufficient life" -> 1; "combined life" -> 4; else -> 20 }
                g.state = g.state.updateEntity(g.player1Id) { it.with(LifeTotalComponent(life)).with(ManaPoolComponent(blue = 1)) }
                val before = g.state
                val elected = when(kind) { "wrong color" -> listOf(Color.RED); "too many pips" -> listOf(Color.BLUE, Color.BLUE); else -> listOf(Color.BLUE) }
                val result = g.execute(ActivateAbility(g.player1Id, g.findPermanent(def.name)!!, def.activatedAbilities.single().id,
                    paymentStrategy = PaymentStrategy.Explicit(emptyList(), elected)))
                (result.error != null) shouldBe true
                g.state shouldBe before
            }
        }
        test("repeat activations pay each elected life cost and stop before overpaying") {
            val g = scenario().withPlayers().withCardOnBattlefield(1, single.name).build()
            g.state = g.state.updateEntity(g.player1Id) { it.with(LifeTotalComponent(5)) }
            val result = g.execute(ActivateAbility(g.player1Id, g.findPermanent(single.name)!!, single.activatedAbilities.single().id,
                repeatCount = 3, paymentStrategy = PaymentStrategy.Explicit(emptyList(), listOf(Color.BLUE))))
            result.error shouldBe null
            g.state.lifeTotal(g.player1Id) shouldBe 1
            g.state.stack.size shouldBe 2
            result.events.filterIsInstance<LifeChangedEvent>().filter { it.reason == LifeChangeReason.PAYMENT }.size shouldBe 2
        }
    }
}
