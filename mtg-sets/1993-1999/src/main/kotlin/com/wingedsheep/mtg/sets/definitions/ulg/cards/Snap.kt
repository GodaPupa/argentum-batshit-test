package com.wingedsheep.mtg.sets.definitions.ulg.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/** Snap — Urza's Legacy #43. */
val Snap = card("Snap") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Return target creature to its owner's hand. Untap up to two lands."

    spell {
        val creature = target("target creature", Targets.Creature)
        val (firstLand, secondLand) = targets(
            "land",
            TargetPermanent(count = 2, optional = true, filter = TargetFilter.Land),
        )
        effect = Effects.Move(creature, Zone.HAND)
            .then(Effects.Untap(firstLand))
            .then(Effects.Untap(secondLand))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "43"
        artist = "Mike Raabe"
        flavorText = "Good riddance."
        imageUri = "https://cards.scryfall.io/normal/front/f/7/f7e0549e-2d23-4ea8-b8d1-ae21af2c9091.jpg?1783946245"
    }
}
