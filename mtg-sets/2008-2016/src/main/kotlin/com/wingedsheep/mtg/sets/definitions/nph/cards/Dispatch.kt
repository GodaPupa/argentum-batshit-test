package com.wingedsheep.mtg.sets.definitions.nph.cards

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect

/**
 * Dispatch — New Phyrexia #7.
 * Tapping happens before the resolution-time metalcraft check. Metalcraft is not a casting or targeting restriction, and no player receives priority between those instructions.
 */
val Dispatch = card("Dispatch") {
    manaCost = "{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText = "Tap target creature.\nMetalcraft — If you control three or more artifacts, exile that creature."

    spell {
        val victim = target("target creature", Targets.Creature)
        effect = Effects.Composite(
            Effects.Tap(victim),
            ConditionalEffect(
                condition = Conditions.YouControlAtLeast(3, GameObjectFilter.Artifact),
                effect = Effects.Exile(victim)
            )
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "7"
        artist = "Erica Yang"
        flavorText = "Venser wondered if it could still be called a teleportation spell if the destination is oblivion."
        imageUri = "https://cards.scryfall.io/normal/front/4/9/496634f9-1271-4be7-bad5-364bb87a6962.jpg?1783941327"
        ruling("2011-06-01", "If you control three or more artifacts when Dispatch resolves, you'll tap the creature, then exile it.")
    }
}
