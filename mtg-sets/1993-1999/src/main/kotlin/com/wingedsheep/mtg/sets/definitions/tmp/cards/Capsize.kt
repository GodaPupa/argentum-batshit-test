package com.wingedsheep.mtg.sets.definitions.tmp.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/**
 * Capsize
 * {1}{U}{U}
 * Instant
 *
 * Buyback {3}
 * Return target permanent to its owner's hand.
 */
val Capsize = card("Capsize") {
    manaCost = "{1}{U}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Buyback {3} (You may pay an additional {3} as you cast this spell. If you do, put this card into your hand as it resolves.)\n" +
        "Return target permanent to its owner's hand."

    keywordAbility(KeywordAbility.buyback("{3}"))

    spell {
        val permanent = target("target permanent", TargetPermanent())
        effect = Effects.ReturnToHand(permanent)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "55"
        artist = "Tom Wänerstrand"
    }
}
