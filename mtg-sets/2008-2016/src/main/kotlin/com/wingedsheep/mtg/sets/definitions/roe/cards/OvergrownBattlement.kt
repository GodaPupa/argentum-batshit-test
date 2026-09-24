package com.wingedsheep.mtg.sets.definitions.roe.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.TimingRule
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Overgrown Battlement
 * {1}{G}
 * Creature — Wall
 * 0/4
 * Defender
 * {T}: Add {G} for each creature with defender you control.
 */
val OvergrownBattlement = card("Overgrown Battlement") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Wall"
    oracleText = "Defender\n{T}: Add {G} for each creature with defender you control."
    power = 0
    toughness = 4

    keywords(Keyword.DEFENDER)

    activatedAbility {
        cost = Costs.Tap
        effect = Effects.AddMana(
            Color.GREEN,
            DynamicAmount.AggregateBattlefield(
                Player.You,
                GameObjectFilter.Creature.withKeyword(Keyword.DEFENDER),
            ),
        )
        manaAbility = true
        timing = TimingRule.ManaAbility
    }

    metadata {
        rarity = Rarity.COMMON
    }
}
