package com.wingedsheep.mtg.sets.definitions.som.cards

import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect

/** Galvanic Blast — Scars of Mirrodin #91. */
val GalvanicBlast = card("Galvanic Blast") {
    manaCost = "{R}"
    colorIdentity = "R"
    typeLine = "Instant"
    oracleText = "Galvanic Blast deals 2 damage to any target.\n" +
        "Metalcraft — Galvanic Blast deals 4 damage instead if you control three or more artifacts."
    spell {
        val victim = target("any target", Targets.Any)
        effect = ConditionalEffect(
            condition = Conditions.YouControlAtLeast(3, GameObjectFilter.Artifact),
            effect = Effects.DealDamage(4, victim),
            elseEffect = Effects.DealDamage(2, victim),
        )
    }
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "91"
        artist = "Marc Simonetti"
        flavorText = "Mirrodin has little weather, but it certainly has lightning."
        imageUri = "https://cards.scryfall.io/normal/front/f/5/f5881bbc-8600-464d-9dcd-5a7780918d1d.jpg?1783941725"
    }
}
