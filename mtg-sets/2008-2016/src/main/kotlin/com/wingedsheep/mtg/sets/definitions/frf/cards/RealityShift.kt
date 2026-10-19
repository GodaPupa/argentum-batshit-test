package com.wingedsheep.mtg.sets.definitions.frf.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.TargetObject

/**
 * Reality Shift — Fate Reforged #46
 * {1}{U}
 * Instant
 *
 * Exile target creature. Its controller manifests the top card of their library.
 */
val RealityShift = card("Reality Shift") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Exile target creature. Its controller manifests the top card of their library."

    spell {
        val creature = target(
            "target creature",
            TargetObject(filter = TargetFilter.Creature)
        )
        effect = Effects.ForEachPlayer(
            Player.ControllerOf("target creature"),
            listOf(
                Effects.Exile(creature),
                Patterns.Library.manifest()
            )
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "46"
        artist = "Howard Lyon"
    }
}
