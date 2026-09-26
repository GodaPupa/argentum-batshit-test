package com.wingedsheep.mtg.sets.definitions.wwk.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/**
 * Goliath Sphinx
 * {5}{U}{U}
 * Creature — Sphinx
 * 8/7
 *
 * Flying
 *
 * Canonical definition is kept in its original Worldwake home. The Pauper eligibility used by
 * Sphinx Stage E comes from its later Commander Masters common printing.
 */
val GoliathSphinx = card("Goliath Sphinx") {
    manaCost = "{5}{U}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Sphinx"
    oracleText = "Flying"
    power = 8
    toughness = 7
    keywords(Keyword.FLYING)

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "28"
        artist = "Greg Staples"
    }
}
