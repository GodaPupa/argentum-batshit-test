package com.wingedsheep.mtg.sets.definitions.grn.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

val MurmuringMystic = card("Murmuring Mystic") {
    manaCost = "{3}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Human Wizard"
    oracleText = "Whenever you cast an instant or sorcery spell, create a 1/1 blue Bird Illusion creature token with flying."
    power = 1
    toughness = 5

    triggeredAbility {
        trigger = Triggers.YouCastInstantOrSorcery
        effect = Effects.CreateToken(
            power = 1,
            toughness = 1,
            colors = setOf(Color.BLUE),
            creatureTypes = setOf("Bird", "Illusion"),
            keywords = setOf(Keyword.FLYING),
            imageUri = "https://cards.scryfall.io/normal/front/0/3/03c3dc23-d6f2-4209-82e1-a0b936c94231.jpg?1783934064",
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "45"
        artist = "Mark Winters"
        flavorText = "Rumors float through the city like crows, alighting on citizens seemingly at random."
        imageUri = "https://cards.scryfall.io/normal/front/5/f/5fc6adff-dcb3-456d-a8c2-0e77b784ff89.jpg?1783934187"
        ruling("2018-10-05", "Murmuring Mystic's triggered ability resolves before the spell that caused it to trigger. It resolves even if that spell is countered.")
    }
}
