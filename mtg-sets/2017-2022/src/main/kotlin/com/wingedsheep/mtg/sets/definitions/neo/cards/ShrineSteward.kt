package com.wingedsheep.mtg.sets.definitions.neo.cards

import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.SearchDestination

/**
 * Shrine Steward — Kamigawa: Neon Dynasty #259
 * {5}
 * Artifact Creature — Construct
 * 3/2
 *
 * When this creature enters, you may search your library for an Aura or Shrine card,
 * reveal it, put it into your hand, then shuffle.
 *
 * Reuses the generic optional library-search pipeline and the existing OR-subtype filter.
 */
val ShrineSteward = card("Shrine Steward") {
    manaCost = "{5}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Construct"
    power = 3
    toughness = 2
    oracleText =
        "When this creature enters, you may search your library for an Aura or Shrine card, " +
            "reveal it, put it into your hand, then shuffle."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        optional = true
        effect = Patterns.Library.searchLibrary(
            filter = GameObjectFilter.Any.withAnySubtype("Aura", "Shrine"),
            destination = SearchDestination.HAND,
            reveal = true
        )
        description =
            "When this creature enters, you may search your library for an Aura or Shrine card, " +
                "reveal it, put it into your hand, then shuffle."
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "259"
        artist = "kleinerHai"
        flavorText = "From broken swords, the first Imperials forged a peaceful servant to tend the shrines of Eiganjo."
    }
}
