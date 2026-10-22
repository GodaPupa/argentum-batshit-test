package com.wingedsheep.engine.handlers

import com.wingedsheep.engine.core.CardEntityFactory
import com.wingedsheep.engine.mechanics.layers.MutableProjectedValues
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.conditions.Compare
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.conditions.NumberMatches
import com.wingedsheep.sdk.scripting.conditions.NumberProperty
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Excluded deterministic fixtures for the numeric-condition projection boundary. */
class ProjectionNumericConditionTest : FunSpec({
    val owner = EntityId.of("numeric-owner")
    val opponent = EntityId.of("numeric-opponent")
    val source = EntityId.of("numeric-source")
    val support = EntityId.of("numeric-support")
    val definition = CardDefinition.enchantment("Numeric Red Pip Fixture", ManaCost.parse("{R}"))
    val state = GameState(
        entities = mapOf(
            source to CardEntityFactory.create(definition, owner),
            support to CardEntityFactory.create(definition, owner),
        ),
        zones = mapOf(ZoneKey(owner, Zone.BATTLEFIELD) to listOf(source, support)),
    )
    val values = mapOf(
        source to MutableProjectedValues(controllerId = owner),
        support to MutableProjectedValues(controllerId = opponent),
    )
    fun projection() = ConditionEvaluationContext.Projection(source, values[source], values)
    val devotion = DynamicAmount.DevotionTo(colors = listOf(Color.RED))

    test("Compare reads a prior control layer for both operands without a fallback projection") {
        val evaluator = ConditionEvaluator(defaultProjection = { error("Intermediate projection was lost") })
        evaluator.evaluate(state, Compare(devotion, ComparisonOperator.EQ, DynamicAmount.Fixed(1)), projection()) shouldBe true
        evaluator.evaluate(state, Compare(DynamicAmount.Fixed(2), ComparisonOperator.EQ, devotion), projection()) shouldBe false
    }

    test("NumberMatches reads the same prior control layer without a fallback projection") {
        val evaluator = ConditionEvaluator(defaultProjection = { error("Intermediate projection was lost") })
        evaluator.evaluate(state, NumberMatches(devotion, NumberProperty.Odd), projection()) shouldBe true
        evaluator.evaluate(state, NumberMatches(devotion, NumberProperty.Even), projection()) shouldBe false
    }

    test("resolution of fixed numeric conditions keeps projection lazy") {
        val evaluator = ConditionEvaluator(defaultProjection = { error("Fixed amount requested projection") })
        val context = EffectContext(sourceId = source, controllerId = owner)
        evaluator.evaluate(state, Compare(DynamicAmount.Fixed(2), ComparisonOperator.EQ, DynamicAmount.Fixed(2)), context) shouldBe true
        evaluator.evaluate(state, NumberMatches(DynamicAmount.Fixed(2), NumberProperty.Even), context) shouldBe true
    }
})
