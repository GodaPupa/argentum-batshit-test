package com.wingedsheep.mtg.sets.definitions.mh3.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.SearchDestination

/**
 * Twisted Landscape
 * Land
 *
 * {T}: Add {C}.
 * {T}, Sacrifice this land: Search your library for a basic Swamp, Mountain, or Forest card,
 * put it onto the battlefield tapped, then shuffle.
 * Cycling {B}{R}{G}
 */
val TwistedLandscape = card("Twisted Landscape") {
    typeLine = "Land"
    colorIdentity = "BRG"
    oracleText = "{T}: Add {C}.\n" +
        "{T}, Sacrifice this land: Search your library for a basic Swamp, Mountain, or Forest " +
        "card, put it onto the battlefield tapped, then shuffle.\n" +
        "Cycling {B}{R}{G} ({B}{R}{G}, Discard this card: Draw a card.)"

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Tap, Costs.SacrificeSelf)
        effect = Patterns.Library.searchLibrary(
            filter = GameObjectFilter.BasicLand.withAnySubtype("Swamp", "Mountain", "Forest"),
            count = 1,
            destination = SearchDestination.BATTLEFIELD,
            entersTapped = true,
            reveal = true,
            shuffleAfter = true
        )
        description = "Search your library for a basic Swamp, Mountain, or Forest card, put it " +
            "onto the battlefield tapped, then shuffle."
    }

    keywordAbility(KeywordAbility.cycling("{B}{R}{G}"))

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "232"
        artist = "Piotr Dura"
    }
}
