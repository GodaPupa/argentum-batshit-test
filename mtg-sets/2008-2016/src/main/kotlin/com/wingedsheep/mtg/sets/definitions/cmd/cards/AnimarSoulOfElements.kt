package com.wingedsheep.mtg.sets.definitions.cmd.cards

import com.wingedsheep.sdk.dsl.*
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.scripting.*
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.events.CounterTypeFilter

/** Exact frozen Manual Transmission v0.7 identity; composes existing engine primitives. */
val AnimarSoulOfElements = card("Animar, Soul of Elements") {
    manaCost = "{G}{U}{R}"
    colorIdentity = "GRU"
    typeLine = "Legendary Creature — Elemental"
    oracleText = "Protection from white and from black\nWhenever you cast a creature spell, put a +1/+1 counter on Animar.\nCreature spells you cast cost {1} less to cast for each +1/+1 counter on Animar."
    power = 1
    toughness = 1

    keywordAbility(KeywordAbility.protectionFrom(Color.WHITE))
    keywordAbility(KeywordAbility.protectionFrom(Color.BLACK))

    triggeredAbility {
        trigger = Triggers.YouCastCreature
        effect = Effects.AddCounters(Counters.PLUS_ONE_PLUS_ONE, 1, EffectTarget.Self)
    }

    staticAbility {
        ability = ModifySpellCost(
            target = SpellCostTarget.YouCast(GameObjectFilter.Creature),
            modification = CostModification.ReduceGenericBy(
                CostReductionSource.Dynamic(DynamicAmounts.countersOnSelf(CounterTypeFilter.PlusOnePlusOne))
            )
        )
    }

    metadata {
        rarity = Rarity.MYTHIC
        collectorNumber = "181"
        artist = "Peter Mohrbacher"
        imageUri = "https://cards.scryfall.io/normal/front/c/b/cb073d5b-9515-492d-9b2d-0f64e85f1da8.jpg?1783941186"
        ruling("2018-03-16", "Animar's triggered ability triggers only when a creature spell is cast, after costs are paid. The counter put on Animar for a creature spell won't affect the cost of that creature spell, only future ones.")
        ruling("2018-03-16", "Animar's triggered ability resolves before the creature spell that causes it to trigger. The ability will resolve even if that spell is countered.")
        ruling("2018-03-16", "To determine the total cost of a creature spell, start with the mana cost or alternative cost you're paying, add any cost increases, then apply any cost reductions. The mana value of the creature remains unchanged, no matter what the total cost to cast it was.")
    }
}
