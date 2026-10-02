package com.wingedsheep.mtg.sets.definitions.wwk.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.EntersWithDynamicCounters
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.events.CounterTypeFilter
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.EntityNumericProperty
import com.wingedsheep.sdk.scripting.values.EntityReference

/**
 * Everflowing Chalice — Worldwake #123
 * {0}
 * Artifact
 *
 * Multikicker {2}
 * This artifact enters with a charge counter on it for each time it was kicked.
 * {T}: Add {C} for each charge counter on this artifact.
 */
val EverflowingChalice = card("Everflowing Chalice") {
    manaCost = "{0}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "Multikicker {2} (You may pay an additional {2} any number of times as you cast this spell.)\n" +
        "This artifact enters with a charge counter on it for each time it was kicked.\n" +
        "{T}: Add {C} for each charge counter on this artifact."

    keywordAbility(KeywordAbility.multikicker("{2}"))

    replacementEffect(
        EntersWithDynamicCounters(
            counterType = CounterTypeFilter.Named(Counters.CHARGE),
            count = DynamicAmount.CastChoice(ChoiceSlot.KICKED),
        )
    )

    activatedAbility {
        cost = Costs.Tap
        manaAbility = true
        effect = Effects.AddColorlessMana(
            DynamicAmount.EntityProperty(
                EntityReference.Source,
                EntityNumericProperty.CounterCount(CounterTypeFilter.Named(Counters.CHARGE)),
            )
        )
        description = "{T}: Add {C} for each charge counter on this artifact."
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "123"
        artist = "Steve Argyle"
    }
}
