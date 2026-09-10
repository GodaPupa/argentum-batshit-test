package com.wingedsheep.engine.handlers.effects.combat

import com.wingedsheep.engine.core.CreatureGoadedEvent
import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.combat.GoadedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.sdk.scripting.effects.GoadEffect
import kotlin.reflect.KClass

/**
 * Executor for [GoadEffect] (CR 701.15).
 *
 * Adds the effect's controller to the target creature's [GoadedComponent] goader set.
 * Re-goading by an already-recorded goader is a no-op (CR 701.15d) since the set
 * deduplicates. The component is later cleared by
 * [com.wingedsheep.engine.core.CleanupPhaseManager.expireGoadedDesignationFor],
 * called after the untap step of each goader's next turn (CR 701.15a) — same hook
 * as `Duration.UntilYourNextTurn` floating effects.
 *
 * The target must still be on the battlefield at resolution; otherwise the effect
 * fizzles. The "must be a creature" check is left to the target requirement at
 * targeting time — if the target stopped being a creature mid-resolution, applying
 * the goaded designation is still legal (the designation is not an ability and
 * does nothing until the permanent becomes a creature again or attacks).
 */
class GoadExecutor : EffectExecutor<GoadEffect> {

    override val effectType: KClass<GoadEffect> = GoadEffect::class

    override fun execute(
        state: GameState,
        effect: GoadEffect,
        context: EffectContext
    ): EffectResult {
        val targetId = context.resolveTarget(effect.target)
            ?: return EffectResult.success(state)

        if (targetId !in state.getBattlefield()) {
            return EffectResult.success(state)
        }

        val goaderId = context.controllerId
        val existing = state.getEntity(targetId)?.get<GoadedComponent>()
        if (existing != null && goaderId in existing.goaderIds) {
            // CR 701.15d: same player goading again has no effect.
            return EffectResult.success(state)
        }

        val newGoaders = (existing?.goaderIds ?: emptySet()) + goaderId
        val newState = state.updateEntity(targetId) { it.with(GoadedComponent(newGoaders)) }

        val creatureName = state.getEntity(targetId)?.get<CardComponent>()?.name ?: "Creature"
        val goaderName = state.getEntity(goaderId)?.get<PlayerComponent>()?.name ?: "Player"
        val events = listOf<GameEvent>(
            CreatureGoadedEvent(
                creatureId = targetId,
                creatureName = creatureName,
                goaderId = goaderId,
                goaderName = goaderName
            )
        )
        return EffectResult.success(newState, events)
    }
}
