package com.wingedsheep.mtg.sets.definitions.ulg.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.Chooser
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.TapUntapCollectionEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Cloud of Faeries
 * {1}{U}
 * Creature — Faerie
 * 1/1
 * Flying
 * When this creature enters, untap up to two lands.
 * Cycling {2} ({2}, Discard this card: Draw a card.)
 *
 * Lands are chosen as the triggered ability resolves, without targeting or a controller restriction.
 */
val CloudOfFaeries = card("Cloud of Faeries") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Faerie"
    oracleText = "Flying\nWhen this creature enters, untap up to two lands.\nCycling {2} ({2}, Discard this card: Draw a card.)"
    power = 1
    toughness = 1
    keywords(Keyword.FLYING)
    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.Composite(
            GatherCardsEffect(
                source = CardSource.BattlefieldMatching(
                    filter = GameObjectFilter.Land,
                    player = Player.Each
                ),
                storeAs = "cloudLands"
            ),
            SelectFromCollectionEffect(
                from = "cloudLands",
                selection = SelectionMode.ChooseUpTo(DynamicAmount.Fixed(2)),
                chooser = Chooser.Controller,
                filter = GameObjectFilter.Land,
                storeSelected = "cloudUntap",
                prompt = "Choose up to two lands to untap",
                useTargetingUI = true
            ),
            TapUntapCollectionEffect(collectionName = "cloudUntap", tap = false)
        )
    }
    keywordAbility(KeywordAbility.cycling("{2}"))
    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "29"
        artist = "Melissa A. Benson"
        imageUri = "https://cards.scryfall.io/normal/front/4/e/4e76d04a-0038-4b5b-a026-3056ee940da9.jpg?1783946247"
        ruling(
            "2022-12-08",
            "You choose which lands to untap as the triggered ability resolves. " +
                "They aren't targeted, and they don't have to be lands that you control."
        )
    }
}
