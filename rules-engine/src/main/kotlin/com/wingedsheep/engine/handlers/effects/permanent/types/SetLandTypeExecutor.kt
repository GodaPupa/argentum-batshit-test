package com.wingedsheep.engine.handlers.effects.permanent.types

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.scripting.effects.SetLandTypeEffect
import kotlin.reflect.KClass

/**
 * Executor for [SetLandTypeEffect].
 * Sets a target land's basic land subtype via a Layer 4 floating effect, replacing
 * all existing land subtypes (Rule 305.7). Unlike [AddSubtypeExecutor], the land
 * loses its old land types and the mana abilities they granted.
 */
class SetLandTypeExecutor : EffectExecutor<SetLandTypeEffect> {

    override val effectType: KClass<SetLandTypeEffect> = SetLandTypeEffect::class

    override fun execute(
        state: GameState,
        effect: SetLandTypeEffect,
        context: EffectContext
    ): EffectResult {
        val targetId = context.resolveTarget(effect.target)
            ?: return EffectResult.success(state)

        if (targetId !in state.getBattlefield()) {
            return EffectResult.success(state)
        }

        val landType = if (effect.fromChosenValueKey != null) {
            context.pipeline.chosenValues[effect.fromChosenValueKey]
                ?: return EffectResult.success(state)
        } else {
            effect.landType
        }

        val newState = state.addFloatingEffect(
            layer = Layer.TYPE,
            modification = SerializableModification.SetBasicLandTypes(setOf(landType)),
            affectedEntities = setOf(targetId),
            duration = effect.duration,
            context = context
        )

        return EffectResult.success(newState)
    }
}
