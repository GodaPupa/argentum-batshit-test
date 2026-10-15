package com.wingedsheep.mtg.sets.definitions.j25.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility

/** Exact Oracle and printing data: https://scryfall.com/card/j25/28/shardless-outlander */
val ShardlessOutlander = card("Shardless Outlander") {
    manaCost = "{7}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Construct Scout"
    oracleText = "Trample (This creature can deal excess combat damage to the player or planeswalker it's attacking.)\nBasic landcycling {2} ({2}, Discard this card: Search your library for a basic land card, reveal it, put it into your hand, then shuffle.)"
    power = 6
    toughness = 5

    keywords(Keyword.TRAMPLE)
    keywordAbility(KeywordAbility.basicLandcycling("{2}"))

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "28"
        artist = "Leon Tukker"
        flavorText = "The Conflux proved that Alara's strength came from unity, not division."
        imageUri = "https://cards.scryfall.io/normal/front/f/c/fccb51a4-cb78-4437-b9ab-cc77736af561.jpg?1783908862"
    }
}
