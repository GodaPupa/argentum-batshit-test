package com.wingedsheep.mtg.sets.definitions.lea.cards

import com.wingedsheep.sdk.dsl.DynamicAmounts
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetObject

/**
 * Swords to Plowshares
 * {W}
 * Instant
 * Exile target creature. Its controller gains life equal to its power.
 *
 * The life gain ([DynamicAmounts.targetPower]) is sequenced before the exile so the
 * targeted creature's power and controller are read while it is still on the
 * battlefield, matching the ruling that last-known power is used if the creature
 * somehow leaves before the life gain would otherwise see it.
 */
val SwordsToPlowshares = card("Swords to Plowshares") {
    manaCost = "{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText = "Exile target creature. Its controller gains life equal to its power."

    spell {
        val creature = target(
            "target creature",
            TargetObject(filter = TargetFilter.Creature)
        )
        effect = Effects.GainLife(DynamicAmounts.targetPower(), EffectTarget.TargetController)
            .then(Effects.Exile(creature))
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "40"
        artist = "Jeff A. Menges"
        imageUri = "https://cards.scryfall.io/normal/front/3/8/386ea9eb-abc1-4862-aa2d-8fb808d79490.jpg?1783948709"
        ruling("2022-12-08", "Use the power of the creature from when it was last on the battlefield to determine how much life is gained.")
    }
}
