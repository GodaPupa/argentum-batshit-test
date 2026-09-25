package com.wingedsheep.mtg.sets.definitions.dis.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Exact frozen Manual Transmission v0.7 identity; composed from existing shared mechanics. */
val CoilingOracle = card("Coiling Oracle") {
    manaCost = "{G}{U}"
    colorIdentity = "GU"
    typeLine = "Creature — Snake Elf Druid"
    oracleText = "When this creature enters, reveal the top card of your library. If it's a land card, put it onto the battlefield. Otherwise, put that card into your hand."
    power = 1
    toughness = 1

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.Composite(listOf(
            GatherCardsEffect(
                source = CardSource.TopOfLibrary(DynamicAmount.Fixed(1)),
                storeAs = "revealed",
                revealed = true
            ),
            SelectFromCollectionEffect(
                from = "revealed",
                selection = SelectionMode.All,
                filter = GameObjectFilter.Land,
                storeSelected = "land",
                storeRemainder = "nonland"
            ),
            MoveCollectionEffect(from = "land", destination = CardDestination.ToZone(Zone.BATTLEFIELD)),
            MoveCollectionEffect(from = "nonland", destination = CardDestination.ToZone(Zone.HAND))
        ))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "107"
        artist = "Mark Zug"
        flavorText = "Snaking remnants of nature directed by a body of thought and progress, the oracles embody all that is Simic."
        imageUri = "https://cards.scryfall.io/normal/front/5/5/55a6ba2a-b372-4b15-9a1e-09b41316eab7.jpg?1783943405"
    }
}
