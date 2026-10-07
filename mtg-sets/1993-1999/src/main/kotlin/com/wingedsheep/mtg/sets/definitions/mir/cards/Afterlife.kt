package com.wingedsheep.mtg.sets.definitions.mir.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/**
 * Afterlife — Mirage #1
 * {2}{W}
 * Instant
 *
 * Destroy target creature. It can't be regenerated. Its controller creates a
 * 1/1 white Spirit creature token with flying.
 *
 * This composes the existing no-regeneration destroy rail with the already-qualified
 * target-controller token rail; no Afterlife-specific executor is required.
 */
val Afterlife = card("Afterlife") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText =
        "Destroy target creature. It can't be regenerated. Its controller creates a " +
            "1/1 white Spirit creature token with flying."

    spell {
        val creature = target("creature", Targets.Creature)
        effect = Effects.Composite(
            listOf(
                Effects.Destroy(creature, noRegenerate = true),
                Effects.CreateToken(
                    power = 1,
                    toughness = 1,
                    colors = setOf(Color.WHITE),
                    creatureTypes = setOf("Spirit"),
                    keywords = setOf(Keyword.FLYING),
                    controller = EffectTarget.TargetController,
                ),
            )
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "1"
        artist = "Pete Venters"
        flavorText = "Facets of life, reflections of the soul."
    }
}
