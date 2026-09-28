package com.wingedsheep.mtg.sets.definitions.nec.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.CreatePredefinedTokenEffect

/**
 * Shorikai, Genesis Engine
 * {2}{W}{U} · Legendary Artifact — Vehicle · 8/8
 *
 * {1}, {T}: Draw two cards, then discard a card. Create a 1/1 colorless Pilot creature token
 * with "This token crews Vehicles as though its power were 2 greater."
 * Crew 8
 */
val ShorikaiGenesisEngine = card("Shorikai, Genesis Engine") {
    manaCost = "{2}{W}{U}"
    typeLine = "Legendary Artifact — Vehicle"
    oracleText = "{1}, {T}: Draw two cards, then discard a card. Create a 1/1 colorless Pilot creature token with \"This token crews Vehicles as though its power were 2 greater.\"\nCrew 8"
    power = 8
    toughness = 8

    keywordAbility(KeywordAbility.Numeric(Keyword.CREW, 8))

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap)
        effect = Effects.Composite(
            Effects.DrawCards(2),
            Effects.Discard(1),
            CreatePredefinedTokenEffect("Pilot", 1)
        )
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "4"
        artist = "Wisnu Tan"
    }
}
