package com.wingedsheep.mtg.sets.definitions.mh2.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.targets.TargetSpell

/**
 * Lose Focus
 * {1}{U}
 * Instant
 *
 * Replicate {U}
 * Counter target spell unless its controller pays {2}.
 */
val LoseFocus = card("Lose Focus") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Replicate {U} (When you cast this spell, copy it for each time you paid its replicate cost. You may choose new targets for the copies.)\n" +
        "Counter target spell unless its controller pays {2}."

    keywordAbility(KeywordAbility.replicate("{U}"))

    spell {
        target("target", TargetSpell())
        effect = Effects.CounterUnlessPays("{2}")
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "49"
    }
}
