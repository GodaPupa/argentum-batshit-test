package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId

/**
 * Trusted input eligibility, not a pilot or a turn scheduler.
 *
 * The initializer leaves setup at turn one, beginning/untap. KeepHand and BottomCards update
 * their owner's mulligan state without handing the priority marker to another seat. During
 * that opening window, every still-available setup choice belongs to its own player. A pending
 * typed decision always takes precedence. The caller's separately frozen seat/round order is
 * preserved: this function exposes eligible actors and never selects a response or actor.
 */
internal fun actorsEligibleForInput(state: GameState): List<EntityId> {
    if (state.gameOver) return emptyList()
    state.pendingDecision?.let { question ->
        return listOf(question.playerId).filter { it in state.turnOrder }
    }

    if (state.turnNumber == 1 && state.phase == Phase.BEGINNING && state.step == Step.UNTAP) {
        val setupActors = state.turnOrder.filter { player ->
            val mulligan = state.getEntity(player)?.get<MulliganStateComponent>()
            mulligan != null && (!mulligan.hasKept || mulligan.cardsToBottom > 0)
        }
        if (setupActors.isNotEmpty()) return setupActors
    }

    return listOfNotNull(state.priorityPlayerId).filter { it in state.turnOrder }
}
