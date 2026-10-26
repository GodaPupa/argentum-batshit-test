package com.wingedsheep.engine.handlers.effects.stack

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.targeting.FixedRecipientChoice
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.scripting.effects.ChangeTargetToEffect
import kotlin.reflect.KClass

/** Resolve symbolic inputs once; the occurrence offer owns all subsequent identity checks. */
class ChangeTargetToExecutor : EffectExecutor<ChangeTargetToEffect> {
    override val effectType: KClass<ChangeTargetToEffect> = ChangeTargetToEffect::class

    override fun execute(state: GameState, effect: ChangeTargetToEffect, context: EffectContext): EffectResult {
        val current = context.withCurrentObjectReferences(state)
        val stack = current.resolveTarget(effect.stackObject, state) ?: return EffectResult.success(state)
        if (stack !in state.stack) return EffectResult.success(state)
        val id = current.resolveTarget(effect.recipient, state) ?: return EffectResult.success(state)
        val recipient = when {
            id in state.turnOrder -> ChosenTarget.Player(id)
            id in state.stack -> ChosenTarget.Spell(id)
            id in state.getBattlefield() -> ChosenTarget.Permanent(id)
            else -> {
                val zone = state.logicalZone(id) ?: return EffectResult.success(state)
                ChosenTarget.Card(id, zone.ownerId, zone.zoneType)
            }
        }
        return FixedRecipientChoice.start(state, stack, recipient, current.controllerId, current.sourceId)
    }
}
