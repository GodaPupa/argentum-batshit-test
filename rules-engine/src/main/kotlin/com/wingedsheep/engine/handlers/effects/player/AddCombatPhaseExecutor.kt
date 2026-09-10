package com.wingedsheep.engine.handlers.effects.player

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.AdditionalPhasesComponent
import com.wingedsheep.engine.state.components.player.ExtraPhaseKind
import com.wingedsheep.engine.state.components.player.QueuedPhase
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.effects.AddCombatPhaseEffect
import kotlin.reflect.KClass

/**
 * Append a [kind] phase (optionally carrying an [attackerRestriction] for a COMBAT phase) to the
 * active player's [AdditionalPhasesComponent] queue (CR 500.8). The queue is drained by the
 * TurnManager after the postcombat main phase. Shared by [AddCombatPhaseExecutor] and
 * [AddMainPhaseExecutor] so the two atoms stay a single append.
 */
internal fun GameState.queueAdditionalPhase(
    player: EntityId,
    kind: ExtraPhaseKind,
    attackerRestriction: GameObjectFilter? = null
): GameState {
    val existing = getEntity(player)?.get<AdditionalPhasesComponent>()
    val newPhases = (existing?.phases ?: emptyList()) + QueuedPhase(kind, attackerRestriction)
    return updateEntity(player) { it.with(AdditionalPhasesComponent(newPhases)) }
}

/**
 * Executor for [AddCombatPhaseEffect] — "After this phase, there is an additional combat phase."
 *
 * Queues a single COMBAT phase on the active player; it is inserted after the postcombat main phase
 * and is NOT followed by an extra main phase (that requires composing with [AddMainPhaseEffect]).
 */
class AddCombatPhaseExecutor : EffectExecutor<AddCombatPhaseEffect> {

    override val effectType: KClass<AddCombatPhaseEffect> = AddCombatPhaseEffect::class

    override fun execute(
        state: GameState,
        effect: AddCombatPhaseEffect,
        context: EffectContext
    ): EffectResult {
        val activePlayer = state.activePlayerId
            ?: return EffectResult.error(state, "No active player for AddCombatPhaseEffect")
        return EffectResult.success(
            state.queueAdditionalPhase(activePlayer, ExtraPhaseKind.COMBAT, effect.attackerRestriction)
        )
    }
}
