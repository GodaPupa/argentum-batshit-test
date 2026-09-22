package com.wingedsheep.mtg.sets.definitions.tor.cards

import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.AdditionalCost
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.costs.CostAtom
import com.wingedsheep.sdk.scripting.effects.DrawCardsEffect

/**
 * Deep Analysis
 * {3}{U}
 * Sorcery
 *
 * Target player draws two cards.
 * Flashback—{1}{U}, Pay 3 life.
 */
val DeepAnalysis = card("Deep Analysis") {
    manaCost = "{3}{U}"
    colorIdentity = "U"
    typeLine = "Sorcery"
    oracleText = "Target player draws two cards.\nFlashback—{1}{U}, Pay 3 life."

    spell {
        val player = target("target player", Targets.Player)
        effect = DrawCardsEffect(2, player)
    }

    keywordAbility(
        KeywordAbility.flashback(
            "{1}{U}",
            AdditionalCost.Atom(CostAtom.PayLife(3))
        )
    )

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "36"
    }
}
