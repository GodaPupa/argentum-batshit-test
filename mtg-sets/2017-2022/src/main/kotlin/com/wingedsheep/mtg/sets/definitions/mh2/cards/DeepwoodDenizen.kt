package com.wingedsheep.mtg.sets.definitions.mh2.cards

import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.events.CounterTypeFilter
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.Aggregation
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Exact Oracle and printing data: https://scryfall.com/card/mh2/155/deepwood-denizen */
val DeepwoodDenizen = card("Deepwood Denizen") {
    manaCost = "{2}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elf Warrior"
    oracleText = "Vigilance (Attacking doesn't cause this creature to tap.)\n{5}{G}, {T}: Draw a card. This ability costs {1} less to activate for each +1/+1 counter on creatures you control."
    power = 3
    toughness = 2

    keywords(Keyword.VIGILANCE)
    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{5}{G}"), Costs.Tap)
        genericCostReduction = DynamicAmount.AggregateBattlefield(
            player = Player.You,
            filter = GameObjectFilter.Creature,
            aggregation = Aggregation.SUM,
            counterType = CounterTypeFilter.PlusOnePlusOne
        )
        effect = Effects.DrawCards(1)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "155"
        artist = "Josu Hernaiz"
        flavorText = "\"You didn't come all this way just to bother me, did you? Say your piece and be gone.\""
        imageUri = "https://cards.scryfall.io/normal/front/3/3/333f02f7-3b8a-41e3-9ae5-2151539e64ad.jpg?1783926833"
    }
}
