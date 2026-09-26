package com.wingedsheep.mtg.sets.definitions.fra.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.MayEffect
import com.wingedsheep.sdk.scripting.effects.MoveSourceAndExactCardsEffect
import com.wingedsheep.sdk.scripting.effects.SearchDestination
import com.wingedsheep.sdk.scripting.effects.SuccessCriterion

/**
 * Sphinx's Approach — Reality Fracture #41 (common).
 *
 * Draw two cards. Then you may exile this spell and four cards named Sphinx's Approach from your
 * graveyard. If you do, search your library for a Sphinx creature card, put it onto the battlefield,
 * then shuffle.
 *
 * A deck can have any number of cards named Sphinx's Approach.
 */
val SphinxsApproach = card("Sphinx's Approach") {
    manaCost = "{1}{U}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText =
        "Draw two cards. Then you may exile this spell and four cards named Sphinx's Approach from your graveyard. " +
            "If you do, search your library for a Sphinx creature card, put it onto the battlefield, then shuffle.\n" +
            "A deck can have any number of cards named Sphinx's Approach."

    spell {
        val atomicExile = MoveSourceAndExactCardsEffect(
            sourceRequiredZone = Zone.STACK,
            additionalSourceZone = Zone.GRAVEYARD,
            additionalFilter = GameObjectFilter.Any.named("Sphinx's Approach"),
            additionalCount = 4,
            destination = Zone.EXILE,
            storeMovedAs = "approachExiled",
        )

        val searchForSphinx = Patterns.Library.searchLibrary(
            filter = GameObjectFilter.Creature.withSubtype("Sphinx"),
            count = 1,
            destination = SearchDestination.BATTLEFIELD,
            shuffleAfter = true,
            reveal = false,
        )

        effect = Effects.Composite(
            Effects.DrawCards(2),
            MayEffect(
                effect = Effects.IfYouDo(
                    action = atomicExile,
                    ifYouDo = searchForSphinx,
                    successCriterion = SuccessCriterion.CollectionNonEmpty("approachExiled", min = 5),
                ),
                descriptionOverride =
                    "You may exile this spell and four cards named Sphinx's Approach from your graveyard",
                sourceRequiredZone = Zone.STACK,
            ),
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "41"
        artist = "Nathaniel Himawan"
        scryfallId = "f49be090-c745-40e5-bc1c-605b8d98acdf"
        imageUri = "https://cards.scryfall.io/normal/front/f/4/f49be090-c745-40e5-bc1c-605b8d98acdf.jpg"
        releaseDate = "2026-10-02"
    }
}
