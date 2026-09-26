package com.wingedsheep.engine.support

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.core.Suspension
import com.wingedsheep.engine.core.TriggerOrderingContinuation
import com.wingedsheep.engine.state.GameState

/** True only for the engine's explicit simultaneous-trigger placement question. */
fun GameState.hasPendingTriggerOrder(): Boolean =
    pendingDecision is ChooseOptionDecision &&
        (peekContinuation() as? Suspension)?.answer is TriggerOrderingContinuation

/**
 * A fixture's explicit selection of the offered trigger order. This keeps its pre-existing
 * listed order while exercising real controller decisions. It neither passes priority nor
 * answers targeting, optional effects, modes, or any other ChooseOptionDecision.
 */
fun GameTestDriver.chooseTriggerOrderInListedOrder() {
    check(state.hasPendingTriggerOrder()) { "Expected a triggered-ability ordering question" }
    while (state.hasPendingTriggerOrder()) {
        val before = state
        val decision = state.pendingDecision as ChooseOptionDecision
        val result = submitDecision(decision.playerId, OptionChosenResponse(decision.id, 0))
        check(result.error == null) { "Trigger order was rejected: ${result.error}" }
        check(state != before) { "Accepted trigger-order choice made no progress" }
    }
}

/** Explicit counterpart for scenario fixtures; the ordinary resolveStack helper stays unchanged. */
fun ScenarioTestBase.TestGame.chooseTriggerOrderInListedOrder() {
    check(state.hasPendingTriggerOrder()) { "Expected a triggered-ability ordering question" }
    while (state.hasPendingTriggerOrder()) {
        val before = state
        val decision = state.pendingDecision as ChooseOptionDecision
        val result = submitDecision(OptionChosenResponse(decision.id, 0))
        check(result.error == null) { "Trigger order was rejected: ${result.error}" }
        check(state != before) { "Accepted trigger-order choice made no progress" }
    }
}
