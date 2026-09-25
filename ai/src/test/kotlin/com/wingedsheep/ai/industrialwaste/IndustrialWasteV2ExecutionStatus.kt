package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameEndedEvent
import com.wingedsheep.engine.core.TurnChangedEvent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.PlayerTurnsTakenComponent
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

@Serializable
internal enum class IndustrialWasteV2StopStatus {
    RUNNING, REAL_TERMINAL, DRAW, TURN_CAP, ACTION_CAP,
    REJECTED_ACTION, EXCEPTION, UNRESOLVED_TELEMETRY,
}

/** No validity flag or complete decision-rule metrics: those require the remaining R1 gates. */
@Serializable
internal data class IndustrialWasteV2ExecutionStatus(
    val status: IndustrialWasteV2StopStatus,
    val submittedActions: Int,
    val acceptedActions: Int,
    val ownTurnsStarted: Int,
    val ownTurnsCompleted: Int,
    val actionCapReached: Boolean,
    val turnCapReached: Boolean,
    val engineGameOver: Boolean,
    val engineWinnerId: String?,
    val engineEndReason: String?,
    val pendingDecisionAtStop: Boolean,
    val stackDepthAtStop: Int,
    val diagnostic: String?,
)

/**
 * Prospective bounded submission/status component, not an authorized R1 runner. It owns the
 * current state and invokes the supplied real executor exactly once per submitted GameAction.
 * All players' actions, including passes and decision/mulligan responses, count toward 4000;
 * initialization and internal engine events do not. A rejected or throwing attempt still counts.
 * No fallback, stack draining, extra decision response or synthetic winner follows a stop.
 *
 * Own turn eight is allowed to finish, including its cleanup decisions. Its actual transition
 * to the next turn ends the horizon; a global turn number is never divided by the player count.
 * This class does not classify affordability or infer missing metric flags from a capped state.
 */
internal class IndustrialWasteV2ExecutionTracker(initial: GameState, private val player: EntityId) {
    var state: GameState = initial
        private set
    private var submitted = 0
    private var accepted = 0
    private var completedOwnTurns = 0
    private var fault: IndustrialWasteV2StopStatus? = null
    private var diagnostic: String? = null
    private var endReason: String? = null

    init {
        require(initial.turnOrder.size == 2 && player in initial.turnOrder)
        require(!initial.gameOver && initial.winnerId == null)
        // Only fresh initialization or excluded T1 scenario fixtures. No unaudited resume counts.
        require(initial.ownTurns() in 0..1 && (initial.ownTurns() == 0 || initial.activePlayerId == player))
    }

    fun submit(action: GameAction, execute: (GameState, GameAction) -> ExecutionResult): ExecutionResult? {
        check(snapshot().status == IndustrialWasteV2StopStatus.RUNNING) { "R1 status already stopped; no additional action is permitted" }
        val before = state
        submitted++
        val result = try {
            execute(before, action)
        } catch (failure: Throwable) {
            fault = IndustrialWasteV2StopStatus.EXCEPTION
            diagnostic = "${failure::class.simpleName}: ${failure.message}"
            return null
        }
        if (result.error != null) {
            fault = IndustrialWasteV2StopStatus.REJECTED_ACTION
            diagnostic = result.error
            // Keep the last accepted state, even if a malformed error wrapper contains changes.
            return result
        }
        accepted++
        state = result.newState
        val turnEvents = result.events.filterIsInstance<TurnChangedEvent>()
        if (state.ownTurns() != before.ownTurns() + turnEvents.count { it.activePlayerId == player } ||
            turnEvents.zipWithNext().any { (left, right) -> right.turnNumber <= left.turnNumber } ||
            turnEvents.firstOrNull()?.let { it.turnNumber <= before.turnNumber } == true) {
            markUnresolved("Own-turn counter and ordered public turn events disagree")
        }
        if (state.turnNumber != before.turnNumber) {
            if (turnEvents.lastOrNull()?.let { it.turnNumber == state.turnNumber && it.activePlayerId == state.activePlayerId } != true ||
                state.turnNumber <= before.turnNumber) {
                markUnresolved("Turn transition lacks matching ordered engine event")
            } else if (before.activePlayerId == player) {
                completedOwnTurns = before.ownTurns()
            }
        } else if (turnEvents.isNotEmpty() || state.activePlayerId != before.activePlayerId) {
            markUnresolved("Turn-change event or active player did not match the public turn")
        }
        if (state.ownTurns() > OWN_TURN_CAP || completedOwnTurns > OWN_TURN_CAP) {
            markUnresolved("Own-turn horizon was overrun")
        }
        val endEvents = result.events.filterIsInstance<GameEndedEvent>()
        if (state.gameOver) {
            val ended = endEvents.singleOrNull()
            if (ended == null || ended.winnerId != state.winnerId ||
                state.winnerId?.let { it !in before.turnOrder } == true) {
                markUnresolved("Terminal state lacks a consistent engine end event")
            } else endReason = ended.reason.name
        } else if (state.winnerId != null || endEvents.isNotEmpty()) {
            markUnresolved("Engine terminal event and public state disagree")
        }
        return result
    }

    /** May invalidate a provisional cap/terminal classification when another observer cannot resolve telemetry. */
    fun markUnresolved(reason: String) {
        require(reason.isNotBlank())
        if (fault == null) {
            fault = IndustrialWasteV2StopStatus.UNRESOLVED_TELEMETRY
            diagnostic = reason
        }
    }

    fun snapshot(): IndustrialWasteV2ExecutionStatus {
        val actionCap = submitted >= SUBMITTED_ACTION_CAP
        val turnCap = completedOwnTurns >= OWN_TURN_CAP
        return IndustrialWasteV2ExecutionStatus(
            classify(fault, state.gameOver, state.winnerId != null, actionCap, turnCap),
            submitted, accepted, state.ownTurns(), completedOwnTurns, actionCap, turnCap,
            state.gameOver, state.winnerId?.toString(), endReason,
            state.pendingDecision != null, state.stack.size, diagnostic,
        )
    }

    private fun GameState.ownTurns() = requireNotNull(getEntity(player)?.get<PlayerTurnsTakenComponent>()).count

    companion object {
        const val OWN_TURN_CAP = 8
        const val SUBMITTED_ACTION_CAP = 4000

        /** Pure precedence core also exercised at synthetic simultaneous-boundary fixtures. */
        internal fun classify(
            fault: IndustrialWasteV2StopStatus?, gameOver: Boolean, hasWinner: Boolean,
            actionCapReached: Boolean, turnCapReached: Boolean,
        ): IndustrialWasteV2StopStatus {
            require(fault == null || fault in setOf(IndustrialWasteV2StopStatus.REJECTED_ACTION,
                IndustrialWasteV2StopStatus.EXCEPTION, IndustrialWasteV2StopStatus.UNRESOLVED_TELEMETRY))
            return fault ?: when {
                gameOver && hasWinner -> IndustrialWasteV2StopStatus.REAL_TERMINAL
                gameOver -> IndustrialWasteV2StopStatus.DRAW
                actionCapReached -> IndustrialWasteV2StopStatus.ACTION_CAP
                turnCapReached -> IndustrialWasteV2StopStatus.TURN_CAP
                else -> IndustrialWasteV2StopStatus.RUNNING
            }
        }
    }
}
