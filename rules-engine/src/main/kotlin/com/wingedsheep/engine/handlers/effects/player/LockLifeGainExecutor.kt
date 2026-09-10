package com.wingedsheep.engine.handlers.effects.player

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.CantGainLifeComponent
import com.wingedsheep.engine.state.components.player.PlayerEffectRemoval
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.effects.LockLifeGainEffect
import kotlin.reflect.KClass

/**
 * Resolves [LockLifeGainEffect] — tags each resolved player target with a
 * [CantGainLifeComponent] so their life gain is locked for the effect's duration.
 *
 * Only players (entities with a [LifeTotalComponent]) are affected; a non-player target — e.g. a
 * creature or planeswalker hit by a "deal damage to any target" rider — is silently skipped, so
 * the effect composes safely after such damage. The lock is idempotent at the most-durable level:
 * a Permanent lock is never downgraded by a later turn-scoped lock.
 */
class LockLifeGainExecutor : EffectExecutor<LockLifeGainEffect> {

    override val effectType: KClass<LockLifeGainEffect> = LockLifeGainEffect::class

    override fun execute(
        state: GameState,
        effect: LockLifeGainEffect,
        context: EffectContext
    ): EffectResult {
        val removeOn = when (effect.duration) {
            Duration.EndOfTurn -> PlayerEffectRemoval.EndOfTurn
            Duration.UntilYourNextTurn -> PlayerEffectRemoval.UntilYourNextTurn
            else -> PlayerEffectRemoval.Permanent
        }

        val targetIds = context.resolvePlayerTargets(effect.target, state)
            .filter { state.turnOrder.contains(it) }

        var newState = state
        for (playerId in targetIds) {
            val container = newState.getEntity(playerId) ?: continue
            // Only players have a life total to lock.
            if (container.get<LifeTotalComponent>() == null) continue
            val existing = container.get<CantGainLifeComponent>()
            // Never downgrade a Permanent lock to a shorter duration.
            if (existing?.removeOn == PlayerEffectRemoval.Permanent) continue
            newState = newState.updateEntity(playerId) { c ->
                c.with(CantGainLifeComponent(removeOn = removeOn))
            }
        }

        return EffectResult.success(newState)
    }
}
