package com.wingedsheep.engine.handlers.effects.permanent.control

import com.wingedsheep.engine.core.ControlChangedEvent
import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.sdk.scripting.effects.GainControlByActivePlayerEffect
import kotlin.reflect.KClass

/**
 * Executor for GainControlByActivePlayerEffect.
 *
 * Gives control of target permanent to the active player (whose turn it is),
 * rather than to the ability's controller.
 *
 * Used by Risky Move: "At the beginning of each player's upkeep, that player
 * gains control of Risky Move."
 */
class GainControlByActivePlayerExecutor : EffectExecutor<GainControlByActivePlayerEffect> {

    override val effectType: KClass<GainControlByActivePlayerEffect> = GainControlByActivePlayerEffect::class

    override fun execute(
        state: GameState,
        effect: GainControlByActivePlayerEffect,
        context: EffectContext
    ): EffectResult {
        val targetId = context.resolveTarget(effect.target)
            ?: return EffectResult.error(state, "No valid target for control change")

        val targetContainer = state.getEntity(targetId)
            ?: return EffectResult.error(state, "Target permanent no longer exists")

        val cardComponent = targetContainer.get<CardComponent>()
            ?: return EffectResult.error(state, "Target is not a card")

        val newControllerId = state.activePlayerId
            ?: return EffectResult.error(state, "No active player")

        // Use projected controller so floating-effect-based control changes are respected
        val currentControllerId = state.projectedState.getController(targetId)
            ?: targetContainer.get<ControllerComponent>()?.playerId
        if (currentControllerId == newControllerId) return EffectResult.success(state)

        // Remove any previous Layer.CONTROL floating effects from the same source on the same target
        val filteredEffects = state.floatingEffects.filter { floating ->
            !(floating.sourceId == context.sourceId &&
              floating.effect.layer == Layer.CONTROL &&
              targetId in floating.effect.affectedEntities)
        }

        // Create new floating effect — use controllerId override since control goes to active player
        val controlContext = context.copy(controllerId = newControllerId)
        // Rule 302.6: new controller hasn't had this permanent since their most recent turn began.
        val newState = state.copy(floatingEffects = filteredEffects)
            .addFloatingEffect(
                layer = Layer.CONTROL,
                modification = SerializableModification.ChangeController(newControllerId),
                affectedEntities = setOf(targetId),
                duration = com.wingedsheep.sdk.scripting.Duration.Permanent,
                context = controlContext
            )
            .updateEntity(targetId) { it.with(SummoningSicknessComponent) }
            .let { clearRingBearerOnControlChange(it, targetId, newControllerId) }

        val events = listOf(
            ControlChangedEvent(
                permanentId = targetId,
                permanentName = cardComponent.name,
                oldControllerId = currentControllerId ?: context.controllerId,
                newControllerId = newControllerId
            )
        )

        return EffectResult.success(newState, events)
    }
}
