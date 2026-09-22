package com.wingedsheep.mtg.sets.definitions.grn.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Murmuring Mystic
 * {3}{U}
 * Creature — Human Wizard
 * 1/5
 *
 * Whenever you cast an instant or sorcery spell, create a 1/1 blue Bird Illusion creature token
 * with flying.
 */
val MurmuringMystic = card("Murmuring Mystic") {
    manaCost = "{3}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Human Wizard"
    power = 1
    toughness = 5
    oracleText = "Whenever you cast an instant or sorcery spell, create a 1/1 blue Bird Illusion creature token with flying."

    triggeredAbility {
        trigger = Triggers.YouCastInstantOrSorcery
        effect = Effects.CreateToken(
            power = 1,
            toughness = 1,
            colors = setOf(Color.BLUE),
            creatureTypes = setOf("Bird", "Illusion"),
            keywords = setOf(Keyword.FLYING),
        )
        description = "Whenever you cast an instant or sorcery spell, create a 1/1 blue Bird Illusion creature token with flying."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "45"
        artist = "Mark Winters"
        flavorText = "Rumors float through the city like crows, alighting on citizens seemingly at random."
        ruling(
            "2018-10-05",
            "The triggered ability resolves before the spell that caused it to trigger and resolves even if that spell is countered."
        )
    }
}
