package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.actorsEligibleForInput
import com.wingedsheep.gym.actorinput.completeActorLegalActions
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.model.EntityId

/**
 * Privileged projection seam for a future trusted Stage-E runner. The caller freezes seat order,
 * source/trial epoch and separate policy randomness. This object selects an eligible seat only;
 * it never picks a pilot action, mutates GameState, initializes a deck, or consumes a game.
 */
internal class SphinxStageEOpeningRouter(
    private val observation: ObservationAdapter,
    private val enumerator: LegalActionEnumerator,
) {
    fun projectCurrent(
        state: GameState,
        frozenSeatOrder: List<EntityId>,
        epoch: ActorEpoch,
        policyRngState: Long,
    ): ActorInput {
        require(frozenSeatOrder.size == 2 &&
            frozenSeatOrder.distinct().size == 2 &&
            frozenSeatOrder.toSet() == state.turnOrder.toSet()) {
            "The frozen two-seat order must match the actual initialized game"
        }
        val eligible = actorsEligibleForInput(state)
        // In the initial untap window, both seats can remain eligible without the priority
        // marker changing. Resolve the least-advanced unkept London round first; only after
        // both seats keep do their ordered bottom choices proceed. Seat order breaks ties.
        val setup = state.turnNumber == 1 && state.phase == Phase.BEGINNING &&
            state.step == Step.UNTAP && state.pendingDecision == null
        val actor = if (setup) {
            frozenSeatOrder.filter { it in eligible }.minWithOrNull(
                compareBy<EntityId> {
                    val mulligan = state.getEntity(it)?.get<MulliganStateComponent>()
                        ?: error("Opening seat lacks mulligan state")
                    if (mulligan.hasKept) 1 else 0
                }.thenBy {
                    val mulligan = state.getEntity(it)!!.get<MulliganStateComponent>()!!
                    if (mulligan.hasKept) 0 else mulligan.mulligansTaken
                }.thenBy { frozenSeatOrder.indexOf(it) }
            )
        } else frozenSeatOrder.firstOrNull { it in eligible }
        val currentActor = actor
            ?: throw IllegalStateException("No eligible actor may receive a current Stage-E input")
        val menu = completeActorLegalActions(state, currentActor, enumerator)
        return observation.build(state, currentActor, menu, epoch, policyRngState)
    }
}
