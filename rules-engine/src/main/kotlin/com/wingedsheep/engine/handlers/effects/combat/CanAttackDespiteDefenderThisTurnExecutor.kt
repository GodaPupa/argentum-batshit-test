package com.wingedsheep.engine.handlers.effects.combat

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.combat.CanAttackDespiteDefenderThisTurnComponent
import com.wingedsheep.sdk.scripting.effects.CanAttackDespiteDefenderThisTurnEffect
import kotlin.reflect.KClass

/**
 * Executor for [CanAttackDespiteDefenderThisTurnEffect].
 *
 * Adds [CanAttackDespiteDefenderThisTurnComponent] to the target creature, letting it
 * attack this turn as though it didn't have defender. The marker is honored by the
 * defender attack-restriction rule and removed at end of turn during cleanup.
 */
class CanAttackDespiteDefenderThisTurnExecutor : EffectExecutor<CanAttackDespiteDefenderThisTurnEffect> {

    override val effectType: KClass<CanAttackDespiteDefenderThisTurnEffect> = CanAttackDespiteDefenderThisTurnEffect::class

    override fun execute(
        state: GameState,
        effect: CanAttackDespiteDefenderThisTurnEffect,
        context: EffectContext
    ): EffectResult {
        val targetId = context.resolveTarget(effect.target)
            ?: return EffectResult.success(state)

        if (targetId !in state.getBattlefield()) {
            return EffectResult.success(state)
        }

        val newState = state.updateEntity(targetId) { it.with(CanAttackDespiteDefenderThisTurnComponent) }
        return EffectResult.success(newState)
    }
}
