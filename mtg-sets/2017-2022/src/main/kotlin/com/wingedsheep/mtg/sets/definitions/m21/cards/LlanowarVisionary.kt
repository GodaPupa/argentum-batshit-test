package com.wingedsheep.mtg.sets.definitions.m21.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AbilityCost
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.AddManaEffect
import com.wingedsheep.sdk.scripting.effects.DrawCardsEffect

/**
 * Llanowar Visionary
 * {2}{G}
 * Creature — Elf Druid
 * 2/2
 * When this creature enters, draw a card.
 * {T}: Add {G}.
 */
val LlanowarVisionary = card("Llanowar Visionary") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elf Druid"
    oracleText = "When this creature enters, draw a card.\n{T}: Add {G}."
    power = 2
    toughness = 2

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = DrawCardsEffect(1)
    }

    activatedAbility {
        cost = AbilityCost.Tap
        effect = AddManaEffect(Color.GREEN)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "193"
        artist = "Cristi Balanescu"
        flavorText = "The elves of Llanowar look to their past to determine the shape of their future."
    }
}
