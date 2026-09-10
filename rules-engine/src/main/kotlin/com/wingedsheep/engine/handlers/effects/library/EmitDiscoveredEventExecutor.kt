package com.wingedsheep.engine.handlers.effects.library

import com.wingedsheep.engine.core.DiscoveredEvent
import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.scripting.effects.EmitDiscoveredEventEffect
import kotlin.reflect.KClass

/**
 * Tail of a discover ([com.wingedsheep.sdk.scripting.effects.DiscoverEffect]): emits a
 * [DiscoveredEvent] so "Whenever you discover" triggers (CR 701.57) fire exactly once per discover,
 * after the whole process — including the cast/hand decision — has resolved (CR 701.57b).
 *
 * The discover executor bundles this into the discover's follow-up so it runs from a *completed*
 * resolution rather than a paused batch (a game event emitted while paused for the may-cast
 * decision does not reliably fire watcher triggers). The carried [EmitDiscoveredEventEffect.value]
 * is the discover threshold N, evaluated once at discover time and surfaced via
 * `ContextPropertyKey.TRIGGER_DISCOVER_VALUE`.
 */
class EmitDiscoveredEventExecutor : EffectExecutor<EmitDiscoveredEventEffect> {

    override val effectType: KClass<EmitDiscoveredEventEffect> = EmitDiscoveredEventEffect::class

    override fun execute(
        state: GameState,
        effect: EmitDiscoveredEventEffect,
        context: EffectContext
    ): EffectResult {
        val sourceName = context.sourceId
            ?.let { state.getEntity(it)?.get<CardComponent>()?.name }
            ?: "Discover"

        return EffectResult.success(
            state,
            listOf(
                DiscoveredEvent(
                    playerId = context.controllerId,
                    value = effect.value,
                    sourceName = sourceName
                )
            )
        )
    }
}
