package com.wingedsheep.mtg.sets.definitions.c20.cards

import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.effects.ConditionalEffect
import com.wingedsheep.sdk.scripting.references.Player

val BondersOrnament = card("Bonder's Ornament") {
    manaCost = "{3}"
    typeLine = "Artifact"
    oracleText = "{T}: Add one mana of any color.\n{4}, {T}: Each player who controls a permanent named Bonder's Ornament draws a card."

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddManaOfChoice()
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{4}"), Costs.Tap)
        effect = Effects.ForEachPlayer(
            players = Player.Each,
            effects = listOf(
                ConditionalEffect(
                    condition = Conditions.YouControl(
                        GameObjectFilter.Any.named("Bonder's Ornament")
                    ),
                    effect = Effects.DrawCards(1)
                )
            )
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "67"
    }
}
