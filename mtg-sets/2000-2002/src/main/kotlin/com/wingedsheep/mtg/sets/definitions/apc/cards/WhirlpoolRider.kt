package com.wingedsheep.mtg.sets.definitions.apc.cards

import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.references.Player

/** Whirlpool Rider — Apocalypse #35. */
val WhirlpoolRider = card("Whirlpool Rider") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Merfolk"
    power = 1
    toughness = 1
    oracleText = "When this creature enters, shuffle the cards from your hand into your library, " +
        "then draw that many cards."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Patterns.Hand.wheelEffect(Player.You)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "35"
        artist = "Ray Lago"
        flavorText = "Where the wind meets the water, change is inevitable."
        imageUri = "https://cards.scryfall.io/normal/front/0/d/0de47f44-8c5e-4114-9064-145d2d8813c6.jpg?1783945350"
    }
}
