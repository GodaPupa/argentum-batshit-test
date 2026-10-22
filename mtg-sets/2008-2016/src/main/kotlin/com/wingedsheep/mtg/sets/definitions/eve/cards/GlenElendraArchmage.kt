package com.wingedsheep.mtg.sets.definitions.eve.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity

/** Glen Elendra Archmage — Eventide #22. */
val GlenElendraArchmage = card("Glen Elendra Archmage") {
    manaCost = "{3}{U}"
    colorIdentity = "U"
    typeLine = "Creature — Faerie Wizard"
    oracleText = "Flying\n{U}, Sacrifice Glen Elendra Archmage: Counter target noncreature spell.\nPersist"
    power = 2
    toughness = 2

    keywords(Keyword.FLYING, Keyword.PERSIST)

    activatedAbility {
        cost = Costs.Composite(Costs.Mana("{U}"), Costs.SacrificeSelf)
        target = Targets.NoncreatureSpell
        effect = Effects.CounterSpell()
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "22"
    }
}
