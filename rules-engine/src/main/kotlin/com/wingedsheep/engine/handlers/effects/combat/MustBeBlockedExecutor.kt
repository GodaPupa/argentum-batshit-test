package com.wingedsheep.engine.handlers.effects.combat

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.effects.MustBeBlockedEffect
import kotlin.reflect.KClass

/**
 * Executor for MustBeBlockedEffect.
 * "All creatures able to block target creature this turn do so."
 *
 * This creates a floating effect that marks the target creature as needing to be
 * blocked by all creatures that can block it. The CombatManager checks for this
 * restriction during declare blockers validation.
 */
class MustBeBlockedExecutor : EffectExecutor<MustBeBlockedEffect> {

    override val effectType: KClass<MustBeBlockedEffect> = MustBeBlockedEffect::class

    override fun execute(
        state: GameState,
        effect: MustBeBlockedEffect,
        context: EffectContext
    ): EffectResult {
        // Resolve the target creature
        val targetId = context.resolveTarget(effect.target)
            ?: return EffectResult.error(state, "No valid target for must-be-blocked effect")

        // Verify target exists and is a creature. Must use the projection, not the base type
        // line: the target may only be a creature via a continuous effect — e.g. Disturbed
        // Slumber animates a land with BecomeCreature immediately before this effect.
        if (state.getEntity(targetId) == null) {
            return EffectResult.error(state, "Target creature no longer exists")
        }
        if (!state.projectedState.isCreature(targetId)) {
            return EffectResult.error(state, "Target is not a creature")
        }

        // Create a floating effect marking this creature as "must be blocked"
        val modification = if (effect.allCreatures) {
            SerializableModification.MustBeBlockedByAll
        } else {
            SerializableModification.MustBeBlockedIfAble
        }

        val newState = state.addFloatingEffect(
            layer = Layer.ABILITY,
            modification = modification,
            affectedEntities = setOf(targetId),
            duration = Duration.EndOfTurn,
            context = context
        )

        return EffectResult.success(newState)
    }
}
