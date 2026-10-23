package com.wingedsheep.engine.handlers.continuations

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.effects.PendingEntryChoices
import com.wingedsheep.engine.handlers.effects.PendingEntryCommitter
import com.wingedsheep.engine.state.GameState

/** Consumes a saved entry answer; the outer effect resumes only after the whole entry plan commits. */
class PendingEntryContinuationResumer(private val services: EngineServices) : ContinuationResumerModule {
    private val choices = PendingEntryChoices(services.cardRegistry)
    private val tokens = com.wingedsheep.engine.handlers.effects.token.PendingTokenEntries(
        services.cardRegistry, com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler(services.cardRegistry),
    )

    override fun resumers(): List<ContinuationResumer<*>> = listOf(
        resumer(PendingTokenChoiceContinuation::class) { state, continuation, response, checkForMore ->
            finish(tokens.answerChoice(state, continuation, response), checkForMore)
        },
        resumer(PendingTokenHostContinuation::class) { state, continuation, response, checkForMore ->
            finish(tokens.answerHost(state, continuation, response), checkForMore)
        },
        resumer(PendingTokenDefenderContinuation::class) { state, continuation, response, checkForMore ->
            finish(tokens.answerDefender(state, continuation, response), checkForMore)
        },
        resumer(PendingEntryCopyContinuation::class) { state, continuation, response, checkForMore ->
            finish(choices.answerCopy(state, continuation, response, ::commit), checkForMore)
        },
        resumer(PendingEntryChoiceContinuation::class) { state, continuation, response, checkForMore ->
            finish(choices.answerChoice(state, continuation, response, ::commit), checkForMore)
        },
    )

    private fun commit(state: GameState, plan: PendingZoneEntryPlan): EffectResult = PendingEntryCommitter.commit(
        state, plan, services.cardRegistry, services.targetFinder, services.effectExecutorRegistry::execute,
    )

    private fun finish(result: EffectResult, checkForMore: CheckForMore): ExecutionResult {
        val published = if (result.isSuccess) result.copy(
            state = exposeCollectionsToNextFrame(result.state, result.updatedCollections,
                result.updatedStoredNumbers, result.updatedChosenValues),
        ) else result
        return mergeAndContinue(published.toExecutionResult(), emptyList(), checkForMore)
    }
}
