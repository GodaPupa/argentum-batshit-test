package com.wingedsheep.mtg.sets.definitions.tmp.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility

/** Capsize — Tempest #55. */
val Capsize = card("Capsize") {
    manaCost = "{1}{U}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Buyback {3} (You may pay an additional {3} as you cast this spell. " +
        "If you do, put this card into your hand as it resolves.)\n" +
        "Return target permanent to its owner's hand."

    keywordAbility(KeywordAbility.buyback("{3}"))

    spell {
        val permanent = target("target permanent", Targets.Permanent)
        effect = Effects.ReturnToHand(permanent)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "55"
        artist = "Tom Wänerstrand"
        imageUri = "https://cards.scryfall.io/normal/front/e/5/e538b359-d893-422d-9d60-5f3e8ee0fa9e.jpg?1783946658"
    }
}
