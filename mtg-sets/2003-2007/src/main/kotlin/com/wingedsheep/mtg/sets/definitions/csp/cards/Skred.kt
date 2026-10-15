package com.wingedsheep.mtg.sets.definitions.csp.cards

import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.predicates.CardPredicate
import com.wingedsheep.sdk.scripting.references.Player
import com.wingedsheep.sdk.scripting.targets.TargetCreature

/**
 * Skred
 * {R}
 * Instant
 *
 * Skred deals damage to target creature equal to the number of snow permanents you control.
 */
val Skred = card("Skred") {
    manaCost = "{R}"
    colorIdentity = "R"
    typeLine = "Instant"
    oracleText = "Skred deals damage to target creature equal to the number of snow permanents you control."

    spell {
        val creature = target("target creature", TargetCreature(filter = TargetFilter.Creature))
        val snowPermanents = GameObjectFilter.Permanent.withCardPredicate(CardPredicate.IsSnow)
        effect = Effects.DealDamage(
            DynamicAmounts.battlefield(Player.You, snowPermanents).count(),
            creature
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "97"
        artist = "Christopher Moeller"
    }
}
