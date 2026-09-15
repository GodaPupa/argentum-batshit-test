package com.wingedsheep.mtg.sets.definitions.mmq.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.EntersWithCounters
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.effects.SacrificeSelfEffect
import com.wingedsheep.sdk.scripting.events.CounterTypeFilter

/** Hickory Woodlot — Mercadian Masques #319. */
val HickoryWoodlot = card("Hickory Woodlot") {
    colorIdentity = "G"
    typeLine = "Land"
    oracleText = "This land enters tapped with two depletion counters on it.\n" +
        "{T}, Remove a depletion counter from this land: Add {G}{G}. If there are no depletion " +
        "counters on this land, sacrifice it."

    replacementEffect(EntersTapped())
    replacementEffect(
        EntersWithCounters(
            counterType = CounterTypeFilter.Named(Counters.DEPLETION),
            count = 2,
            selfOnly = true,
        )
    )

    activatedAbility {
        cost = Costs.Composite(Costs.Tap, Costs.RemoveCounterFromSelf(Counters.DEPLETION))
        manaAbility = true
        effect = Effects.Composite(
            Effects.AddMana(Color.GREEN, 2),
            ConditionalEffect(
                condition = Conditions.SourceCounterCountAtMost(Counters.DEPLETION, 0),
                effect = SacrificeSelfEffect,
            ),
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "319"
        artist = "Sean McConnell"
        imageUri = "https://cards.scryfall.io/normal/front/a/f/af7aafb7-6870-4d09-a191-70786766c459.jpg?1562382543"
    }
}
