package com.wingedsheep.mtg.sets.definitions.rtr.cards

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.CostModification
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.ModifySpellCost
import com.wingedsheep.sdk.scripting.SpellCostTarget

/**
 * Goblin Electromancer
 * {U}{R}
 * Creature — Goblin Wizard
 * 2/2
 *
 * Instant and sorcery spells you cast cost {1} less to cast.
 */
val GoblinElectromancer = card("Goblin Electromancer") {
    manaCost = "{U}{R}"
    colorIdentity = "UR"
    typeLine = "Creature — Goblin Wizard"
    oracleText = "Instant and sorcery spells you cast cost {1} less to cast."
    power = 2
    toughness = 2

    staticAbility {
        ability = ModifySpellCost(
            target = SpellCostTarget.YouCast(GameObjectFilter.InstantOrSorcery),
            modification = CostModification.ReduceGeneric(1),
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "163"
        artist = "Svetlin Velinov"
        flavorText = "When asked how much power is required, Izzet mages always answer \"more.\""
    }
}
