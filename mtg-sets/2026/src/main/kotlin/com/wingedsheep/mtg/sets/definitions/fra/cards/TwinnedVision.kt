package com.wingedsheep.mtg.sets.definitions.fra.cards

import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect

/**
 * Twinned Vision — Reality Fracture #157
 * {1}{U/R}
 * Instant
 *
 * Draw a card. If this spell wasn't cast from your hand, draw two cards instead.
 * Flashback—{1}{U/R}{U/R}, Discard a card.
 *
 * Spell copies have no cast-from-hand origin, so the copied object takes the two-card branch.
 * A flashback cast originates in the graveyard and likewise takes the two-card branch.
 */
val TwinnedVision = card("Twinned Vision") {
    manaCost = "{1}{U/R}"
    colorIdentity = "UR"
    typeLine = "Instant"
    oracleText =
        "Draw a card. If this spell wasn't cast from your hand, draw two cards instead.\n" +
            "Flashback—{1}{U/R}{U/R}, Discard a card. " +
            "(You may cast this card from your graveyard for its flashback cost. Then exile it.)"

    spell {
        effect = ConditionalEffect(
            condition = Conditions.WasCastFromHand,
            effect = Effects.DrawCards(1),
            elseEffect = Effects.DrawCards(2)
        )
    }

    keywordAbility(
        KeywordAbility.flashback(
            "{1}{U/R}{U/R}",
            Costs.additional.DiscardCards()
        )
    )

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "157"
        artist = "Andreas Zafiratos"
        flavorText = "\"She will rage and burn until her fuel is expended. It's almost sad to see me like this.\""
    }
}
