package com.wingedsheep.mtg.sets.definitions.c13.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.AddManaOfChoiceEffect
import com.wingedsheep.sdk.scripting.effects.ManaSpellRider
import com.wingedsheep.sdk.scripting.values.ManaColorSet

/** Commander 2013 #310. Its second ability produces no mana without a colored commander identity. */
val OpalPalace = card("Opal Palace") {
    typeLine = "Land"
    colorIdentity = ""
    oracleText = "{T}: Add {C}.\n" +
        "{1}, {T}: Add one mana of any color in your commander's color identity. " +
        "If you spend this mana to cast your commander, it enters with a number of additional " +
        "+1/+1 counters on it equal to the number of times it's been cast from the command zone this game."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{1}"), Costs.Tap)
        effect = AddManaOfChoiceEffect(
            colorSet = ManaColorSet.CommanderIdentity,
            riders = setOf(ManaSpellRider.CommanderCastEntryCounters),
        )
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "310"
        artist = "Andreas Rocha"
        imageUri = "https://cards.scryfall.io/normal/front/e/8/e8b912d9-5682-4462-954a-b4c2eb1aebbb.jpg?1783939624"
        ruling("2020-11-10", "The \"number of times it's been cast from the command zone\" includes the most recent time. For example, the first time you cast your commander from the command zone in a game, if you spent mana from Opal Palace's last ability to do so, it will enter the battlefield with a +1/+1 counter.")
        ruling("2020-11-10", "If you have two commanders, the last ability adds one mana of any color in their combined color identities.")
        ruling("2020-11-10", "If Opal Palace's last ability produces two mana (most likely due to Mana Reflection) and you spend them to cast a commander, that commander enters with two counters for each time it's been cast from the command zone this game.")
        ruling("2020-11-10", "If you don't have a commander, Opal Palace's last ability produces no mana.")
        ruling("2020-11-10", "If your commander is a card that has no colors in its color identity, Opal Palace's last ability produces no mana. It doesn't produce {C}.")
    }
}
