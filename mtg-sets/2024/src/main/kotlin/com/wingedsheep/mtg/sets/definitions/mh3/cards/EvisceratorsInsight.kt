package com.wingedsheep.mtg.sets.definitions.mh3.cards

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility

/**
 * Eviscerator's Insight — Modern Horizons 3 #93.
 * The additional sacrifice is a casting cost for both normal and flashback casts. The existing flashback mechanism exiles the spell when it leaves the stack.
 */
val EvisceratorsInsight = card("Eviscerator's Insight") {
    manaCost = "{1}{B}"
    colorIdentity = "B"
    typeLine = "Instant"
    oracleText = "As an additional cost to cast this spell, sacrifice an artifact or creature.\nDraw two cards.\nFlashback {4}{B} (You may cast this card from your graveyard for its flashback cost and any additional costs. Then exile it.)"

    additionalCost(Costs.additional.SacrificePermanent(GameObjectFilter.CreatureOrArtifact))

    spell {
        effect = Effects.DrawCards(2)
    }

    keywordAbility(KeywordAbility.flashback("{4}{B}"))

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "93"
        artist = "Sam White"
        flavorText = "\"We're all just the sum of our parts.\""
        imageUri = "https://cards.scryfall.io/normal/front/b/0/b00573bd-5bbb-4531-a0bb-a40f62b92b88.jpg?1783911280"
        ruling("2024-06-07", "A spell cast using flashback will always be exiled afterward, whether it resolves, is countered, or leaves the stack in some other way.")
    }
}
