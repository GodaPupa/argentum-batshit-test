package com.wingedsheep.mtg.sets.definitions.dst.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.dsl.namedFromVariable
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource

/**
 * Echoing Truth
 * {1}{U}
 * Instant
 *
 * Return target nonland permanent and all other permanents with the same name as that permanent
 * to their owners' hands.
 */
val EchoingTruth = card("Echoing Truth") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Return target nonland permanent and all other permanents with the same name as that permanent to their owners' hands."

    spell {
        target("target nonland permanent", Targets.NonlandPermanent)
        effect = Effects.Pipeline {
            val chosen = gather(CardSource.ChosenTargets, name = "target")
            val chosenName = storeCardName(chosen, name = "name")
            val sameNamed = gather(
                GameObjectFilter.Any.namedFromVariable(chosenName),
                name = "sameNamed"
            )
            toHand(sameNamed)
        }
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "21"
        artist = "Greg Staples"
        flavorText = "A single lie unleashes a tide of disbelief."
    }
}
