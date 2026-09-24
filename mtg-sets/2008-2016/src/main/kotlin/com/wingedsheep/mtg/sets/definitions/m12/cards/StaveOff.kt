package com.wingedsheep.mtg.sets.definitions.m12.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Stave Off
 * {W}
 * Instant
 *
 * Target creature gains protection from the color of your choice until end of turn.
 */
val StaveOff = card("Stave Off") {
    manaCost = "{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText = "Target creature gains protection from the color of your choice until end of turn."

    spell {
        val creature = target("target creature", Targets.Creature)
        effect = Effects.ChooseColorThen(
            Effects.GrantProtectionFromChosenColor(creature)
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "36"
        artist = "Mark Zug"
    }
}
