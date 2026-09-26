package com.wingedsheep.engine.handlers.effects.permanent.protection

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.TargetingRestrictionCreatedEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.effects.PreventTargetingEffect
import com.wingedsheep.sdk.scripting.targets.EffectTarget
import kotlin.reflect.KClass

class PreventTargetingExecutor : EffectExecutor<PreventTargetingEffect> {
    override val effectType: KClass<PreventTargetingEffect> = PreventTargetingEffect::class

    override fun execute(state: GameState, effect: PreventTargetingEffect, context: EffectContext): EffectResult {
        val target = context.resolveTarget(effect.target, state) ?: return EffectResult.success(state)
        val entity = state.getEntity(target) ?: return EffectResult.success(state)
        val controllers = context.resolvePlayerTargets(EffectTarget.PlayerRef(effect.fromPlayers), state).toSet()
        if (controllers.isEmpty()) return EffectResult.success(state)
        val reference = if (entity.has<CardComponent>()) state.objectRef(target) else null
        val updated = state.addFloatingEffect(
            layer = Layer.ABILITY,
            modification = SerializableModification.PreventTargeting(controllers, reference),
            affectedEntities = setOf(target),
            duration = effect.duration,
            context = context
        )
        return EffectResult.success(updated, listOf(TargetingRestrictionCreatedEvent(target, controllers, context.sourceId)))
    }
}
