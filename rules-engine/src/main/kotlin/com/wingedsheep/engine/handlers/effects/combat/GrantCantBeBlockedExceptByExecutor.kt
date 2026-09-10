package com.wingedsheep.engine.handlers.effects.combat

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.handlers.effects.TargetResolutionUtils
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.effects.GrantCantBeBlockedExceptByEffect
import kotlin.reflect.KClass

/**
 * Executor for [GrantCantBeBlockedExceptByEffect].
 *
 * Creates a Layer.ABILITY floating effect carrying [SerializableModification.CantBeBlockedExceptBy]
 * for the targeted creature. That modification maps to the real
 * [com.wingedsheep.engine.mechanics.layers.Modification.CantBeBlockedExceptBy], so the projector
 * adds the filter to `cantBeBlockedExceptByFilters` and the existing
 * [com.wingedsheep.engine.mechanics.combat.rules.CantBeBlockedExceptByRule] enforces it — no
 * combat-specific wiring needed. The floating-effect, one-shot counterpart to the static
 * `CantBeBlockedExceptBy` ability.
 *
 * For multi-target spells, wrap in ForEachTargetEffect with EffectTarget.ContextTarget(0).
 */
class GrantCantBeBlockedExceptByExecutor : EffectExecutor<GrantCantBeBlockedExceptByEffect> {

    override val effectType: KClass<GrantCantBeBlockedExceptByEffect> = GrantCantBeBlockedExceptByEffect::class

    override fun execute(
        state: GameState,
        effect: GrantCantBeBlockedExceptByEffect,
        context: EffectContext
    ): EffectResult {
        val entityId = TargetResolutionUtils.resolveTarget(effect.target, context)
            ?: return EffectResult.success(state)
        state.getEntity(entityId)?.get<CardComponent>()
            ?: return EffectResult.success(state)

        val newState = state.addFloatingEffect(
            layer = Layer.ABILITY,
            modification = SerializableModification.CantBeBlockedExceptBy(effect.blockerFilter),
            affectedEntities = setOf(entityId),
            duration = effect.duration,
            context = context
        )

        return EffectResult.success(newState)
    }
}
