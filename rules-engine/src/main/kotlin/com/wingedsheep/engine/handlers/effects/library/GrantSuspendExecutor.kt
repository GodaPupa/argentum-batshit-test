package com.wingedsheep.engine.handlers.effects.library

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.mechanics.layers.Layer
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.layers.addFloatingEffect
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.SuspendedComponent
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.effects.GrantSuspendEffect
import kotlin.reflect.KClass

/**
 * Executor for [GrantSuspendEffect] — marks the target card as suspended (CR 702.62) and
 * pre-arms its haste.
 *
 * - The [SuspendedComponent] marker is what makes the engine grant the exile-side
 *   countdown-and-cast triggered ability (see [com.wingedsheep.sdk.scripting.Suspend]).
 * - CR 702.62g: a creature played via suspend "gains haste until you lose control of the spell
 *   or the permanent it becomes." A normal `GrantKeyword(HASTE)` can't be used here because the
 *   card is in exile (not a projected creature), so we add a self-sourced floating haste effect
 *   keyed to the card. It lies dormant while the card sits in exile and takes effect once the
 *   card is played onto the battlefield (the permanent reuses the same entity id). Harmless for
 *   a non-creature card, which never becomes a creature on the battlefield.
 *
 *   The duration is [Duration.WhileControlledByController], not `Permanent`: the projector drops
 *   the haste the instant the permanent's projected controller stops being the player who played
 *   it (the card's owner — only the owner ever plays a suspended card, per CR 702.62a), faithfully
 *   modeling "until you lose control of it." It's still cleared outright when the card leaves play.
 *
 * The marker step does not move the card or add time counters; the
 * [com.wingedsheep.sdk.dsl.Effects.Suspend] chain composes those from a move/counter step.
 */
class GrantSuspendExecutor : EffectExecutor<GrantSuspendEffect> {

    override val effectType: KClass<GrantSuspendEffect> = GrantSuspendEffect::class

    override fun execute(
        state: GameState,
        effect: GrantSuspendEffect,
        context: EffectContext
    ): EffectResult {
        val targetId = context.resolveTarget(effect.target, state)
            ?: return EffectResult.success(state)

        var newState = state.updateEntity(targetId) { container ->
            container.with(SuspendedComponent)
        }
        newState = newState.addFloatingEffect(
            layer = Layer.ABILITY,
            modification = SerializableModification.GrantKeyword(Keyword.HASTE.name),
            affectedEntities = setOf(targetId),
            // CR 702.62g — haste lasts "until you lose control" of the permanent it becomes.
            duration = Duration.WhileControlledByController,
            // Self-source the haste so it survives the granter (e.g. Taigam) leaving play
            // during the time the card waits in exile. context.controllerId — the player who
            // suspended/will play the card — is captured as the floating effect's controller
            // and becomes the gate the projector checks against.
            context = context.copy(sourceId = targetId),
        )
        return EffectResult.success(newState)
    }
}
