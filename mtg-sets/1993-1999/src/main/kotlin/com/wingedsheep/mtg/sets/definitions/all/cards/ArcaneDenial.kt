package com.wingedsheep.mtg.sets.definitions.all.cards

import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.dsl.Effects
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.scripting.effects.CounterEffect
import com.wingedsheep.sdk.scripting.effects.CreateDelayedTriggerEffect
import com.wingedsheep.sdk.scripting.effects.DelayedTriggerTiming
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import com.wingedsheep.sdk.scripting.targets.TargetSpell

/**
 * Arcane Denial — Alliances #22b
 * {1}{U} · Instant
 *
 * Counter target spell. Its controller may draw up to two cards at the beginning of the next
 * turn's upkeep. You draw a card at the beginning of the next turn's upkeep.
 *
 * Both delayed abilities are scheduled before the counter. No player receives priority during
 * resolution, so that ordering is externally equivalent, and it lets the generic delayed-trigger
 * baker capture the target spell's actual controller while the spell is still on the stack.
 * DelayedTriggerTiming.NEXT_TURN gives the printed "next turn's upkeep" meaning: not this turn,
 * and with no fireOnPlayer restriction the very next player's upkeep qualifies.
 */
val ArcaneDenial = card("Arcane Denial") {
    manaCost = "{1}{U}"
    colorIdentity = "U"
    typeLine = "Instant"
    oracleText = "Counter target spell. Its controller may draw up to two cards at the beginning " +
        "of the next turn's upkeep.\nYou draw a card at the beginning of the next turn's upkeep."

    spell {
        target("target", TargetSpell())
        effect = Effects.Composite(
            listOf(
                CreateDelayedTriggerEffect(
                    step = Step.UPKEEP,
                    timing = DelayedTriggerTiming.NEXT_TURN,
                    effect = Effects.DrawUpTo(2, EffectTarget.TargetController)
                ),
                CreateDelayedTriggerEffect(
                    step = Step.UPKEEP,
                    timing = DelayedTriggerTiming.NEXT_TURN,
                    effect = Effects.DrawCards(1)
                ),
                CounterEffect()
            )
        )
    }

    metadata {
        rarity = Rarity.COMMON
        collectorNumber = "22b"
        artist = "Richard Kane Ferguson"
        ruling(
            "2007-09-16",
            "The controller of the countered spell chooses whether to draw zero, one, or two cards when the delayed ability resolves."
        )
    }
}
