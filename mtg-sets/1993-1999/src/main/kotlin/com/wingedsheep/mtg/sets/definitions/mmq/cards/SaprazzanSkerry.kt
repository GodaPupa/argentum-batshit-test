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

/** Saprazzan Skerry — Mercadian Masques #328. */
val SaprazzanSkerry = card("Saprazzan Skerry") {
    colorIdentity = "U"
    typeLine = "Land"
    oracleText = "This land enters tapped with two depletion counters on it.\n" +
        "{T}, Remove a depletion counter from this land: Add {U}{U}. If there are no depletion " +
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
            Effects.AddMana(Color.BLUE, 2),
            ConditionalEffect(
                condition = Conditions.SourceCounterCountAtMost(Counters.DEPLETION, 0),
                effect = SacrificeSelfEffect,
            ),
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "328"
        artist = "Pat Lewis"
        imageUri = "https://cards.scryfall.io/normal/front/0/0/006871fd-2641-42cb-a2ac-a33d05fc5a35.jpg?1562378939"
    }
}
