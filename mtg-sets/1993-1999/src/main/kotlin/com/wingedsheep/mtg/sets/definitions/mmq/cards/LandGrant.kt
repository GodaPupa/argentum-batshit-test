package com.wingedsheep.mtg.sets.definitions.mmq.cards

import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.SelfAlternativeCost

/**
 * Land Grant
 * {1}{G}
 * Sorcery
 *
 * If you have no land cards in hand, you may reveal your hand rather than pay this spell's mana cost.
 * Search your library for a Forest card, reveal that card, put it into your hand, then shuffle.
 */
val LandGrant = card("Land Grant") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Sorcery"
    oracleText = "If you have no land cards in hand, you may reveal your hand rather than pay this spell's mana cost.\n" +
        "Search your library for a Forest card, reveal that card, put it into your hand, then shuffle."

    selfAlternativeCost = SelfAlternativeCost(
        manaCost = ManaCost.parse("{0}"),
        additionalCosts = listOf(Costs.additional.RevealHand),
        condition = Conditions.NoLandCardsInHand,
    )

    spell {
        effect = Patterns.Library.searchLibrary(
            filter = GameObjectFilter.Any.withSubtype(Subtype.FOREST),
            count = 1,
            reveal = true,
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "255"
    }
}
