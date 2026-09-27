package com.wingedsheep.mtg.sets.definitions.mir.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.CostModification
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.SpellCostTarget
import com.wingedsheep.sdk.scripting.effects.DealDamageEffect
import com.wingedsheep.sdk.scripting.filters.unified.GroupFilter
import com.wingedsheep.sdk.scripting.targets.AnyTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Kaervek's Torch — Mirage #185
 * {X}{R}
 * Sorcery
 *
 * As long as Kaervek's Torch is on the stack, spells that target it cost {2} more to cast.
 * Kaervek's Torch deals X damage to any target.
 */
val KaerveksTorch = card("Kaervek's Torch") {
    manaCost = "{X}{R}"
    colorIdentity = "R"
    typeLine = "Sorcery"
    oracleText = "As long as Kaervek's Torch is on the stack, spells that target it cost {2} more to cast.\n" +
        "Kaervek's Torch deals X damage to any target."

    staticAbility {
        ability = ModifySpellCost(
            target = SpellCostTarget.AnyCasterTargeting(GroupFilter.source()),
            modification = CostModification.IncreaseGeneric(2),
            sourceZones = setOf(Zone.STACK),
        )
    }

    spell {
        val t = target("any target", AnyTarget())
        effect = DealDamageEffect(DynamicAmount.XValue, t)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "185"
        artist = "John Coulthart"
        flavorText = "The pulsing heat of the midday Sun burns in the Lion's eye.\n—Stone inscription, source unknown"
    }
}
