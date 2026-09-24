package com.wingedsheep.mtg.sets.definitions.m15.cards

import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.SearchDestination

/**
 * Heliod's Pilgrim — Magic 2015 #14
 * {2}{W}
 * Creature — Human Cleric
 * 1/2
 *
 * When this creature enters, you may search your library for an Aura card, reveal it,
 * put it into your hand, then shuffle.
 *
 * This deliberately reuses the same generic optional Aura-tutor recipe already used by
 * Totem-Guide Hartebeest; no card-specific search executor is required.
 */
val HeliodsPilgrim = card("Heliod's Pilgrim") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Creature — Human Cleric"
    power = 1
    toughness = 2
    oracleText =
        "When this creature enters, you may search your library for an Aura card, reveal it, " +
            "put it into your hand, then shuffle."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        optional = true
        effect = Patterns.Library.searchLibrary(
            filter = GameObjectFilter.Any.withSubtype(Subtype.AURA),
            destination = SearchDestination.HAND,
            reveal = true
        )
        description =
            "When this creature enters, you may search your library for an Aura card, reveal it, " +
                "put it into your hand, then shuffle."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "14"
        artist = "Izzy"
        flavorText = "The blessings of Heliod are apparent for all to see."
    }
}
