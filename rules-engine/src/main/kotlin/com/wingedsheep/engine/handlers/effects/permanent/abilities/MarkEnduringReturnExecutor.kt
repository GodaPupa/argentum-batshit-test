package com.wingedsheep.engine.handlers.effects.permanent.abilities

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.EnduringReturnComponent
import com.wingedsheep.sdk.scripting.effects.MarkEnduringReturnEffect
import kotlin.reflect.KClass

/**
 * Executor for [MarkEnduringReturnEffect].
 *
 * Stamps the source permanent with [EnduringReturnComponent], the marker the card's Enduring
 * type-changing static ability reads (via
 * [com.wingedsheep.sdk.scripting.conditions.SourceReturnedAsEnchantment]) to become an
 * enchantment with no other card types or subtypes. Composed after the return-to-battlefield
 * move in the synthesized Enduring trigger; a no-op if the source isn't on the battlefield
 * (e.g. the return move fizzled because the card had already left the graveyard).
 */
class MarkEnduringReturnExecutor : EffectExecutor<MarkEnduringReturnEffect> {

    override val effectType: KClass<MarkEnduringReturnEffect> = MarkEnduringReturnEffect::class

    override fun execute(
        state: GameState,
        effect: MarkEnduringReturnEffect,
        context: EffectContext
    ): EffectResult {
        val sourceId = context.sourceId ?: return EffectResult.success(state)
        // Only mark a source that is actually on the battlefield (return succeeded).
        if (sourceId !in state.getBattlefield()) return EffectResult.success(state)

        val newState = state.updateEntity(sourceId) { container ->
            container.with(EnduringReturnComponent)
        }
        return EffectResult.success(newState)
    }
}
