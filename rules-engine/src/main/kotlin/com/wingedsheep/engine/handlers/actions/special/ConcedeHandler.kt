package com.wingedsheep.engine.handlers.actions.special

import com.wingedsheep.engine.core.Concede
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.GameEndReason
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.PlayerLostEvent
import com.wingedsheep.engine.handlers.actions.ActionHandler
import com.wingedsheep.engine.mechanics.StateBasedActionChecker
import com.wingedsheep.engine.mechanics.CastPriorityProcessor
import com.wingedsheep.engine.mechanics.combat.BlockDeclarationProcessor
import com.wingedsheep.engine.mechanics.combat.CombatDefenders
import com.wingedsheep.engine.mechanics.sba.game.GameEndCheck
import com.wingedsheep.engine.mechanics.sba.player.PlayerLeavesGameCheck
import com.wingedsheep.engine.mechanics.sba.player.TeamLossPropagationCheck
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.LossReason
import com.wingedsheep.engine.state.components.player.PlayerLostComponent
import kotlin.reflect.KClass

/**
 * Handler for the Concede action.
 *
 * Conceding is always legal (CR 104.3a — a player can concede at any time). It marks the
 * player as having lost; the state-based action loop then does the rest:
 * [com.wingedsheep.engine.mechanics.sba.player.PlayerLeavesGameCheck] removes the
 * conceding player's objects (CR 800.4a) and [com.wingedsheep.engine.mechanics.sba.game.GameEndCheck]
 * ends the game only when one player remains. So in a two-player game conceding ends the
 * game immediately, while in a multiplayer pod it continues for the others.
 */
class ConcedeHandler(
    private val sbaChecker: StateBasedActionChecker,
    private val castPriorityProcessor: CastPriorityProcessor? = null,
    private val blockDeclarationProcessor: BlockDeclarationProcessor? = null,
) : ActionHandler<Concede> {
    override val actionType: KClass<Concede> = Concede::class

    override fun validate(state: GameState, action: Concede): String? = null

    override fun execute(state: GameState, action: Concede): ExecutionResult {
        // Idempotent: a player already out of the game can't concede again.
        if (state.getEntity(action.playerId)?.has<PlayerLostComponent>() == true) {
            return ExecutionResult.success(state)
        }

        val marked = state.updateEntity(action.playerId) { container ->
            container.with(PlayerLostComponent(LossReason.CONCESSION))
        }
        val lostEvent = PlayerLostEvent(action.playerId, GameEndReason.CONCESSION)

        if (CombatDefenders.nextUndeclaredDefender(state) != null) {
            val processor = blockDeclarationProcessor
                ?: return ExecutionResult.error(state, "Block-declaration service is required for this pending concession")
            // Departure is immediate; other SBAs and trigger placement still wait for the whole
            // declaration round. Existing cancellation removes only the departed chooser's pause.
            val departure = applyImmediateDeparture(marked)
            return processor.complete(departure.copy(
                state = processor.preserveDepartedSources(state, departure.state),
            ), listOf(lostEvent))
        }

        if (marked.pendingCastPriority != null && !marked.stackResolutionPendingPriority) {
            val processor = castPriorityProcessor
                ?: return ExecutionResult.error(state, "Post-cast priority service is required for this pending concession")
            // A commander/SBA question can already be pending. CR 800.4 departure processing
            // happens immediately; running the full SBA list first would ask that question again
            // before its late leave-game check could remove the departing chooser. Reuse the
            // existing decision-free team/departure/end checks without changing global SBA order.
            return processor.resume(applyImmediateDeparture(marked), listOf(lostEvent))
        }
        val sbaResult = sbaChecker.checkAndApply(marked)
        // A chooser may have left while simultaneous triggers were being ordered after a
        // resolution. Departure retains the other controllers' exact waiting batch in the
        // existing boundary; finish it before handing out priority.
        if (!sbaResult.state.gameOver && sbaResult.state.pendingCastPriority != null &&
            !sbaResult.state.stackResolutionPendingPriority) {
            val processor = castPriorityProcessor
                ?: return ExecutionResult.error(state, "Priority service is required for surviving triggered abilities")
            return processor.resume(sbaResult, listOf(lostEvent))
        }
        if (sbaResult.isPaused) {
            return ExecutionResult.propagatePause(
                sbaResult.state,
                listOf(lostEvent) + sbaResult.events
            )
        }
        return ExecutionResult.success(
            sbaResult.newState,
            listOf(lostEvent) + sbaResult.events
        )
    }

    private fun applyImmediateDeparture(state: GameState): ExecutionResult {
        val teamLoss = TeamLossPropagationCheck().check(state)
        var current = teamLoss.state
        val events = mutableListOf<GameEvent>().apply { addAll(teamLoss.events) }
        val leaveCheck = PlayerLeavesGameCheck()
        while (true) {
            val departure = leaveCheck.check(current)
            current = departure.state
            events.addAll(departure.events)
            // Each nonempty departure marks one player as processed. This also preserves the
            // existing no-teardown behavior when at most one team remains and the game ends.
            if (departure.events.isEmpty()) break
        }
        val end = GameEndCheck().check(current)
        return end.copy(events = events + end.events)
    }
}
