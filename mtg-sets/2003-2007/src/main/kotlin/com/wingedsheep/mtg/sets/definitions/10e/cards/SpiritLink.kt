package com.wingedsheep.mtg.sets.definitions.`10e`.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.Triggers
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.TriggerBinding
import com.wingedsheep.sdk.scripting.values.ContextPropertyKey
import com.wingedsheep.sdk.scripting.values.DynamicAmount

/**
 * Spirit Link
 * {W}
 * Enchantment — Aura
 *
 * Enchant creature
 * Whenever enchanted creature deals damage, you gain that much life.
 */
val SpiritLink = card("Spirit Link") {
    manaCost = "{W}"
    colorIdentity = "W"
    typeLine = "Enchantment — Aura"
    oracleText = "Enchant creature\nWhenever enchanted creature deals damage, you gain that much life."

    auraTarget = Targets.Creature

    triggeredAbility {
        trigger = Triggers.dealsDamage(binding = TriggerBinding.ATTACHED)
        effect = Effects.GainLife(
            DynamicAmount.ContextProperty(ContextPropertyKey.TRIGGER_DAMAGE_AMOUNT)
        )
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "45"
        artist = "Kev Walker"
    }
}
