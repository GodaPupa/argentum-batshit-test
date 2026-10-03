package com.wingedsheep.engine.handlers.continuations

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.effects.DestructionReplacements

class DestructionContinuationResumer(private val services: EngineServices) : ContinuationResumerModule, AutoResumerModule {
    override fun resumers(): List<ContinuationResumer<*>> = listOf(
        resumer(DestructionReplacementContinuation::class) { state, frame, response, checkForMore ->
            if (response !is OptionChosenResponse || response.optionIndex !in frame.options.indices) {
                ExecutionResult.error(state, "Expected a valid destruction replacement choice")
            } else {
                val result = DestructionReplacements.apply(state, frame.permanent, frame.options[response.optionIndex])
                if (result.isPaused || result.error != null) result.toExecutionResult()
                else checkForMore(result.state, result.events)
            }
        }
    )
    override fun autoResumers(): List<AutoResumer<*>> = listOf(
        autoResumer(LethalDestructionContinuation::class) { state, frame, events, checkForMore ->
            val result = com.wingedsheep.engine.mechanics.sba.creature.LethalDamageCheck()
                .destroyRemaining(state, frame.remaining, frame.passStartState)
            if (result.isPaused || result.error != null) result.copy(events = events + result.events)
            else checkForMore(result.state, events + result.events)
        },
        autoResumer(DestroyCollectionContinuation::class) { state, frame, events, checkForMore ->
            val result = com.wingedsheep.engine.handlers.effects.library.MoveCollectionExecutor(services.cardRegistry)
                .destroyCollection(state, frame.effect, frame.context, frame.cards, frame.remaining, frame.attempted)
            if (result.isPaused || result.error != null) result.toExecutionResult().copy(events = events + result.events)
            else checkForMore(exposeCollectionsToNextFrame(result.state, result.updatedCollections), events + result.events)
        }
    )
}
