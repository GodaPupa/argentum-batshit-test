package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.DecisionResponder
import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.LibraryOrderingComponent
import com.wingedsheep.engine.support.GameTestDriver
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId

/** One shared eligibility predicate for runtime observation and replay of the frozen checkpoint. */
internal fun GameState.isIndustrialWasteV2QuietCheckpoint(player: EntityId): Boolean =
    !gameOver && activePlayerId == player && step == Step.PRECOMBAT_MAIN && stack.isEmpty() &&
        pendingDecision == null && priorityPlayerId == player

/**
 * Complete T1-T8 capability composition for one already-initialized Industrial Waste v2 game.
 *
 * Deliberately has no corpus loader, no seed generator, no claim writer and no authorization path.
 * Its caller must provide a game that has already been initialized with an excluded deterministic
 * fixture. This lets the exact policy, decision responder, execution tracker, original-copy
 * telemetry and checkpoint classifier run together through the real engine without making any
 * official R1 allocation constructible from this class alone.
 */
internal class IndustrialWasteV2FullHorizonRunner(
    private val driver: GameTestDriver,
    private val measuredPlayer: EntityId,
    private val passivePlayer: EntityId,
    private val beforeSubmission: (GameAction) -> Unit = {},
    private val afterSubmission: (ExecutionResult) -> Unit = {},
    private val paymentIntentRecord: (IndustrialWasteV2PaymentIntentRecord) -> Unit = {},
    private val checkpointObservation: (IndustrialWasteV2CheckpointMana, Int, GameState) -> Unit = { _, _, _ -> },
    private val officialAdmission: IndustrialWasteV2OfficialAdmission? = null,
) {
    private val simulator = GameSimulator(driver.cardRegistry)
    private val responder = DecisionResponder(
        simulator,
        AIPlayer.defaultEvaluator(),
        CardAdvisorRegistry().also { IndustrialWasteV2PilotAdvisorModule.register(it) },
    )

    data class Result(
        val status: IndustrialWasteV2ExecutionStatus,
        val eventMetrics: IndustrialWasteV2EventMetrics,
        val checkpoints: List<IndustrialWasteV2CheckpointMana>,
        val actions: List<GameAction>,
        val finalState: GameState,
    )

    fun run(): Result {
        val ordering = requireNotNull(
            driver.state.getEntity(measuredPlayer)?.get<LibraryOrderingComponent>()
        ) { "Full-horizon capability requires an explicit excluded ordering fixture" }
        check(ordering.plan.namespace != "IW_V2_R1_ORDERINGS_2026_09_25" ||
            officialAdmission?.matches(ordering.plan) == true) {
            "Capability runner refuses the frozen official R1 ordering namespace"
        }

        val tracker = IndustrialWasteV2ExecutionTracker(driver.state, measuredPlayer)
        val collector = IndustrialWasteV2EventCollector(
            initial = driver.state,
            player = measuredPlayer,
            originalCopies = ordering.originalCopies,
            initialEvents = driver.events.toList(),
        )
        val checkpoints = linkedMapOf<Int, IndustrialWasteV2CheckpointMana>()
        val actions = mutableListOf<GameAction>()
        val paymentRecords = mutableListOf<IndustrialWasteV2PaymentIntentRecord>()
        val payments = IndustrialWasteV2PaymentBinder(driver.cardRegistry, paymentRecords::add)
        var lastObservedActions: Int? = null
        var lastObservedState: GameState? = null

        fun observeQuietState() {
            val state = driver.state
            if (!state.isIndustrialWasteV2QuietCheckpoint(measuredPlayer)) return
            val accepted = tracker.snapshot().acceptedActions
            val checkpoint = try {
                if (lastObservedActions == accepted) {
                    check(lastObservedState == state) { "Quiet state changed without an accepted action" }
                    return
                }
                IndustrialWasteV2CheckpointManaClassifier.classify(
                    state = state,
                    player = measuredPlayer,
                    originalCopies = ordering.originalCopies,
                    legalActions = simulator.getLegalActions(state, measuredPlayer),
                    cardRegistry = driver.cardRegistry,
                )
            } catch (failure: Exception) {
                tracker.markException("Quiet-checkpoint observer", failure)
                return
            }
            // Every eligible state is preserved, including the state returned by the final
            // permitted action. A cap stops further actions; it does not erase that observation.
            checkpointObservation(checkpoint, accepted, state)
            lastObservedActions = accepted
            lastObservedState = state
            checkpoints.putIfAbsent(checkpoint.ownTurn, checkpoint)
            if (checkpoint.unresolved) {
                tracker.markUnresolved(
                    "Quiet-precombat checkpoint ${checkpoint.ownTurn} after $accepted accepted actions contains an unresolved payment shape"
                )
            }
        }

        observeQuietState()
        while (tracker.snapshot().status == IndustrialWasteV2StopStatus.RUNNING) {
            val state = driver.state

            val decision = state.pendingDecision
            val action: GameAction? = try { if (decision != null) {
                SubmitDecision(
                    decision.playerId,
                    responder.respond(state, decision, decision.playerId),
                )
            } else {
                val priority = requireNotNull(state.priorityPlayerId) {
                    "Full-horizon runner has neither a decision nor priority"
                }
                val legal = simulator.getLegalActions(state, priority)
                if (priority == measuredPlayer) {
                    payments.choose(state, priority, legal)
                } else {
                    check(priority == passivePlayer) {
                        "Unexpected player at two-seat full-horizon capability table"
                    }
                    IndustrialWasteV2PublicActionPolicy.choosePassive(state, priority, legal)
                }
            } } catch (failure: Exception) {
                tracker.markException("Action selection", failure)
                null
            }
            // All selected payment intent rows are forced before any submission, including when
            // selection failed. Writer failures are deliberately outside the runtime catch.
            paymentRecords.forEach(paymentIntentRecord)
            paymentRecords.clear()
            if (action == null) break

            actions += action
            val before = driver.state
            beforeSubmission(action)
            val result = tracker.submit(action) { expectedBefore, submitted ->
                check(expectedBefore == driver.state) {
                    "Tracker and real driver state diverged before submission"
                }
                driver.submit(submitted)
            }
            if (result != null) afterSubmission(result)
            if (result == null || result.error != null) break

            try {
                collector.record(before, action, result)
                check(tracker.state == driver.state) {
                    "Tracker and real driver state diverged after submission"
                }
            } catch (failure: Exception) {
                tracker.markException("Accepted-transition observer", failure)
                break
            }
            // This must precede the next RUNNING guard, so an eligible action-cap/terminal
            // state is still classified and an unresolved result can invalidate a provisional cap.
            observeQuietState()
        }

        val metrics = collector.snapshot()
        if (actions.size != tracker.snapshot().submittedActions) {
            tracker.markUnresolved("Full-horizon transcript/action counter mismatch")
        }
        if (metrics.acceptedTransitions != tracker.snapshot().acceptedActions) {
            tracker.markUnresolved("Telemetry/action acceptance counts diverged; retain partial raw metrics")
        }

        return Result(
            status = tracker.snapshot(),
            eventMetrics = metrics,
            checkpoints = checkpoints.values.toList(),
            actions = actions.toList(),
            finalState = tracker.state,
        )
    }
}
