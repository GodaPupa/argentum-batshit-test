package com.wingedsheep.sdk.dsl

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.conditions.Compare
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.costs.CostAtom
import com.wingedsheep.sdk.scripting.effects.AddCountersEffect
import com.wingedsheep.sdk.scripting.effects.Gate
import com.wingedsheep.sdk.scripting.effects.GatedEffect
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.serialization.CardSerialization
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class AdaptDslTest : FunSpec({
    val card = card("Test Adapter") {
        manaCost = "{2}{G}"
        typeLine = "Creature — Mutant"
        power = 2
        toughness = 2
        adapt(2, "{1}{G}")
    }

    test("adapt composes a mana cost with a resolution-time no-counter gate") {
        val ability = card.activatedAbilities.single()
        val cost = ability.cost.shouldBeInstanceOf<AbilityCost.Atom>()
        cost.atom.shouldBeInstanceOf<CostAtom.Mana>().cost.toString() shouldBe "{1}{G}"

        val gated = ability.effect.shouldBeInstanceOf<GatedEffect>()
        val whenCondition = gated.gate.shouldBeInstanceOf<Gate.WhenCondition>()
        val compare = whenCondition.condition.shouldBeInstanceOf<Compare>()
        compare.operator shouldBe ComparisonOperator.LTE
        compare.right shouldBe DynamicAmount.Fixed(0)

        val add = gated.then.shouldBeInstanceOf<AddCountersEffect>()
        add.counterType shouldBe Counters.PLUS_ONE_PLUS_ONE
        add.count shouldBe 2
    }

    test("adapt card data round-trips through JSON") {
        val json = CardSerialization.json
        val encoded = json.encodeToString(com.wingedsheep.sdk.model.CardDefinition.serializer(), card)
        json.decodeFromString(com.wingedsheep.sdk.model.CardDefinition.serializer(), encoded) shouldBe card
    }

    test("adapt rejects a nonpositive counter count") {
        kotlin.runCatching {
            card("Bad Adapter") { adapt(0, "{G}") }
        }.isFailure shouldBe true
    }
})
