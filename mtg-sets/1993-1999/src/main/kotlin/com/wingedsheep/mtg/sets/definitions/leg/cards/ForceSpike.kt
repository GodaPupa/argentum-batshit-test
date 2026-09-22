package com.wingedsheep.mtg.sets.definitions.leg.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Force Spike
 * {U}
 * Instant
 *
 * Counter target spell unless its controller pays {1}.
 */
val ForceSpike = card("Force Spike") {
    manaCost = "{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Counter target spell unless its controller pays {1}."

    spell {
        target = Targets.Spell
        effect = Effects.CounterUnlessPays("{1}")
    }

    metadata {
        rarity = Rarity.COMMON
    }
}
