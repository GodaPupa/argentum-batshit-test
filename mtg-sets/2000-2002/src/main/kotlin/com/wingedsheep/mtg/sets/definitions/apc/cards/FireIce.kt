package com.wingedsheep.mtg.sets.definitions.apc.cards

import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.CardLayout
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.DividedDamageEffect
import com.wingedsheep.sdk.scripting.targets.AnyTarget
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetPermanent

/**
 * Fire // Ice (APC 128) — split-layout spell (CR 709).
 *
 * Fire {1}{R} — Instant
 *   Fire deals 2 damage divided as you choose among one or two targets.
 *
 * Ice {1}{U} — Instant
 *   Tap target permanent. Draw a card.
 *
 * Both halves reuse already-qualified generic rails: SPLIT face casting, DividedDamageEffect,
 * TapUntapEffect, and DrawCardsEffect. No Fire // Ice-specific executor is required.
 */
val FireIce = card("Fire // Ice") {
    layout = CardLayout.SPLIT
    colorIdentity = "UR"

    face("Fire") {
        manaCost = "{1}{R}"
        typeLine = "Instant"
        oracleText = "Fire deals 2 damage divided as you choose among one or two targets."

        spell {
            target = AnyTarget(count = 2, minCount = 1)
            effect = DividedDamageEffect(
                totalDamage = 2,
                minTargets = 1,
                maxTargets = 2
            )
        }
    }

    face("Ice") {
        manaCost = "{1}{U}"
        typeLine = "Instant"
        oracleText = "Tap target permanent.\nDraw a card."

        spell {
            val target = target("target permanent", TargetPermanent())
            effect = CompositeEffect(
                listOf(
                    Effects.Tap(target),
                    Effects.DrawCards(1)
                )
            )
        }
    }

    metadata {
        rarity = Rarity.UNCOMMON
        collectorNumber = "128"
        artist = "David Martin & Franz Vohwinkel"
    }
}
