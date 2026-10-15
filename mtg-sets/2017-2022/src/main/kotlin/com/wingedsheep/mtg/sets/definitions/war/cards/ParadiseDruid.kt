package com.wingedsheep.mtg.sets.definitions.war.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.scripting.ConditionalStaticAbility
import com.wingedsheep.sdk.scripting.GrantKeyword

/** Exact frozen Manual Transmission v0.7 identity; composes existing engine primitives. */
val ParadiseDruid = card("Paradise Druid") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elf Druid"
    oracleText = "This creature has hexproof as long as it's untapped. (It can't be the target of spells or abilities your opponents control.)\n{T}: Add one mana of any color."
    power = 2
    toughness = 1

    staticAbility {
        ability = ConditionalStaticAbility(
            ability = GrantKeyword(Keyword.HEXPROOF, Filters.Self),
            condition = Conditions.SourceIsUntapped
        )
    }

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddManaOfChoice()
        manaAbility = true
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "171"
        artist = "Nils Hamm"
        flavorText = "\"There are many kinds of duty, and mine is to see our world grow and endure.\""
        imageUri = "https://cards.scryfall.io/normal/front/6/e/6ed8d9e7-cdad-450d-8329-fa653d387a63.jpg?1783933409"
    }
}
