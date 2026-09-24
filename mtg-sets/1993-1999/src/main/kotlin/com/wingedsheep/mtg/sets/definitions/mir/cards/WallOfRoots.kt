package com.wingedsheep.mtg.sets.definitions.mir.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.ActivationRestriction
import com.wingedsheep.sdk.scripting.TimingRule

/**
 * Wall of Roots
 * {1}{G}
 * Creature — Plant Wall
 * 0/5
 * Defender
 * Put a -0/-1 counter on Wall of Roots: Add {G}. Activate only once each turn.
 */
val WallOfRoots = card("Wall of Roots") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Plant Wall"
    oracleText = "Defender\nPut a -0/-1 counter on this creature: Add {G}. Activate only once each turn."
    power = 0
    toughness = 5

    keywords(Keyword.DEFENDER)

    activatedAbility {
        cost = Costs.PutCounterOnSelf("-0/-1")
        effect = Effects.AddMana(Color.GREEN)
        restrictions = listOf(ActivationRestriction.OncePerTurn)
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.COMMON
    }
}
