package com.wingedsheep.mtg.sets.definitions.nph.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.targets.EffectTarget

/** Vapor Snag — New Phyrexia #48. */
val VaporSnag = card("Vapor Snag") {
    manaCost = "{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Return target creature to its owner's hand. Its controller loses 1 life."

    spell {
        val creature = target("target creature", Targets.Creature)
        effect = Effects.ReturnToHand(creature)
            .then(Effects.LoseLife(1, EffectTarget.TargetController))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "48"
        artist = "Raymond Swanland"
        flavorText = "\"This creature is inadequate. Send it to the splicers for innovation.\"\n—Malcator, Executor of Synthesis"
        imageUri = "https://cards.scryfall.io/normal/front/7/0/70305148-23bd-41dd-9de5-13cf5ae591ae.jpg?1783941317"
    }
}
