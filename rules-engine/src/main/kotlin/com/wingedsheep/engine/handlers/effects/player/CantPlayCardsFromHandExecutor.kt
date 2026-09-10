package com.wingedsheep.engine.handlers.effects.player

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.PlayerCantPlayFromHandComponent
import com.wingedsheep.engine.state.components.player.PlayerEffectRemoval
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.effects.CantPlayCardsFromHandEffect
import kotlin.reflect.KClass

/**
 * Executor for [CantPlayCardsFromHandEffect] — stamps a
 * [PlayerCantPlayFromHandComponent] on the target player for the requested duration
 * (Memory Vessel's "they can't play cards from their hand").
 *
 * The restriction is read at legal-action enumeration and re-checked in the cast/play
 * handlers; it only blocks plays from the hand zone, so cards granted a may-play
 * permission elsewhere (the impulse-exiled cards Memory Vessel grants) stay playable.
 */
class CantPlayCardsFromHandExecutor : EffectExecutor<CantPlayCardsFromHandEffect> {

    override val effectType: KClass<CantPlayCardsFromHandEffect> = CantPlayCardsFromHandEffect::class

    override fun execute(
        state: GameState,
        effect: CantPlayCardsFromHandEffect,
        context: EffectContext
    ): EffectResult {
        val targetId = context.resolveTarget(effect.target)
            ?: return EffectResult.error(state, "No valid target for can't-play-from-hand grant")

        if (!state.turnOrder.contains(targetId)) {
            return EffectResult.error(state, "CantPlayCardsFromHand target must be a player")
        }

        val removeOn = when (effect.duration) {
            is Duration.Permanent -> PlayerEffectRemoval.Permanent
            is Duration.EndOfTurn -> PlayerEffectRemoval.EndOfTurn
            else -> PlayerEffectRemoval.UntilYourNextTurn
        }

        // "Until your next turn" keys off the *activating* player, not the player the restriction
        // sits on — so an opponent's restriction lasts until the activating player's next turn,
        // not the opponent's own (Memory Vessel). The activating player is the effect controller,
        // which survives per-player iteration (context.controllerId is rebound to each player).
        // Only meaningful when target != activating player; null otherwise (the component's own
        // owner is used).
        val activatingPlayer = context.effectControllerId ?: context.controllerId
        val expiresForPlayerId =
            if (removeOn == PlayerEffectRemoval.UntilYourNextTurn && activatingPlayer != targetId)
                activatingPlayer else null

        val newState = state.updateEntity(targetId) { container ->
            container.with(PlayerCantPlayFromHandComponent(removeOn = removeOn, expiresForPlayerId = expiresForPlayerId))
        }
        return EffectResult.success(newState)
    }
}
