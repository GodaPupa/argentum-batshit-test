package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.DecisionResponder
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.sdk.model.EntityId

internal data class IndustrialWasteV2CapabilityTranscript(
    val status: IndustrialWasteV2ExecutionStatus,
    val actions: List<GameAction>,
)

/**
 * Seed-free capability composition for the frozen R1 pilot pieces.
 *
 * This deliberately stops after one measured own turn and never reads the frozen R1 ordering
 * corpus. It proves that the exact public action policy, decision responder and fail-closed
 * execution tracker can drive the real processor together without a fallback action.
 * It is not the authorized 512-allocation runner.
 */
internal class IndustrialWasteV2CapabilityRunner(
    private val driver: GameTestDriver,
    private val measuredPlayer: EntityId,
    private val passivePlayer: EntityId,
) {
    private val simulator = GameSimulator(driver.cardRegistry)
    private val responder = DecisionResponder(
        simulator,
        AIPlayer.defaultEvaluator(),
        CardAdvisorRegistry().also { IndustrialWasteV2PilotAdvisorModule.register(it) },
    )

    fun runOneMeasuredTurn(maxSubmittedActions: Int = 800): IndustrialWasteV2ExecutionStatus =
        runOneMeasuredTurnWithTranscript(maxSubmittedActions).status

    /**
     * Capability-only transcript surface. The returned actions are exactly the real actions
     * submitted to the engine, in order, including passes and decision responses. No action is
     * synthesized or retried. A caller may replay them only against an identically initialized
     * deterministic fixture; this does not bind or consume any official R1 ordering row.
     */
    fun runOneMeasuredTurnWithTranscript(maxSubmittedActions: Int = 800): IndustrialWasteV2CapabilityTranscript {
        val tracker = IndustrialWasteV2ExecutionTracker(driver.state, measuredPlayer)
        val actions = mutableListOf<GameAction>()
        while (
            tracker.snapshot().status == IndustrialWasteV2StopStatus.RUNNING &&
            tracker.snapshot().ownTurnsCompleted < 1
        ) {
            check(tracker.snapshot().submittedActions < maxSubmittedActions) {
                "Seed-free pilot capability exceeded its local action guard"
            }
            val state = driver.state
            val decision = state.pendingDecision
            val action: GameAction = if (decision != null) {
                SubmitDecision(
                    decision.playerId,
                    responder.respond(state, decision, decision.playerId),
                )
            } else {
                val priority = requireNotNull(state.priorityPlayerId) {
                    "Capability runner has neither a decision nor priority"
                }
                val legal = simulator.getLegalActions(state, priority)
                if (priority == measuredPlayer) {
                    IndustrialWasteV2PublicActionPolicy.choose(state, priority, legal)
                } else {
                    check(priority == passivePlayer) { "Unexpected player at two-seat capability table" }
                    IndustrialWasteV2PublicActionPolicy.choosePassive(state, priority, legal)
                }
            }
            actions += action
            val result = tracker.submit(action) { before, submitted ->
                check(before == driver.state) { "Tracker and real driver state diverged before submission" }
                driver.submit(submitted)
            }
            if (result == null || result.error != null) break
            check(tracker.state == driver.state) { "Tracker and real driver state diverged after submission" }
        }
        val status = tracker.snapshot()
        check(actions.size == status.submittedActions) {
            "Capability transcript/action counter mismatch: ${actions.size} vs ${status.submittedActions}"
        }
        return IndustrialWasteV2CapabilityTranscript(status, actions.toList())
    }
}
