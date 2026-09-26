package com.wingedsheep.engine.handlers.continuations

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState

class TriggerOrderingResumer(private val services: EngineServices) : ContinuationResumerModule {
    override fun resumers(): List<ContinuationResumer<*>> = listOf(
        resumer(TriggerOrderingContinuation::class, ::resumeOrder)
    )

    private fun resumeOrder(
        state: GameState,
        continuation: TriggerOrderingContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore,
    ): ExecutionResult {
        if (response !is OptionChosenResponse) return ExecutionResult.error(state, "Expected triggered-ability order choice")
        val selected = continuation.remaining.getOrNull(response.optionIndex)
            ?: return ExecutionResult.error(state, "Invalid triggered-ability order choice")
        val chosen = continuation.chosen + selected
        val remaining = continuation.remaining.filterIndexed { index, _ -> index != response.optionIndex }
        if (remaining.size > 1) return services.triggerProcessor.presentTriggerOrdering(state, chosen, remaining)
        val ordered = (chosen + remaining).map { it.copy(placementOrderChosen = true) }
        val result = services.triggerProcessor.processTriggers(state, ordered)
        if (result.isPaused || !result.isSuccess) return result
        return checkForMore(result.newState, result.events.toList())
    }
}
