package com.wingedsheep.mtg.sets.definitions.lea.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AdditionalManaOnTap
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Exact frozen Manual Transmission v0.7 identity; uses existing shared primitives. */
val WildGrowth = card("Wild Growth") {
    manaCost = "{G}"
    colorIdentity = "G"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant land\nWhenever enchanted land is tapped for mana, its controller adds an additional {G}."

    auraTarget = Targets.Land

    staticAbility {
        ability = AdditionalManaOnTap(color = Color.GREEN, amount = DynamicAmount.Fixed(1))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "229"
        artist = "Mark Poole"
        imageUri = "https://cards.scryfall.io/normal/front/f/d/fd896dfa-66c0-4327-8e5b-489bbe350c95.jpg?1783948670"
        ruling("2004-10-04", "The additional mana is not an ability of the land and is not something the land can produce.")
    }
}
