package com.wingedsheep.mtg.sets.definitions.mh3.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

val UnfathomableTruths = card("Unfathomable Truths") {
    manaCost = "{4}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Devoid\nDraw three cards and create a 0/1 colorless Eldrazi Spawn creature token with \"Sacrifice this token: Add {C}.\""

    keywords(Keyword.DEVOID)

    spell {
        effect = Effects.Composite(
            Effects.DrawCards(3),
            Effects.CreateEldraziSpawn(1)
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "77"
        artist = "Drew Tucker"
    }
}
