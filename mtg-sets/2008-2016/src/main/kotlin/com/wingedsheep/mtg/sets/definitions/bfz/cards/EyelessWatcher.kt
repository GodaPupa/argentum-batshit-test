package com.wingedsheep.mtg.sets.definitions.bfz.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.core.Keyword

/** Exact frozen Manual Transmission v0.7 identity; uses the shared Eldrazi token definition. */
val EyelessWatcher = card("Eyeless Watcher") {
    manaCost = "{3}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Eldrazi Drone"
    oracleText = "Devoid (This card has no color.)\nWhen this creature enters, create two 1/1 colorless Eldrazi Scion creature tokens. They have \"Sacrifice this token: Add {C}.\""
    power = 1
    toughness = 1

    keywords(Keyword.DEVOID)

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.CreateEldraziScion(2)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "166"
        artist = "Yohann Schepacz"
        flavorText = "Every Eldrazi in Ulamog's lineage is an extension of the titan's will."
        imageUri = "https://cards.scryfall.io/normal/front/4/d/4d949d0e-baf7-4573-bb15-7e30e3e9b202.jpg?1783938189"
        ruling("2015-08-25", "Cards with devoid use frames that are variations of the transparent frame traditionally used for Eldrazi. The top part of the card features some color over a background based on the texture of the hedrons that once imprisoned the Eldrazi. This coloration is intended to aid deckbuilding and game play.")
        ruling("2015-08-25", "A card with devoid is just colorless. It's not colorless and the colors of mana in its mana cost.")
        ruling("2015-08-25", "Other cards and abilities can give a card with devoid color. If that happens, it's just the new color, not that color and colorless.")
        ruling("2015-08-25", "Devoid works in all zones, not just on the battlefield.")
        ruling("2015-08-25", "If a card loses devoid, it will still be colorless. This is because effects that change an object's color (like the one created by devoid) are considered before the object loses devoid.")
        ruling("2015-08-25", "Eldrazi Scions are similar to Eldrazi Spawn, seen in the Zendikar block. Note that Eldrazi Scions are 1/1, not 0/1.")
        ruling("2015-08-25", "Eldrazi and Scion are each separate creature types. Anything that affects Eldrazi will affect these tokens, for example.")
        ruling("2015-08-25", "Sacrificing an Eldrazi Scion creature token to add {C} is a mana ability. It doesn't use the stack and can't be responded to.")
        ruling("2015-08-25", "Some instants and sorceries that create Eldrazi Scions require targets. If all targets for such a spell have become illegal by the time that spell tries to resolve, the spell won't resolve and none of its effects will happen. You won't get any Eldrazi Scions.")
    }
}
