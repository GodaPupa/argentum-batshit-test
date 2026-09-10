package com.wingedsheep.sdk.dsl

import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.conditions.Compare
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.Aggregation
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * Unit tests for the "different kinds of counters among a group" DSL surface —
 * the DISTINCT_COUNTER_TYPES aggregation and Conditions.DifferentCounterKindsAtLeast(n).
 */
class DifferentCounterKindsConditionTest : DescribeSpec({

    describe("AggregateBattlefield with DISTINCT_COUNTER_TYPES") {
        it("renders an Oracle-style description") {
            DynamicAmount.AggregateBattlefield(
                Player.You,
                GameObjectFilter.Creature,
                Aggregation.DISTINCT_COUNTER_TYPES
            ).description shouldBe
                "the number of different kinds of counters among creatures you control"
        }
    }

    describe("Conditions.DifferentCounterKindsAtLeast") {
        it("defaults to creatures you control and compares against a fixed threshold with GTE") {
            val condition = Conditions.DifferentCounterKindsAtLeast(3)
            condition.shouldBeInstanceOf<Compare>()
            condition.left shouldBe DynamicAmount.AggregateBattlefield(
                Player.You,
                GameObjectFilter.Creature,
                Aggregation.DISTINCT_COUNTER_TYPES
            )
            condition.operator shouldBe ComparisonOperator.GTE
            condition.right shouldBe DynamicAmount.Fixed(3)
        }

        it("is parameterized over the group filter") {
            val condition = Conditions.DifferentCounterKindsAtLeast(2, GameObjectFilter.Any)
            condition.shouldBeInstanceOf<Compare>()
            val left = condition.left
            left.shouldBeInstanceOf<DynamicAmount.AggregateBattlefield>()
            left.filter shouldBe GameObjectFilter.Any
            left.aggregation shouldBe Aggregation.DISTINCT_COUNTER_TYPES
        }
    }
})
