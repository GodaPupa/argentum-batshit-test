package com.wingedsheep.mtg.sets.definitions.soi.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Patterns
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardOrder
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Pieces of the Puzzle
 * {2}{U}
 * Sorcery
 *
 * Reveal the top five cards of your library. Put up to two instant and/or
 * sorcery cards from among them into your hand and the rest into your graveyard.
 */
val PiecesOfThePuzzle = card("Pieces of the Puzzle") {
    manaCost = "{2}{U}"
    colorIdentity = "U"
    typeLine = "Sorcery"
    oracleText = "Reveal the top five cards of your library. Put up to two instant and/or sorcery cards from among them into your hand and the rest into your graveyard."

    spell {
        effect = Patterns.Library.lookAtTopAndTakeMatching(
            count = DynamicAmount.Fixed(5),
            filter = GameObjectFilter.InstantOrSorcery,
            prompt = "Put up to two instant and/or sorcery cards from among them into your hand",
            selection = SelectionMode.ChooseUpTo(DynamicAmount.Fixed(2)),
            revealed = true,
            restDestination = CardDestination.ToZone(Zone.GRAVEYARD),
            restOrder = CardOrder.Preserve,
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "78"
        artist = "Magali Villeneuve"
        flavorText = "\"The clues have begun to reveal a truth I hesitate to accept.\""
    }
}
