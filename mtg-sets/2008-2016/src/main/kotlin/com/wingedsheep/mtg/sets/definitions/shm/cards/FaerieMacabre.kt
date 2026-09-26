package com.wingedsheep.mtg.sets.definitions.shm.cards

import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.Costs
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetObject

/**
 * Faerie Macabre
 * {1}{B}{B}
 * Creature — Faerie Rogue
 *
 * Flying
 * Discard this card: Exile up to two target cards from graveyards.
 *
 * Canonical printing: Shadowmoor #66.
 */
val FaerieMacabre = card("Faerie Macabre") {
    manaCost = "{1}{B}{B}"
    colorIdentity = "B"
    typeLine = "Creature — Faerie Rogue"
    power = 2
    toughness = 2
    oracleText =
        "Flying\n" +
        "Discard this card: Exile up to two target cards from graveyards."

    keywords(Keyword.FLYING)

    activatedAbility {
        activateFromZone = Zone.HAND
        cost = Costs.DiscardSelf
        target(
            "up to two target cards from graveyards",
            TargetObject(
                count = 2,
                minCount = 0,
                optional = true,
                filter = TargetFilter.CardInGraveyard,
            )
        )
        effect = Effects.ForEachTarget(
            Effects.Exile(EffectTarget.ContextTarget(0), fromZone = Zone.GRAVEYARD)
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "66"
        artist = "rk post"
        flavorText = "The line between dream and death is gauzy and fragile. She leads those too near it from one side to the other."
    }
}
