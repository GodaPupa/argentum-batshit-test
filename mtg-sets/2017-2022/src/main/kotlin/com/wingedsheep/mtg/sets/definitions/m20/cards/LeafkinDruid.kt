package com.wingedsheep.mtg.sets.definitions.m20.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect

/**
 * Leafkin Druid
 * {1}{G}
 * Creature — Elemental Druid
 * 0/3
 *
 * {T}: Add {G}. If you control four or more creatures, add {G}{G} instead.
 */
val LeafkinDruid = card("Leafkin Druid") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elemental Druid"
    power = 0
    toughness = 3
    oracleText = "{T}: Add {G}. If you control four or more creatures, add {G}{G} instead."

    activatedAbility {
        cost = Costs.Tap
        effect = ConditionalEffect(
            condition = Conditions.ControlCreaturesAtLeast(4),
            effect = Effects.AddMana(Color.GREEN, 2),
            elseEffect = Effects.AddMana(Color.GREEN),
        )
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "178"
    }
}
