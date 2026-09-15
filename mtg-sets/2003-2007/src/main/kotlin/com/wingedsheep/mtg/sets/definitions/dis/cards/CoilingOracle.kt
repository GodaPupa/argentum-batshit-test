package com.wingedsheep.mtg.sets.definitions.dis.cards

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardDestination
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CollectionFilter
import com.wingedsheep.sdk.scripting.effects.FilterCollectionEffect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.RevealCollectionEffect
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Coiling Oracle — Dissension #107. */
val CoilingOracle = card("Coiling Oracle") {
    manaCost = "{G}{U}"
    colorIdentity = "GU"
    typeLine = "Creature — Snake Elf Druid"
    power = 1
    toughness = 1
    oracleText = "When this creature enters, reveal the top card of your library. If it's a land " +
        "card, put it onto the battlefield. Otherwise, put that card into your hand."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.Composite(
            listOf(
                GatherCardsEffect(
                    source = CardSource.TopOfLibrary(DynamicAmount.Fixed(1)),
                    storeAs = "revealed",
                ),
                RevealCollectionEffect(from = "revealed"),
                FilterCollectionEffect(
                    from = "revealed",
                    filter = CollectionFilter.MatchesFilter(GameObjectFilter.Land),
                    storeMatching = "landCards",
                    storeNonMatching = "nonLandCards",
                ),
                MoveCollectionEffect(
                    from = "landCards",
                    destination = CardDestination.ToZone(Zone.BATTLEFIELD),
                ),
                MoveCollectionEffect(
                    from = "nonLandCards",
                    destination = CardDestination.ToZone(Zone.HAND),
                ),
            )
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "107"
        artist = "Mark Zug"
        imageUri = "https://cards.scryfall.io/normal/front/5/5/55a6ba2a-b372-4b15-9a1e-09b41316eab7.jpg?1783943405"
    }
}
