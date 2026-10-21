package com.wingedsheep.mtg.sets.definitions.plc.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.scripting.targets.EffectTarget

val Pongify = card("Pongify") {
    manaCost = "{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Destroy target creature. It can't be regenerated. Its controller creates a 3/3 green Ape creature token."

    spell {
        val creature = target("creature", Targets.Creature)
        effect = Effects.Composite(
            Effects.Destroy(creature, noRegenerate = true),
            Effects.CreateToken(
                power = 3,
                toughness = 3,
                colors = setOf(Color.GREEN),
                creatureTypes = setOf("Ape"),
                controller = EffectTarget.TargetController
            )
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "44"
        artist = "Heather Hudson"
        flavorText = "Some spellcrafting mistakes go on to become spells of their own."
        imageUri = "https://cards.scryfall.io/normal/front/c/c/cce74a84-4441-4f2e-89d8-df0b096790ed.jpg?1783943162"
        ruling("2021-03-19", "If the target creature is an illegal target by the time Pongify tries to resolve, the spell won't resolve. No player creates an Ape token. If the target is legal but not destroyed (most likely because it has indestructible), its controller does create an Ape token.")
    }
}
