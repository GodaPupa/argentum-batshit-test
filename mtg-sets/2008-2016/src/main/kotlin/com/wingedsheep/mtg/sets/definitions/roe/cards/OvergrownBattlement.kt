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
    oracleText = "Defender\n{T}: Add {G} for each creature you control with defender."
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
        collectorNumber = "203"
        artist = "Franz Vohwinkel"
        flavorText = "\"Our enemy is nothing less than the end of all life. We shall not want for allies.\"\n—*The War Diaries*"
        imageUri = "https://cards.scryfall.io/normal/front/f/2/f2e3d197-e978-4ec6-ab69-3c5fd8ac3fc1.jpg?1783941961"
        ruling("2017-11-17", "Overgrown Battlement's last ability is a mana ability. It doesn't use the stack and can't be responded to (such as by removing creatures with defender you control).")
    }
}
