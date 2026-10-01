package com.wingedsheep.mtg.sets.definitions.ths.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetSpell

/**
 * Swan Song
 * {U}
 * Instant
 * Counter target enchantment, instant, or sorcery spell.
 * Its controller creates a 2/2 blue Bird creature token with flying.
 */
val SwanSong = card("Swan Song") {
    manaCost = "{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Counter target enchantment, instant, or sorcery spell. Its controller creates a 2/2 blue Bird creature token with flying."

    spell {
        target(
            "target enchantment, instant, or sorcery spell",
            TargetSpell(
                filter = TargetFilter(
                    GameObjectFilter.InstantOrSorcery or GameObjectFilter.Enchantment,
                    zone = Zone.STACK,
                )
            )
        )
        // Create the token before moving the target off the stack so TargetController remains
        // available; both instructions still resolve for a legal target even if it can't be countered.
        effect = Effects.CreateToken(
            power = 2,
            toughness = 2,
            colors = setOf(Color.BLUE),
            creatureTypes = setOf("Bird"),
            keywords = setOf(Keyword.FLYING),
            controller = EffectTarget.TargetController,
        ).then(Effects.CounterSpell())
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "65"
        artist = "Peter Mohrbacher"
    }
}
