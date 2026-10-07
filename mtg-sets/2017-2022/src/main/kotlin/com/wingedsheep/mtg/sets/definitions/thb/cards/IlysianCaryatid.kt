package com.wingedsheep.mtg.sets.definitions.thb.cards

import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect

/**
 * Ilysian Caryatid
 * {1}{G}
 * Creature — Plant
 * 1/1
 *
 * {T}: Add one mana of any color. If you control a creature with power 4 or greater,
 * add two mana of any one color instead.
 */
val IlysianCaryatid = card("Ilysian Caryatid") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Plant"
    power = 1
    toughness = 1
    oracleText = "{T}: Add one mana of any color. If you control a creature with power 4 or greater, add two mana of any one color instead."

    activatedAbility {
        cost = Costs.Tap
        effect = ConditionalEffect(
            condition = Conditions.YouControl(GameObjectFilter.Creature.powerAtLeast(4)),
            effect = Effects.AddAnyColorMana(2),
            elseEffect = Effects.AddAnyColorMana(1),
        )
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "174"
        artist = "Winona Nelson"
        flavorText = "Those who die as heroes are permitted to dwell in Ilysia, a protected realm of the Underworld as tranquil and vibrant as the rest is bleak."
    }
}
