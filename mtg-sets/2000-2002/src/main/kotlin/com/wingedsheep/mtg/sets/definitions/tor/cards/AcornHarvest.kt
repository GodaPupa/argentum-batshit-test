package com.wingedsheep.mtg.sets.definitions.tor.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility

/**
 * Acorn Harvest
 * {3}{G}
 * Sorcery
 *
 * Create two 1/1 green Squirrel creature tokens.
 * Flashback—{1}{G}, Pay 3 life.
 */
val AcornHarvest = card("Acorn Harvest") {
    manaCost = "{3}{G}"
    colorIdentity = "G"
    typeLine = "Sorcery"
    oracleText = "Create two 1/1 green Squirrel creature tokens.\nFlashback—{1}{G}, Pay 3 life."

    spell {
        effect = Effects.CreateToken(
            power = 1,
            toughness = 1,
            colors = setOf(Color.GREEN),
            creatureTypes = setOf("Squirrel"),
            count = 2
        )
    }

    keywordAbility(
        KeywordAbility.flashback(
            "{1}{G}",
            Costs.additional.PayLife(3)
        )
    )

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "118"
        artist = "Edward P. Beard, Jr."
    }
}
