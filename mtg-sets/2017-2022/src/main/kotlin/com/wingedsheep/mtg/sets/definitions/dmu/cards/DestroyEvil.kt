package com.wingedsheep.mtg.sets.definitions.dmu.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.ModalEffect
import com.wingedsheep.sdk.scripting.effects.Mode
import com.wingedsheep.sdk.scripting.filters.unified.TargetFilter
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetCreature
import com.wingedsheep.sdk.scripting.targets.withId

/**
 * Destroy Evil — Dominaria United #17
 * {1}{W}
 * Instant
 *
 * Choose one —
 * • Destroy target creature with toughness 4 or greater.
 * • Destroy target enchantment.
 *
 * Both modes compose existing generic modal-target and destruction rails. The creature branch
 * uses the same toughness-at-least predicate already exercised by Valorous Stance.
 */
val DestroyEvil = card("Destroy Evil") {
    manaCost = "{1}{W}"
    colorIdentity = "W"
    typeLine = "Instant"
    oracleText = "Choose one —\n" +
        "• Destroy target creature with toughness 4 or greater.\n" +
        "• Destroy target enchantment."

    spell {
        val chosen = EffectTarget.BoundVariable("target")
        effect = ModalEffect.chooseOne(
            Mode.withTarget(
                effect = Effects.Destroy(chosen),
                target = TargetCreature(
                    filter = TargetFilter.Creature.toughnessAtLeast(4)
                ).withId("target")
            ),
            Mode.withTarget(
                effect = Effects.Destroy(chosen),
                target = Targets.Enchantment.withId("target")
            )
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "17"
        artist = "Anna Christenson"
        flavorText = "Serra's grace most often manifests as a healing touch, but it may also grant a merciful death."
        imageUri = "https://cards.scryfall.io/normal/front/b/7/b75e667a-1ceb-445a-acf4-ae1fd2dae9e7.jpg"
    }
}
