package com.wingedsheep.mtg.sets.definitions.aer.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersWithDynamicCounters
import com.wingedsheep.sdk.scripting.targets.AnyTarget
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Walking Ballista
 * {X}{X}
 * Artifact Creature — Construct
 * 0/0
 *
 * This creature enters with X +1/+1 counters on it.
 * {4}: Put a +1/+1 counter on this creature.
 * Remove a +1/+1 counter from this creature: It deals 1 damage to any target.
 */
val WalkingBallista = card("Walking Ballista") {
    manaCost = "{X}{X}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Construct"
    power = 0
    toughness = 0
    oracleText = "This creature enters with X +1/+1 counters on it.\n" +
        "{4}: Put a +1/+1 counter on this creature.\n" +
        "Remove a +1/+1 counter from this creature: It deals 1 damage to any target."

    replacementEffect(EntersWithDynamicCounters(count = DynamicAmount.CastX))

    activatedAbility {
        cost = Costs.Mana("{4}")
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
        description = "{4}: Put a +1/+1 counter on this creature."
    }

    activatedAbility {
        cost = Costs.RemoveCounterFromSelf(Counters.PLUS_ONE_PLUS_ONE, 1)
        val t = target("any target", AnyTarget())
        effect = Effects.DealDamage(1, t)
        description = "Remove a +1/+1 counter from this creature: It deals 1 damage to any target."
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "181"
        artist = "Daniel Ljunggren"
        ruling("2020-08-07", "A casting cost of {X}{X} means that you pay twice X. If you want X to be 3, you pay {6} to cast Walking Ballista.")
        ruling("2020-08-07", "If Walking Ballista has been dealt damage or had its toughness reduced by an effect, this limits how many times you'll be able to remove +1/+1 counters from it in a single turn.")
    }
}
