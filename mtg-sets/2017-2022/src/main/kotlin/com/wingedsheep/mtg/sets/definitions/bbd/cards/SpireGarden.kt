package com.wingedsheep.mtg.sets.definitions.bbd.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.EntersTapped
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.conditions.Compare
import com.wingedsheep.sdk.scripting.conditions.ComparisonOperator
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/** Exact frozen Manual Transmission v0.7 identity, using the current live opponent count. */
val SpireGarden = card("Spire Garden") {
    manaCost = ""
    colorIdentity = "GR"
    typeLine = "Land"
    oracleText = "This land enters tapped unless you have two or more opponents.\n{T}: Add {R} or {G}."

    replacementEffect(EntersTapped(unlessCondition = Compare(
        DynamicAmount.PlayerCount(Player.EachOpponent),
        ComparisonOperator.GTE,
        DynamicAmount.Fixed(2)
    )))

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.RED)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(Color.GREEN)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "85"
        artist = "Darek Zabrocki"
        flavorText = "From thrilling combat to unparalleled vistas, Valor's Reach has it all."
        imageUri = "https://cards.scryfall.io/normal/front/6/4/64943615-7543-4acd-a884-22ece8f0ed3e.jpg?1783934847"
        ruling("2018-06-08", "If you began the game with two or more opponents but now only have one opponent left, these lands enter the battlefield tapped.")
    }
}
