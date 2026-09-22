package com.wingedsheep.mtg.sets.definitions.znr.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.MayEffect
import com.wingedsheep.sdk.scripting.effects.SearchDestination
import com.wingedsheep.sdk.scripting.references.Player

/**
 * Cleansing Wildfire
 * {1}{R}
 * Sorcery
 *
 * Destroy target land. Its controller may search their library for a basic land card, put it
 * onto the battlefield tapped, then shuffle. Draw a card.
 */
val CleansingWildfire = card("Cleansing Wildfire") {
    manaCost = "{1}{R}"
    colorIdentity = "R"
    typeLine = "Sorcery"
    oracleText = "Destroy target land. Its controller may search their library for a basic land " +
        "card, put it onto the battlefield tapped, then shuffle.\nDraw a card."

    spell {
        val land = target("target land", Targets.Land)
        effect = Effects.Destroy(land) then
            Effects.ForEachPlayer(
                Player.ControllerOf("the targeted land"),
                listOf(
                    MayEffect(
                        Patterns.Library.searchLibrary(
                            filter = GameObjectFilter.BasicLand,
                            count = 1,
                            destination = SearchDestination.BATTLEFIELD,
                            entersTapped = true,
                            shuffleAfter = true
                        )
                    )
                )
            ) then
            Effects.DrawCards(1)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "137"
        artist = "Mathias Kollros"
        flavorText = "Every rebirth looks like a death."
    }
}
