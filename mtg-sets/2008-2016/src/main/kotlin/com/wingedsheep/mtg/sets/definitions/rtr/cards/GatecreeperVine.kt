package com.wingedsheep.mtg.sets.definitions.rtr.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.SearchDestination

/**
 * Gatecreeper Vine
 * {1}{G}
 * Creature — Plant
 * 0/2
 * Defender
 * When this creature enters, you may search your library for a basic land card or Gate card,
 * reveal it, put it into your hand, then shuffle.
 */
val GatecreeperVine = card("Gatecreeper Vine") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Plant"
    oracleText = "Defender\nWhen this creature enters, you may search your library for a basic land card or Gate card, reveal it, put it into your hand, then shuffle."
    power = 0
    toughness = 2

    keywords(Keyword.DEFENDER)

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        optional = true
        effect = Patterns.Library.searchLibrary(
            filter = GameObjectFilter.BasicLand or GameObjectFilter.Any.withSubtype("Gate"),
            destination = SearchDestination.HAND,
            reveal = true,
        )
    }

    metadata {
        rarity = Rarity.COMMON
    }
}
