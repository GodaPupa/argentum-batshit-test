package com.wingedsheep.mtg.sets.definitions.m20.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardOrder
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Exact Oracle and printing data: https://scryfall.com/card/m20/166/brightwood-tracker */
val BrightwoodTracker = card("Brightwood Tracker") {
    manaCost = "{3}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elf Scout"
    oracleText = "{5}{G}, {T}: Look at the top four cards of your library. You may reveal a creature card from among them and put it into your hand. Put the rest on the bottom of your library in a random order."
    power = 2
    toughness = 4

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{5}{G}"), Costs.Tap)
        effect = Patterns.Library.lookAtTopRevealMatchingToHand(
            count = DynamicAmount.Fixed(4),
            filter = GameObjectFilter.Creature,
            prompt = "You may reveal a creature card and put it into your hand",
            restOrder = CardOrder.Random
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "166"
        artist = "Johannes Voss"
        flavorText = "\"Many have walked here, but there is only one trail I seek.\""
        imageUri = "https://cards.scryfall.io/normal/front/e/1/e1fb9767-29bf-4a69-b37c-0925d41f6b46.jpg?1783932968"
    }
}
