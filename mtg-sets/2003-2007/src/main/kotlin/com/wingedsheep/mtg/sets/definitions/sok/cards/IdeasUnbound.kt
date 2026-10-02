package com.wingedsheep.mtg.sets.definitions.sok.cards

import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CreateDelayedTriggerEffect

/**
 * Ideas Unbound
 * {U}{U}
 * Sorcery — Arcane
 *
 * Draw three cards. Discard three cards at the beginning of the next end step.
 */
val IdeasUnbound = card("Ideas Unbound") {
    manaCost = "{U}{U}"
    colorIdentity = "U"
    typeLine = "Sorcery — Arcane"
    oracleText = "Draw three cards. Discard three cards at the beginning of the next end step."

    spell {
        effect = Effects.Composite(
            listOf(
                Effects.DrawCards(3),
                CreateDelayedTriggerEffect(
                    step = Step.END,
                    effect = Effects.Discard(3)
                )
            )
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "40"
        artist = "Mark Tedin"
        flavorText = "The apprentice stared in puzzlement. \"But Master, you finished writing those spells just yesterday. Don't you remember?\" The jushi's heart froze."
    }
}
