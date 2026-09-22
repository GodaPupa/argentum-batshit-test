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
 *
 * The mill target and the draw recipient are intentionally different axes: the chosen player is
 * passed only to the mill pipeline, while DrawCardsEffect uses the spell controller. If the target
 * becomes illegal, normal all-targets-illegal resolution rules counter the spell before either
 * instruction occurs.
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
        artist = "David Rapoza"
        flavorText = "\"As you inject the viscus vitae into the brain stem, don't let the spastic moaning bother you. It will soon become music to your ears.\"\n—Stitcher Geralf"
        imageUri = "https://cards.scryfall.io/normal/front/2/7/2763047b-2857-42ef-84b6-53842245a7fd.jpg"
    }
}
