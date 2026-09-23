package com.wingedsheep.mtg.sets.definitions.som.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CounterEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetSpell

/**
 * Turn Aside
 * {U}
 * Instant
 *
 * Counter target spell that targets a permanent you control.
 */
val TurnAside = card("Turn Aside") {
    manaCost = "{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Counter target spell that targets a permanent you control."

    spell {
        val spell = target(
            "target spell that targets a permanent you control",
            TargetSpell(
                filter = TargetFilter(
                    baseFilter = GameObjectFilter.Any.targetsMatching(
                        GameObjectFilter.Permanent.youControl()
                    ),
                    zone = Zone.STACK
                )
            )
        )
        effect = CounterEffect()
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "49"
        artist = "Shelly Wan"
    }
}
