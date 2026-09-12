package com.wingedsheep.mtg.sets.definitions.mmq.cards

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.dsl.Conditions
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Filters
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.SelfAlternativeCost
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetCreature

val SnuffOut = card("Snuff Out") {
    manaCost = "{3}{B}"
    colorIdentity = "B"
    typeLine = "Instant"
    oracleText = "If you control a Swamp, you may pay 4 life rather than pay this spell's mana cost.\n" +
        "Destroy target nonblack creature. It can't be regenerated."

    selfAlternativeCost = SelfAlternativeCost(
        manaCost = ManaCost.parse("{0}"),
        additionalCosts = listOf(Costs.additional.PayLife(4)),
        condition = Conditions.YouControl(Filters.SwampCard),
    )

    spell {
        val creature = target(
            "target nonblack creature",
            TargetCreature(filter = TargetFilter.Creature.notColor(Color.BLACK)),
        )
        effect = Effects.Destroy(creature, noRegenerate = true)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "162"
        artist = "Mike Ploog"
    }
}
