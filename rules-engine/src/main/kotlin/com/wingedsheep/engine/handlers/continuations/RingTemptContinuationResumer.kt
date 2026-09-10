package com.wingedsheep.engine.handlers.continuations

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.RingTemptContinuation
import com.wingedsheep.engine.core.RingTemptedEvent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.RingBearerComponent
import com.wingedsheep.sdk.model.EntityId

/**
 * Resumes "the Ring tempts you" after the tempted player picks their Ring-bearer (CR 701.54a).
 *
 * The Ring-bearer designation is unique per owner, so this moves it: any creature currently
 * carrying [RingBearerComponent] for the tempted player loses it, and the chosen creature gains it.
 * Then [RingTemptedEvent] announces the temptation so "Whenever the Ring tempts you" triggers fire.
 */
class RingTemptContinuationResumer(
    @Suppress("unused") private val services: com.wingedsheep.engine.core.EngineServices
) : ContinuationResumerModule {

    override fun resumers(): List<ContinuationResumer<*>> = listOf(
        resumer(RingTemptContinuation::class, ::resumeRingTempt)
    )

    private fun resumeRingTempt(
        state: GameState,
        continuation: RingTemptContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore
    ): ExecutionResult {
        if (response !is CardsSelectedResponse) {
            return ExecutionResult.error(state, "Expected a creature selection for 'the Ring tempts you'")
        }

        val chosen: EntityId? = response.selectedCards.firstOrNull { it in continuation.candidates }

        var newState = clearRingBearer(state, continuation.temptedPlayerId)
        if (chosen != null) {
            newState = newState.updateEntity(chosen) { container ->
                container.with(RingBearerComponent(ownerId = continuation.temptedPlayerId))
            }
        }

        val events = listOf<GameEvent>(
            RingTemptedEvent(
                playerId = continuation.temptedPlayerId,
                temptCount = continuation.temptCount,
                bearerId = chosen,
                sourceName = continuation.sourceName
            )
        )

        return checkForMore(newState, events)
    }

    /** Remove the Ring-bearer designation from [ownerId]'s current bearer, if any. */
    private fun clearRingBearer(state: GameState, ownerId: EntityId): GameState {
        var newState = state
        for (id in state.getBattlefield()) {
            val bearer = state.getEntity(id)?.get<RingBearerComponent>() ?: continue
            if (bearer.ownerId == ownerId) {
                newState = newState.updateEntity(id) { it.without<RingBearerComponent>() }
            }
        }
        return newState
    }
}
