package com.wingedsheep.mtg.sets.definitions.hml.cards

import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CounterDestination
import com.wingedsheep.sdk.scripting.effects.CounterEffect

/**
 * Memory Lapse — Homelands #32a
 * {1}{U} · Instant
 *
 * Counter target spell. If that spell is countered this way, put it on top of its
 * owner's library instead of into that player's graveyard.
 */
val MemoryLapse = card("Memory Lapse") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Counter target spell. If that spell is countered this way, put it on top of its owner's library instead of into that player's graveyard."

    spell {
        target("target spell", Targets.Spell)
        effect = CounterEffect(counterDestination = CounterDestination.LibraryTop)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "32a"
        artist = "Mark Tedin"
        flavorText = "\\\"Um . . . oh . . . what was I saying?\\\"\\n—Reveka, Wizard Savant"
    }
}
