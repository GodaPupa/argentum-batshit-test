package com.wingedsheep.mtg.sets.definitions.dka.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.DrawCardsEffect

/**
 * Thought Scour
 * {U}
 * Instant
 * Target player mills two cards.
 * Draw a card.
 */
val ThoughtScour = card("Thought Scour") {
    manaCost = "{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Target player mills two cards.\nDraw a card."

    spell {
        val player = target("target player", Targets.Player)
        effect = Effects.Composite(
            Patterns.Library.mill(2, player),
            DrawCardsEffect(1),
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "52"
    }
}
