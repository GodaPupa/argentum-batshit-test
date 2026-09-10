package com.wingedsheep.sdk.dsl

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.scripting.effects.AddCountersEffect
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Add "[cost]: Adapt [count]."
 *
 * Adapt is deliberately a composition rather than a new engine primitive. The ability is always
 * legal to activate; only as it resolves does it check whether its source currently has no +1/+1
 * counters. That resolution-time distinction matters when multiple Adapt activations are stacked.
 */
fun CardBuilder.adapt(count: Int, cost: String) {
    require(count > 0) { "Adapt count must be positive" }

    activatedAbility {
        this.cost = Costs.Mana(cost)
        effect = ConditionalEffect(
            condition = Conditions.SourceCounterCountAtMost(Counters.PLUS_ONE_PLUS_ONE, 0),
            effect = AddCountersEffect(
                counterType = Counters.PLUS_ONE_PLUS_ONE,
                count = count,
                target = EffectTarget.Self,
            ),
        )
        description = "$cost: Adapt $count"
    }
}
