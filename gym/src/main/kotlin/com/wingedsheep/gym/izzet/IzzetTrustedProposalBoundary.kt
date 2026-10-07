package com.wingedsheep.gym.izzet

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.gym.actorinput.*
import kotlinx.serialization.json.Json

/**
 * Trusted-owner integration for four parameter-free action schemas only. The pilot receives only
 * pilotMenu and returns an opaque numbered proposal; it never supplies state, projection or offers.
 * All other affordable action schemas and all typed decisions fail closed in this slice.
 * No state transition, RNG access, strategy, attempt reservation or opponent admission occurs here.
 * Pins are compared exactly but must be authenticated independently by the owning runner.
 */
internal class IzzetTrustedProposalBoundary private constructor(
    private val registry: CardRegistry,
    private val window: IzzetProposalWindow,
) {
    private var consumed = false
    fun pilotMenu(): IzzetPilotMenu = window.pilotMenu()

    /** Trusted owner supplies its current state and epoch, never values echoed by a pilot. */
    @Synchronized
    fun resolve(proposal: IzzetNumberedProposal, currentState: GameState,
                currentPins: IzzetSourcePins, attemptId: String, sequence: Long): GameAction {
        check(!consumed) { "Trusted proposal boundary consumed" }
        consumed = true // Projection/materialization failures consume the outer boundary too.
        val current = project(registry, currentState, currentPins, attemptId, sequence)
        val canonical = window.resolve(proposal, currentPins, current.epoch,
            current.observation, current.offers)
        return JSON.decodeFromString(GameAction.serializer(), canonical)
    }

    companion object {
        private val JSON = Json { encodeDefaults = true; explicitNulls = true }
        private data class Projection(val epoch: IzzetDecisionEpoch, val observation: String,
                                      val offers: List<IzzetMaterializedOffer>)

        fun open(registry: CardRegistry, state: GameState, pins: IzzetSourcePins,
                 attemptId: String, sequence: Long): IzzetTrustedProposalBoundary {
            val current = project(registry, state, pins, attemptId, sequence)
            return IzzetTrustedProposalBoundary(registry, IzzetProposalWindow.open(
                pins, current.epoch, current.observation, current.offers))
        }

        private fun project(registry: CardRegistry, state: GameState, pins: IzzetSourcePins,
                            attemptId: String, sequence: Long): Projection {
            require(state.pendingDecision == null) { "Typed decisions require a separately qualified materializer" }
            val actor = requireNotNull(state.priorityPlayerId)
            val epoch = IzzetDecisionEpoch(attemptId, sequence, actor.value, actor.value, null)
            val input = ObservationAdapter(registry).build(state, actor,
                completeActorLegalActions(state, actor, LegalActionEnumerator.create(registry)),
                ActorEpoch(pins.sourceCommit, attemptId, sequence), 0L)
            input.verifyBinding(ActorEpoch(pins.sourceCommit, attemptId, sequence), actor)
            val offers = input.legalActions.filter { it.affordable }.mapIndexed { index, option ->
                val kind = when (option.action) {
                    is KeepHand -> "KeepHand"
                    is TakeMulligan -> "TakeMulligan"
                    is PlayLand -> "PlayLand"
                    is PassPriority -> "PassPriority"
                    else -> error("Unsupported current action materialization; no fallback")
                }
                val action = ActorChoiceSupport.materialize(input, option)
                require(action == option.action && action.playerId == actor)
                IzzetMaterializedOffer(index, kind, null, null,
                    JSON.encodeToString(GameAction.serializer(), action))
            }
            return Projection(epoch, input.canonicalJson(), offers)
        }
    }
}
