package com.wingedsheep.mtg.sets.definitions.m21.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule

/** Llanowar Visionary — Core Set 2021 #193. */
val LlanowarVisionary = card("Llanowar Visionary") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elf Druid"
    oracleText = "When this creature enters, draw a card.\n{T}: Add {G}."
    power = 2
    toughness = 2

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.DrawCards(1)
        description = "When this creature enters, draw a card."
    }

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.GREEN)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "193"
        artist = "Cristi Balanescu"
        flavorText = "The elves of Llanowar look to their past to determine the shape of their future."
        imageUri = "https://cards.scryfall.io/normal/front/d/6/d6e23afa-7e08-4049-baf0-d4d0134ba2c8.jpg?1783930671"
    }
}
