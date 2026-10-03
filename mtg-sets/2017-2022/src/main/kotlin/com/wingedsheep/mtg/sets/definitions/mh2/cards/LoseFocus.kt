package com.wingedsheep.mtg.sets.definitions.mh2.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility

val LoseFocus = card("Lose Focus") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Replicate {U} (When you cast this spell, copy it for each time you paid its replicate cost. You may choose new targets for the copies.)\nCounter target spell unless its controller pays {2}."
    keywordAbility(KeywordAbility.replicate("{U}"))
    spell {
        target = Targets.Spell
        effect = Effects.CounterUnlessPays("{2}")
    }
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "49"
        artist = "Martina Fačková"
    }
}
