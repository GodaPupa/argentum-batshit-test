package com.wingedsheep.mtg.sets.definitions.frf.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.ActivationRestriction
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.AddManaEffect

/**
 * Whisperer of the Wilds
 * {1}{G}
 * Creature — Human Shaman
 * 0/2
 * {T}: Add {G}.
 * Ferocious — {T}: Add {G}{G}. Activate only if you control a creature with power 4 or greater.
 */
val WhispererOfTheWilds = card("Whisperer of the Wilds") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Human Shaman"
    oracleText = "{T}: Add {G}.\nFerocious — {T}: Add {G}{G}. Activate only if you control a creature with power 4 or greater."
    power = 0
    toughness = 2

    activatedAbility {
        cost = AbilityCost.Tap
        effect = AddManaEffect(Color.GREEN)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = AbilityCost.Tap
        effect = AddManaEffect(Color.GREEN, 2)
        manaAbility = true
        timing = TimingRule.ManaAbility
        restrictions = listOf(
            ActivationRestriction.OnlyIfCondition(
                Conditions.YouControl(GameObjectFilter.Creature.powerAtLeast(4))
            )
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "144"
        artist = "David Gaillet"
        flavorText = "She speaks only in whispers but can be heard over the fiercest storm."
    }
}
