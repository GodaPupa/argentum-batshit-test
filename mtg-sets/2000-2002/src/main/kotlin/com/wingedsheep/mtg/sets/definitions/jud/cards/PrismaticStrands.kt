package com.wingedsheep.mtg.sets.definitions.jud.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility

/** Prismatic Strands — Judgment #18. */
val PrismaticStrands = card("Prismatic Strands") {
    manaCost = "{2}{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText = "Prevent all damage that sources of the color of your choice would deal this turn.\n" +
        "Flashback—Tap an untapped white creature you control. " +
        "(You may cast this card from your graveyard for its flashback cost. Then exile it.)"

    spell {
        effect = Effects.ChooseColorThen(Effects.PreventAllDamageFromChosenColor())
    }

    keywordAbility(
        KeywordAbility.flashback(
            "",
            Costs.additional.TapPermanents(1, GameObjectFilter.Creature.withColor(Color.WHITE))
        )
    )

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "18"
        artist = "Eric Peterson"
        imageUri = "https://cards.scryfall.io/normal/front/3/4/3454ef42-2e0b-4ce4-945f-e4ec3e83c39d.jpg?1562629309"
    }
}
