package com.wingedsheep.sdk.dsl

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.ActivatedAbility
import com.wingedsheep.sdk.scripting.costs.CostAtom
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Add a mana-activated Adapt ability (CR 701.46).
 *
 * Adapt checks for +1/+1 counters when the ability resolves, not when it is activated. The helper
 * therefore composes an ordinary mana cost with a [ConditionalEffect] that reads the source live
 * and places counters through the shared counter-placement effect. A permanent that gains a
 * +1/+1 counter in response still pays the cost, but the resolving Adapt ability does nothing.
 */
fun CardBuilder.adapt(count: Int, cost: String) {
    require(count > 0) { "Adapt count must be positive" }
    val parsedCost = ManaCost.parse(cost)
    activatedAbilities.add(
        ActivatedAbility(
            cost = AbilityCost.Atom(CostAtom.Mana(parsedCost)),
            effect = ConditionalEffect(
                condition = Conditions.SourceCounterCountAtMost(Counters.PLUS_ONE_PLUS_ONE, 0),
                effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, count, EffectTarget.Self),
            ),
            descriptionOverride = "$cost: Adapt $count.",
        )
    )
}
