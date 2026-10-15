package com.wingedsheep.mtg.sets.definitions.neo.cards

import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.DealDamageEffect
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.TargetCreature
import com.wingedsheep.sdk.scripting.targets.TargetObject

/**
 * Master's Rebuke
 * {1}{G}
 * Instant
 *
 * Target creature you control deals damage equal to its power to target creature or planeswalker
 * you don't control.
 */
val MastersRebuke = card("Master's Rebuke") {
    manaCost = "{1}{G}"
    colorIdentity = "G"
    typeLine = "Instant"
    oracleText = "Target creature you control deals damage equal to its power to target creature or planeswalker you don't control."

    spell {
        val source = target(
            "target creature you control",
            TargetCreature(filter = TargetFilter.Creature.youControl())
        )
        val recipient = target(
            "target creature or planeswalker you don't control",
            TargetObject(filter = TargetFilter(GameObjectFilter.CreatureOrPlaneswalker.opponentControls())),
        )
        effect = DealDamageEffect(DynamicAmounts.targetPower(0), recipient, damageSource = source)
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "202"
        artist = "Francisco Miyara"
        flavorText = "\"You have always been a promising student, but your arrogance far outstrips your prowess.\""
    }
}
