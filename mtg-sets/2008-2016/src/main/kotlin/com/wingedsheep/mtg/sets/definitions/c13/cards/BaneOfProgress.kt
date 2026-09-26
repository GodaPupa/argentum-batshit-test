package com.wingedsheep.mtg.sets.definitions.c13.cards

import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Bane of Progress
 * {4}{G}{G}
 * Creature — Elemental
 *
 * When this creature enters, destroy all artifacts and enchantments. Put a +1/+1 counter on
 * this creature for each permanent destroyed this way.
 *
 * Canonical printing: Commander 2013 #137.
 */
val BaneOfProgress = card("Bane of Progress") {
    manaCost = "{4}{G}{G}"
    colorIdentity = "G"
    typeLine = "Creature — Elemental"
    power = 2
    toughness = 2
    oracleText =
        "When this creature enters, destroy all artifacts and enchantments. " +
        "Put a +1/+1 counter on this creature for each permanent destroyed this way."

    triggeredAbility {
        trigger = Triggers.EntersBattlefield
        effect = Effects.Composite(
            Effects.DestroyAll(
                filter = GameObjectFilter.ArtifactOrEnchantment,
                storeDestroyedAs = "baneDestroyed",
            ),
            Effects.AddDynamicCounters(
                counterType = Counters.PLUS_ONE_PLUS_ONE,
                amount = DynamicAmount.VariableReference("baneDestroyed_count"),
                target = EffectTarget.Self,
            ),
        )
    }

    metadata {
        rarity = Rarity.RARE
        collectorNumber = "137"
        artist = "Lars Grant-West"
        flavorText = "It sees shaped stone and carved wood as mutilations."
    }
}
