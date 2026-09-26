package com.wingedsheep.mtg.sets.definitions.c18.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetObject

val ForgeOfHeroes = card("Forge of Heroes") {
    typeLine = "Land"
    oracleText = "{T}: Add {C}.\n" +
        "{T}: Choose target commander that entered this turn. Put a +1/+1 counter on it if it's a creature and a loyalty counter on it if it's a planeswalker."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddColorlessMana(1)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Tap
        val commander = target("commander that entered this turn", TargetObject(
            filter = TargetFilter(GameObjectFilter.Any.commander().enteredThisTurn(), zone = Zone.BATTLEFIELD)
        ))
        // Independent resolution-time tests: a commander can be either type, both, or neither.
        effect = ConditionalEffect(
            condition = Conditions.TargetMatchesFilter(GameObjectFilter.Creature),
            effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, commander)
        ).then(ConditionalEffect(
            condition = Conditions.TargetMatchesFilter(GameObjectFilter.Planeswalker),
            effect = Effects.AddCounters(Counters.LOYALTY, 1, commander)
        ))
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "58"
        artist = "Titus Lunter"
        flavorText = "History is not delicately woven. It is hammered with fire."
        imageUri = "https://cards.scryfall.io/normal/front/8/2/826ff8c2-8a1d-45f5-a7e2-d8bf826fcadc.jpg?1783934322"
        ruling("2018-07-13", "If the target commander is somehow a creature and a planeswalker, most likely because it's Gideon, it receives both kinds of counters.")
        ruling("2018-07-13", "If the target commander is somehow neither a creature nor a planeswalker (such as Arixmethes, Slumbering Isle may be), it receives no counters.")
    }
}
