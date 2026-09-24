package com.wingedsheep.mtg.sets.definitions.mh1.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Generous Gift — Modern Horizons #11
 * {2}{W}
 * Instant
 *
 * Destroy target permanent. Its controller creates a 3/3 green Elephant creature token.
 */
val GenerousGift = card("Generous Gift") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText = "Destroy target permanent. Its controller creates a 3/3 green Elephant creature token."

    spell {
        val permanent = target("permanent", Targets.Permanent)
        effect = Effects.Composite(
            listOf(
                Effects.Destroy(permanent),
                Effects.CreateToken(
                    power = 3,
                    toughness = 3,
                    colors = setOf(Color.GREEN),
                    creatureTypes = setOf("Elephant"),
                    controller = EffectTarget.TargetController,
                )
            )
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "11"
        artist = "Kev Walker"
    }
}
