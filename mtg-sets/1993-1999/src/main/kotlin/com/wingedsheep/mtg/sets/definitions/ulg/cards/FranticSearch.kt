package com.wingedsheep.mtg.sets.definitions.ulg.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.Chooser
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.TapUntapCollectionEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Frantic Search
 * {2}{U}
 * Instant
 *
 * Draw two cards, then discard two cards. Untap up to three lands.
 *
 * The discard and land choices occur during resolution. The lands are not
 * targets and may be controlled by any player.
 */
val FranticSearch = card("Frantic Search") {
    manaCost = "{2}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Draw two cards, then discard two cards. Untap up to three lands."

    spell {
        effect = CompositeEffect(
            listOf(
                Effects.DrawCards(2),
                Effects.Discard(2),
                GatherCardsEffect(
                    source = CardSource.BattlefieldMatching(
                        filter = GameObjectFilter.Land,
                        player = Player.Each
                    ),
                    storeAs = "franticLands"
                ),
                SelectFromCollectionEffect(
                    from = "franticLands",
                    selection = SelectionMode.ChooseUpTo(DynamicAmount.Fixed(3)),
                    chooser = Chooser.Controller,
                    filter = GameObjectFilter.Land,
                    storeSelected = "franticUntap",
                    prompt = "Choose up to three lands to untap",
                    useTargetingUI = true
                ),
                TapUntapCollectionEffect(
                    collectionName = "franticUntap",
                    tap = false
                )
            )
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "32"
        artist = "Jeff Miracola"
    }
}
