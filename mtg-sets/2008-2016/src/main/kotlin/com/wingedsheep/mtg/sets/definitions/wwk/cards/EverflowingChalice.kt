package com.wingedsheep.mtg.sets.definitions.wwk.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ChoiceSlot
import com.wingedsheep.sdk.scripting.EntersWithDynamicCounters
import com.wingedsheep.sdk.scripting.KeywordAbility
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.events.CounterTypeFilter
import com.wingedsheep.sdk.scripting.values.DynamicAmount
import com.wingedsheep.sdk.scripting.values.EntityNumericProperty
import com.wingedsheep.sdk.scripting.values.EntityReference

/** Earliest expansion printing: Worldwake #123. */
val EverflowingChalice = card("Everflowing Chalice") {
    manaCost = "{0}"
    colorIdentity = ""
    typeLine = "Artifact"
    oracleText = "Multikicker {2} (You may pay an additional {2} any number of times as you cast this spell.)\n" +
        "This artifact enters with a charge counter on it for each time it was kicked.\n" +
        "{T}: Add {C} for each charge counter on this artifact."

    keywordAbility(KeywordAbility.multikicker("{2}"))
    replacementEffect(EntersWithDynamicCounters(
        counterType = CounterTypeFilter.Named(Counters.CHARGE),
        count = DynamicAmount.CastChoice(ChoiceSlot.KICKED)
    ))
    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(DynamicAmount.EntityProperty(
            EntityReference.Source, EntityNumericProperty.CounterCount(CounterTypeFilter.Named(Counters.CHARGE))
        ))
        manaAbility = true
        timing = TimingRule.ManaAbility
    }
    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "123"
        artist = "Steve Argyle"
        imageUri = "https://cards.scryfall.io/normal/front/1/f/1fdcc0c3-4029-4fc3-a486-5d7f45c910bd.jpg?1783942040"
        ruling("2021-03-19", "You can cast Everflowing Chalice without kicking it at all if you wish. However, if Everflowing Chalice has no charge counters on it, activating its last ability won't produce any mana.")
        ruling("2024-11-08", "If a card or token enters as a copy of a permanent, the new permanent isn't kicked, even if the original was.")
    }
}
