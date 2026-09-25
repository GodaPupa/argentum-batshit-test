package com.wingedsheep.engine.mechanics

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.PriorityChangedEvent
import com.wingedsheep.engine.event.PendingTrigger
import com.wingedsheep.engine.event.TriggerDetector
import com.wingedsheep.engine.event.TriggerProcessor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.PendingCastPriority
import com.wingedsheep.sdk.model.EntityId

/**
 * The ordinary post-cast priority boundary (CR 117.3c, 117.5, 704.3).
 * Costs and the cast have completed. Captured cast triggers wait through every SBA choice and
 * repeat pass before being placed. A cast made during stack resolution must not enter this path.
 * Existing events and decisions carry the whole interaction; no caller may synthesize a pass.
 */
class CastPriorityProcessor(
    private val sbaChecker: StateBasedActionChecker,
    private val triggerDetector: TriggerDetector,
    private val triggerProcessor: TriggerProcessor,
) {
    fun start(
        state: GameState,
        caster: EntityId,
        events: List<GameEvent>,
        triggers: List<PendingTrigger>,
    ): ExecutionResult {
        check(!state.stackResolutionPendingPriority)
        check(state.pendingCastPriority == null)
        return settle(state.copy(pendingCastPriority = PendingCastPriority(caster, triggers)), events)
    }

    /** Resume only this boundary, preserving already captured/processed event batches. */
    fun resume(result: ExecutionResult, precedingEvents: List<GameEvent>): ExecutionResult {
        if (result.error != null) return result
        val state = result.state
        val events = precedingEvents + result.events
        if (state.gameOver) return finishTerminal(state, events)
        check(!state.stackResolutionPendingPriority)
        val pending = requireNotNull(state.pendingCastPriority)
        val unprocessed = precedingEvents + if (result.triggersAlreadyProcessed) emptyList() else result.events
        val captured = state.copy(pendingCastPriority = pending.copy(
            triggers = pending.triggers + triggerDetector.detectTriggers(state, unprocessed)
        ))
        if (captured.pendingDecision != null) {
            // These events are captured durably; an enclosing resumer must not capture them again.
            return ExecutionResult.propagatePause(captured, events).copy(triggersAlreadyProcessed = true)
        }
        val completed = settle(captured, events)
        return if (completed.isSuccess && completed.state.pendingDecision == null && !completed.state.gameOver) {
            completed.copy(events = completed.events +
                listOfNotNull(completed.state.priorityPlayerId?.let(::PriorityChangedEvent)))
        } else completed
    }

    private fun settle(initial: GameState, precedingEvents: List<GameEvent>): ExecutionResult {
        var state = initial
        var events = precedingEvents
        while (true) {
            val pending = requireNotNull(state.pendingCastPriority)
            val sba = sbaChecker.checkAndApply(state, pending.triggers.mapNotNull { it.objectReferences.origin }.toSet())
            if (sba.error != null) return sba
            events = events + sba.events
            val triggers = pending.triggers + triggerDetector.detectTriggers(sba.state, sba.events)
            state = sba.state.copy(pendingCastPriority = pending.copy(triggers = triggers))
            if (state.gameOver) return finishTerminal(state, events)
            if (sba.isPaused) {
                return ExecutionResult.propagatePause(state, events).copy(triggersAlreadyProcessed = true)
            }
            if (triggers.isEmpty()) {
                return ExecutionResult.success(state.withPriorityAfterCasting(), events)
                    .copy(triggersAlreadyProcessed = true)
            }

            // Clear the captured batch before processing it. A target-selection pause retains
            // the recipient, while the ordinary trigger continuation owns the remaining batch.
            state = state.copy(pendingCastPriority = pending.copy(triggers = emptyList()))
            // Separate capture batches may each be APNAP-sorted but their concatenation is not.
            // Sort the complete waiting batch stably, retaining each controller's existing order.
            val apnap = state.apnapOrder.withIndex().associate { it.value to it.index }
            val ordered = triggers.sortedBy { apnap[it.controllerId] ?: Int.MAX_VALUE }
            val placed = triggerProcessor.processTriggers(state, ordered)
            if (placed.error != null) return placed
            events = events + placed.events
            state = placed.state.copy(pendingCastPriority = pending.copy(
                triggers = triggerDetector.detectTriggers(placed.state, placed.events)
            ))
            if (placed.isPaused) {
                return ExecutionResult.propagatePause(state, events).copy(triggersAlreadyProcessed = true)
            }
            // Placement can itself trigger another ability; repeat SBAs before that next batch.
        }
    }

    private fun finishTerminal(state: GameState, events: List<GameEvent>): ExecutionResult =
        ExecutionResult.success(
            state.copy(continuationStack = emptyList()).withPriorityAfterStackResolution(), events
        ).copy(triggersAlreadyProcessed = true)
}
