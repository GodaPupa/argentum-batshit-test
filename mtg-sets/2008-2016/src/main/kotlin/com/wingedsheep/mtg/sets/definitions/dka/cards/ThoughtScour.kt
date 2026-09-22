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
 * The draw is not conditional on the targeted player's identity, but the entire spell still
 * follows normal targeted-spell resolution: if its sole target is illegal on resolution the spell
 * fizzles and neither the mill nor the draw occurs.
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
        flavorText = """“As you inject the viscus vitae into the brain stem, don't let the spastic moaning bother you. It will soon become music to your ears.”
—Stitcher Geralf"""
        imageUri = "https://cards.scryfall.io/normal/front/0/7/074f2822-f4d5-4d58-b04d-57bdf9c7cc57.jpg"
    }
}
