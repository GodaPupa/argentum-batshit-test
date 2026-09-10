package com.wingedsheep.engine.handlers.effects.permanent.types

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.scripting.effects.AddColorEffect
import kotlin.reflect.KClass

/**
 * Executor for [AddColorEffect].
 * "It becomes black in addition to its other colors." — adds one or more colors to a single
 * target via a Layer 5 (COLOR) additive floating effect, keeping the target's existing colors.
 */
class AddColorExecutor : EffectExecutor<AddColorEffect> {

    override val effectType: KClass<AddColorEffect> = AddColorEffect::class

    override fun execute(
        state: GameState,
        effect: AddColorEffect,
        context: EffectContext
    ): EffectResult {
        val targetId = context.resolveTarget(effect.target)
            ?: return EffectResult.success(state)

        if (targetId !in state.getBattlefield()) {
            return EffectResult.success(state)
        }

        val newState = state.addFloatingEffect(
            layer = Layer.COLOR,
            modification = SerializableModification.AddColor(effect.colors),
            affectedEntities = setOf(targetId),
            duration = effect.duration,
            context = context
        )

        return EffectResult.success(newState)
    }
}
