package com.wingedsheep.mtg.sets.definitions.m20.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/** Cloudkin Seer — Core Set 2020 #54. */
val CloudkinSeer = card("Cloudkin Seer") {
    manaCost = "{2}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Elemental Wizard"
    oracleText = "Flying\nWhen this creature enters, draw a card."
    power = 2
    toughness = 1

    keywords(Keyword.FLYING)

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.DrawCards(1)
        description = "When this creature enters, draw a card."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "54"
        artist = "Anastasia Ovchinnikova"
        flavorText = "\"I can see which way the wind is blowing.\""
        imageUri = "https://cards.scryfall.io/normal/front/e/2/e2111753-a930-403f-9d94-a86dfcb069da.jpg?1783933012"
    }
}
