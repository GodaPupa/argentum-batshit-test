package com.wingedsheep.mtg.sets.definitions.clb.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Blur
 * {2}{U}
 * Instant
 *
 * Exile target creature you control, then return that card to the battlefield under its owner's
 * control. Draw a card.
 */
val Blur = card("Blur") {
    manaCost = "{2}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Exile target creature you control, then return that card to the battlefield " +
        "under its owner's control.\nDraw a card."

    spell {
        val creature = target("target creature you control", Targets.CreatureYouControl)
        effect = Effects.Exile(creature)
            .then(Effects.Move(creature, Zone.BATTLEFIELD))
            .then(Effects.DrawCards(1))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "58"
        artist = "Dave Greco"
        flavorText = "\"It's as much a trick of the mind as it is a trick of the eye.\""
        imageUri = "https://cards.scryfall.io/normal/front/7/7/77a43413-3ab0-4ef6-83de-192a11d48f00.jpg?1783922795"
    }
}
