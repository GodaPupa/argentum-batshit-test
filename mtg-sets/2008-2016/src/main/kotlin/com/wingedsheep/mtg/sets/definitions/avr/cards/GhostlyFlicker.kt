package com.wingedsheep.mtg.sets.definitions.avr.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/** Ghostly Flicker — blink exactly two artifacts, creatures, and/or lands you control. */
val GhostlyFlicker = card("Ghostly Flicker") {
    manaCost = "{2}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Exile two target artifacts, creatures, and/or lands you control, then return those cards to the battlefield under your control."

    spell {
        val permanentFilter = GameObjectFilter.Artifact
            .or(GameObjectFilter.Creature)
            .or(GameObjectFilter.Land)
            .youControl()
        val (first, second) = targets(
            "artifact, creature, and/or land you control",
            TargetPermanent(count = 2, filter = TargetFilter(permanentFilter)),
        )
        effect = Effects.Move(first, Zone.EXILE)
            .then(Effects.Move(second, Zone.EXILE))
            .then(Effects.Move(first, Zone.BATTLEFIELD))
            .then(Effects.Move(second, Zone.BATTLEFIELD))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "57"
        artist = "Raymond Swanland"
        flavorText = "Sometimes it's useful to take a break from existence."
        imageUri = "https://cards.scryfall.io/normal/front/f/0/f0a44373-0c50-4e14-a7c6-0de66796b81e.jpg?1783940717"
    }
}
