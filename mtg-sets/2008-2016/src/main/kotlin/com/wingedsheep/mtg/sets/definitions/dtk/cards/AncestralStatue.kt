package com.wingedsheep.mtg.sets.definitions.dtk.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.*
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Exact frozen Manual Transmission v0.7 identity; composes existing engine primitives. */
val AncestralStatue = card("Ancestral Statue") {
    manaCost = "{4}"
    colorIdentity = ""
    typeLine = "Artifact Creature — Golem"
    oracleText = "When this creature enters, return a nonland permanent you control to its owner's hand."
    power = 3
    toughness = 4

    // This is a mandatory choice on resolution; it does not target and may choose itself.
    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.Composite(
            GatherCardsEffect(
                source = CardSource.BattlefieldMatching(GameObjectFilter.NonlandPermanent.youControl()),
                storeAs = "nonlandPermanents"
            ),
            SelectFromCollectionEffect(
                from = "nonlandPermanents",
                selection = SelectionMode.ChooseExactly(DynamicAmount.Fixed(1)),
                chooser = Chooser.Controller,
                storeSelected = "returnedPermanent",
                useTargetingUI = true,
                prompt = "Choose a nonland permanent you control to return to its owner's hand"
            ),
            MoveCollectionEffect(
                from = "returnedPermanent",
                destination = CardDestination.ToZone(Zone.HAND)
            )
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "234"
        artist = "Tomasz Jedruszek"
        flavorText = "The mage awakened the statue in hopes of learning the lost lore of her clan, but the statue was interested only in war."
        imageUri = "https://cards.scryfall.io/normal/front/c/7/c7124a6f-b690-4326-93b5-036a8c520c1f.jpg?1783938569"
        ruling("2015-02-25", "Ancestral Statue’s ability is mandatory. If Ancestral Statue is the only nonland permanent you control when its ability resolves, you must return it to its owner’s hand.")
        ruling("2015-02-25", "The triggered ability doesn’t target any permanent. You choose which one to return as the ability resolves. No player can respond to this choice once the ability starts resolving.")
    }
}
