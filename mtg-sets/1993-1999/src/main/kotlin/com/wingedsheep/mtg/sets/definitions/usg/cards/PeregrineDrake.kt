package com.wingedsheep.mtg.sets.definitions.usg.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.Chooser
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.SelectionMode
import com.wingedsheep.sdk.scripting.effects.SelectFromCollectionEffect
import com.wingedsheep.sdk.scripting.effects.TapUntapCollectionEffect
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Peregrine Drake
 * {4}{U}
 * Creature — Drake
 * 2/3
 * Flying
 * When this creature enters, untap up to five lands.
 *
 * Lands are chosen as the triggered ability resolves, without targeting or a controller restriction.
 */
val PeregrineDrake = card("Peregrine Drake") {
    manaCost = "{4}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Drake"
    oracleText = "Flying\nWhen this creature enters, untap up to five lands."
    power = 2
    toughness = 3
    keywords(Keyword.FLYING)
    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.Composite(
            GatherCardsEffect(
                source = CardSource.BattlefieldMatching(
                    filter = GameObjectFilter.Land,
                    player = Player.Each
                ),
                storeAs = "drakeLands"
            ),
            SelectFromCollectionEffect(
                from = "drakeLands",
                selection = SelectionMode.ChooseUpTo(DynamicAmount.Fixed(5)),
                chooser = Chooser.Controller,
                filter = GameObjectFilter.Land,
                storeSelected = "drakeUntap",
                prompt = "Choose up to five lands to untap",
                useTargetingUI = true
            ),
            TapUntapCollectionEffect(collectionName = "drakeUntap", tap = false)
        )
    }
    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "88"
        artist = "Bob Eggleton"
        flavorText = "That the Tolarian mists parted for the drakes was warning enough to stay away."
        imageUri = "https://cards.scryfall.io/normal/front/4/9/4951863f-1c16-4d09-ba9a-f57dc3d81a20.jpg?1783946357"
        ruling(
            "2022-12-08",
            "You choose which lands to untap as the triggered ability resolves. " +
                "They aren't targeted, and they don't have to be lands that you control."
        )
    }
}
