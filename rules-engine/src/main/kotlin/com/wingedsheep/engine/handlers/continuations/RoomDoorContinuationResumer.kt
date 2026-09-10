package com.wingedsheep.engine.handlers.continuations

import com.wingedsheep.engine.core.ChooseDoorContinuation
import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.EngineServices
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.handlers.effects.permanent.room.RoomDoorResolution
import com.wingedsheep.engine.mechanics.layers.StaticAbilityHandler
import com.wingedsheep.engine.state.GameState

/**
 * Resumes a [ChooseDoorContinuation]: the controller has picked which door of a Room to lock or
 * unlock (CR 709.5f/g), after a resolution-time `LockDoorEffect` / `UnlockDoorEffect` found more
 * than one eligible door. Applies the chosen lock/unlock via [RoomDoorResolution] (re-baking the
 * affected half's continuous statics via [staticAbilityHandler], CR 709.5) and lets the normal
 * post-decision trigger pass ([com.wingedsheep.engine.handlers.actions.decision.SubmitDecisionHandler])
 * fire any "When you unlock this door" triggers off the returned events.
 */
class RoomDoorContinuationResumer(services: EngineServices) : ContinuationResumerModule {

    private val staticAbilityHandler = StaticAbilityHandler(services.cardRegistry)

    override fun resumers(): List<ContinuationResumer<*>> = listOf(
        resumer(ChooseDoorContinuation::class, ::resumeChooseDoor)
    )

    private fun resumeChooseDoor(
        state: GameState,
        continuation: ChooseDoorContinuation,
        response: DecisionResponse,
        checkForMore: CheckForMore,
    ): ExecutionResult {
        if (response !is OptionChosenResponse) {
            return ExecutionResult.error(state, "Expected option choice response for door selection")
        }
        val faceId = continuation.candidateFaceIds.getOrNull(response.optionIndex)
            ?: return ExecutionResult.error(state, "Invalid door index: ${response.optionIndex}")

        val (newState, events) = RoomDoorResolution.apply(
            state = state,
            roomId = continuation.roomId,
            faceId = faceId,
            controllerId = continuation.controllerId,
            lock = continuation.lock,
            staticAbilityHandler = staticAbilityHandler,
        )
        return checkForMore(newState, events)
    }
}
