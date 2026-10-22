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
        ruling("2020-11-10", "The number of times it's been cast from the command zone includes the most recent time.")
        ruling("2020-11-10", "If the last ability produces two mana and you spend both to cast a commander, it enters with two counters for each time it's been cast from the command zone this game.")
    }
}
