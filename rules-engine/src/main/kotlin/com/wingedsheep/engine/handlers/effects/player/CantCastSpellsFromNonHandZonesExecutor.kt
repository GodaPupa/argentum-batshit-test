package com.wingedsheep.engine.handlers.effects.player

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.CantCastFromNonHandZonesComponent
import com.wingedsheep.engine.state.components.player.PlayerEffectRemoval
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.effects.CantCastSpellsFromNonHandZonesEffect
import kotlin.reflect.KClass

/**
 * Executor for [CantCastSpellsFromNonHandZonesEffect] — stamps a
 * [CantCastFromNonHandZonesComponent] on each targeted player for the requested duration
 * (Avatar's Wrath's "your opponents can't cast spells from anywhere other than their hands").
 *
 * The restriction is read at legal-action enumeration (the non-hand CastFromZoneEnumerator) and
 * re-checked authoritatively in CastSpellHandler; it only blocks casts from non-hand zones, so
 * ordinary hand casts still resolve. Mirrors [CantPlayCardsFromHandExecutor], including the
 * "until your next turn" window keying off the *casting* player, not the restricted opponent.
 */
class CantCastSpellsFromNonHandZonesExecutor : EffectExecutor<CantCastSpellsFromNonHandZonesEffect> {

    override val effectType: KClass<CantCastSpellsFromNonHandZonesEffect> =
        CantCastSpellsFromNonHandZonesEffect::class

    override fun execute(
        state: GameState,
        effect: CantCastSpellsFromNonHandZonesEffect,
        context: EffectContext
    ): EffectResult {
        val targetIds = context.resolvePlayerTargets(effect.target, state)
            .filter { state.turnOrder.contains(it) }
        if (targetIds.isEmpty()) {
            return EffectResult.error(state, "No valid target for can't-cast-from-non-hand-zones effect")
        }

        val removeOn = when (effect.duration) {
            is Duration.Permanent -> PlayerEffectRemoval.Permanent
            is Duration.EndOfTurn -> PlayerEffectRemoval.EndOfTurn
            else -> PlayerEffectRemoval.UntilYourNextTurn
        }

        // "Until your next turn" keys off the *casting* player, not the restricted opponent —
        // so an opponent's restriction lasts until the caster's next turn (Avatar's Wrath), not
        // the opponent's own. The casting player is the effect controller, which survives the
        // per-player iteration of context.controllerId. Null when target == casting player.
        val castingPlayer = context.effectControllerId ?: context.controllerId

        val newState = targetIds.fold(state) { acc, targetId ->
            val expiresForPlayerId =
                if (removeOn == PlayerEffectRemoval.UntilYourNextTurn && castingPlayer != targetId)
                    castingPlayer else null
            acc.updateEntity(targetId) { container ->
                container.with(
                    CantCastFromNonHandZonesComponent(
                        removeOn = removeOn,
                        expiresForPlayerId = expiresForPlayerId
                    )
                )
            }
        }

        return EffectResult.success(newState)
    }
}
