package com.wingedsheep.engine.handlers.effects.player

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.event.GlobalGrantedTriggeredAbility
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.effects.CreateGlobalTriggeredAbilityEffect
import kotlin.reflect.KClass

/**
 * Executor for CreateGlobalTriggeredAbilityEffect.
 * Creates a global triggered ability (not attached to any specific permanent) lasting for the
 * effect's [duration] — until end of turn (False Cure), permanently (Dimensional Breach,
 * planeswalker emblems), or any other duration (Season of the Bold).
 */
class CreateGlobalTriggeredAbilityExecutor :
    EffectExecutor<CreateGlobalTriggeredAbilityEffect> {

    override val effectType: KClass<CreateGlobalTriggeredAbilityEffect> =
        CreateGlobalTriggeredAbilityEffect::class

    override fun execute(
        state: GameState,
        effect: CreateGlobalTriggeredAbilityEffect,
        context: EffectContext
    ): EffectResult {
        val sourceId = context.sourceId
            ?: return EffectResult.error(state, "No source for global triggered ability")

        val global = GlobalGrantedTriggeredAbility(
            ability = effect.ability,
            controllerId = context.controllerId,
            sourceId = sourceId,
            sourceName = state.getEntity(sourceId)
                ?.get<CardComponent>()?.name
                ?: "Unknown",
            duration = effect.duration,
            descriptionOverride = effect.descriptionOverride
        )

        val newState = state.copy(
            globalGrantedTriggeredAbilities = state.globalGrantedTriggeredAbilities + global
        )

        return EffectResult.success(newState)
    }
}
