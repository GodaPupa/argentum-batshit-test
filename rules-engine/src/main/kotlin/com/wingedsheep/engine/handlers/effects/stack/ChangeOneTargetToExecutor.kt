package com.wingedsheep.engine.handlers.effects.stack

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.targeting.FixedDestinationRetarget
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TargetsComponent
import com.wingedsheep.sdk.scripting.effects.ChangeOneTargetToEffect
import kotlin.reflect.KClass

class ChangeOneTargetToExecutor : EffectExecutor<ChangeOneTargetToEffect> {
    override val effectType: KClass<ChangeOneTargetToEffect> = ChangeOneTargetToEffect::class
    override fun execute(state: GameState, effect: ChangeOneTargetToEffect, context: EffectContext): EffectResult {
        val stackId = context.resolveTarget(effect.stackObject, state) ?: return EffectResult.success(state)
        val destinationId = context.resolveTarget(effect.destination, state) ?: return EffectResult.success(state)
        if (destinationId !in state.getBattlefield()) return EffectResult.success(state)
        val destination = ChosenTarget.Permanent(destinationId)
        val slots = FixedDestinationRetarget.legalSlots(state, stackId, destination)
        if (slots.isEmpty()) return EffectResult.success(state)
        if (slots.size == 1) return FixedDestinationRetarget.replaceSlot(state, stackId, slots.single(), destination)
        val stackRef = state.objectRef(stackId) ?: return EffectResult.success(state)
        val destinationRef = state.objectRef(destinationId) ?: return EffectResult.success(state)
        val previous = state.getEntity(stackId)!!.get<TargetsComponent>()!!
        return EffectResult.from(state.suspendForDecision({ id -> ChooseOptionDecision(
            id = id, playerId = context.controllerId, prompt = "Choose which target to change",
            context = DecisionContext(sourceId = context.sourceId, phase = DecisionPhase.RESOLUTION),
            options = slots.map { "Target ${it + 1}" }
        ) }, FixedDestinationRetargetContinuation(stackRef, destinationRef, slots, previous.targets, context.objectReferences)))
    }
}
