package com.wingedsheep.mtg.sets.definitions.ths.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

val NyleasDisciple = card("Nylea's Disciple") {
    manaCost = "{2}{G}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Centaur Archer"
    oracleText = "When this creature enters, you gain life equal to your devotion to green. (Each {G} in the mana costs of permanents you control counts toward your devotion to green.)"
    power = 3
    toughness = 3

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.GainLife(DynamicAmounts.devotionTo(Color.GREEN))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "167"
        artist = "Trevor Claxton"
        imageUri = "https://cards.scryfall.io/normal/front/3/6/365d716a-1435-48ac-b85f-2cceca1056dd.jpg?1783939742"
        ruling("2013-09-15", "If an activated ability or triggered ability has an effect that depends on your devotion to a color, you count the number of mana symbols of that color among the mana costs of permanents you control as the ability resolves. The permanent with that ability will be counted if it's still on the battlefield at that time.")
    }
}
