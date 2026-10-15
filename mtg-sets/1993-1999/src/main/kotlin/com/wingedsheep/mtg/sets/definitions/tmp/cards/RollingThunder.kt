package com.wingedsheep.mtg.sets.definitions.tmp.cards

import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.DividedDamageEffect
import com.wingedsheep.sdk.scripting.targets.AnyTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Rolling Thunder — Tempest #198
 * {X}{R}{R}
 * Sorcery
 *
 * Rolling Thunder deals X damage divided as you choose among any number of targets.
 *
 * Batch T composes the existing X-cost and divided-damage rails. The only generic
 * target-side addition is AnyTarget(unlimited = true); the amount is the already-qualified
 * DynamicAmount.XValue.
 */
val RollingThunder = card("Rolling Thunder") {
    manaCost = "{X}{R}{R}"
    colorIdentity = "R"
    typeLine = "Sorcery"
    oracleText = "Rolling Thunder deals X damage divided as you choose among any number of targets."

    spell {
        target = AnyTarget(unlimited = true)
        effect = DividedDamageEffect(
            totalDamage = 0,
            minTargets = 0,
            dynamicTotal = DynamicAmount.XValue
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "198"
        artist = "Richard Thomas"
        flavorText = "\"Such rage,\" thought Vhati, gazing up at the thunderhead from the Predator. \"It is Greven's mind manifest.\""
    }
}
