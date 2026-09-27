package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.BottomCards
import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.engine.legalactions.EnumerationMode
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.gym.contract.ActionParameterizer
import com.wingedsheep.gym.contract.ActionParams
import com.wingedsheep.gym.contract.ObservationBuilder
import com.wingedsheep.gym.contract.ResolvedAction
import com.wingedsheep.gym.contract.StateDigest
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.sdk.model.EntityId

/** The policy sees its own information set and a prompt, never the private engine state. */
fun interface PhaseTwoPilotPolicy {
    fun choose(observation: TrainingObservation, prompt: PhaseTwoPilotPrompt): PhaseTwoPilotChoice
}

sealed interface PhaseTwoPilotPrompt {
    data class Mulligan(val canMulligan: Boolean) : PhaseTwoPilotPrompt
    data class Bottom(val count: Int) : PhaseTwoPilotPrompt
    data object Engine : PhaseTwoPilotPrompt
}

sealed interface PhaseTwoPilotChoice {
    data object Keep : PhaseTwoPilotChoice
    data object Mulligan : PhaseTwoPilotChoice
    data class Bottom(val cardIds: List<EntityId>) : PhaseTwoPilotChoice
    data class Action(val actionId: Int, val params: ActionParams = ActionParams.EMPTY) : PhaseTwoPilotChoice
    data class StructuredDecision(val response: DecisionResponse) : PhaseTwoPilotChoice
}

/**
 * Prospective public-information boundary for Phase 2 pilots. The trusted side alone holds the
 * exact engine state, enumerates legal actions, resolves opaque per-window IDs and submits each
 * action through [PhaseTwoTelemetryAdapter]. A policy may neither inspect the raw state/events nor
 * bypass the accepted-action trace. It must still be qualified for strategic competence, and its
 * exact code/registry/runner identity must be sealed before any official allocation.
 *
 * Opening hands are handled before ordinary priority enumeration: the generic legal-action
 * enumerator does not advertise KeepHand/TakeMulligan/BottomCards. Their acting seat is the first
 * outstanding seat in turn order, and the supplied hand view is masked for that seat. The complex
 * decision path accepts a typed response only for the decision's designated player.
 *
 * This is a dormant receiving component. It has no seed, claim, initialisation or game loop.
 */
class PhaseTwoPilotBoundary(
    private val adapter: PhaseTwoTelemetryAdapter,
    cardRegistry: CardRegistry,
    private val policies: Map<EntityId, PhaseTwoPilotPolicy>,
) {
    private val observationBuilder = ObservationBuilder(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)

    init {
        require(adapter.state.turnOrder.size == 4 && policies.keys == adapter.state.turnOrder.toSet()) {
            "Exactly one pilot per frozen four-player seat required"
        }
    }

    fun step(): ExecutionResult {
        val state = adapter.state
        check(!state.gameOver) { "Terminal game has no next pilot choice" }
        val mulliganActor = state.turnOrder.firstOrNull {
            state.requireEntity(it).get<MulliganStateComponent>()?.hasKept == false
        }
        val bottomActor = if (mulliganActor == null) state.turnOrder.firstOrNull {
            (state.requireEntity(it).get<MulliganStateComponent>()?.cardsToBottom ?: 0) > 0
        } else null
        val actor = mulliganActor ?: bottomActor ?: state.pendingDecision?.playerId
            ?: requireNotNull(state.priorityPlayerId) { "No actor in a live game" }
        require(actor in policies) { "Unbound pilot seat" }
        val prompt = when (actor) {
            mulliganActor -> PhaseTwoPilotPrompt.Mulligan(
                requireNotNull(state.requireEntity(actor).get<MulliganStateComponent>()).canMulligan)
            bottomActor -> PhaseTwoPilotPrompt.Bottom(
                requireNotNull(state.requireEntity(actor).get<MulliganStateComponent>()).cardsToBottom)
            else -> PhaseTwoPilotPrompt.Engine
        }
        val actions = if (prompt == PhaseTwoPilotPrompt.Engine && state.pendingDecision == null)
            enumerator.enumerate(state, actor, EnumerationMode.ACTIONS_ONLY) else emptyList()
        val built = observationBuilder.build(state, actor, actions, revealAll = false)
        val observed = built.observation as? TrainingObservation
            ?: error("Game policy requires a game observation")
        // During pregame, priority still points at the first player. Make the designated
        // mulligan/bottoming actor and its digest agree without adding private hand details.
        val observation = if (prompt != PhaseTwoPilotPrompt.Engine) {
            val acting = observed.copy(agentToAct = actor)
            acting.copy(stateDigest = StateDigest.compute(acting))
        } else observed
        check(observation.perspectivePlayerId == actor && observation.agentToAct == actor)

        val action = try {
            when (val choice = policies.getValue(actor).choose(observation, prompt)) {
                PhaseTwoPilotChoice.Keep -> {
                    require(prompt is PhaseTwoPilotPrompt.Mulligan)
                    KeepHand(actor)
                }
                PhaseTwoPilotChoice.Mulligan -> {
                    require(prompt is PhaseTwoPilotPrompt.Mulligan && prompt.canMulligan)
                    TakeMulligan(actor)
                }
                is PhaseTwoPilotChoice.Bottom -> {
                    require(prompt is PhaseTwoPilotPrompt.Bottom && choice.cardIds.size == prompt.count)
                    require(choice.cardIds.distinct().size == choice.cardIds.size &&
                        choice.cardIds.all { it in state.getHand(actor) })
                    BottomCards(actor, choice.cardIds)
                }
                is PhaseTwoPilotChoice.Action -> {
                    require(prompt == PhaseTwoPilotPrompt.Engine)
                    when (val resolved = built.registry.resolve(choice.actionId)) {
                        is ResolvedAction.Legal -> ActionParameterizer.apply(
                            resolved.action, choice.params, state).also { require(it.playerId == actor) }
                        is ResolvedAction.Decision -> {
                            require(choice.params.isEmpty && state.pendingDecision?.playerId == actor)
                            SubmitDecision(actor, resolved.response)
                        }
                        ResolvedAction.Unknown -> error("Unknown action ID for this observation")
                    }
                }
                is PhaseTwoPilotChoice.StructuredDecision -> {
                    require(prompt == PhaseTwoPilotPrompt.Engine &&
                        state.pendingDecision?.playerId == actor &&
                        state.pendingDecision?.id == choice.response.decisionId)
                    SubmitDecision(actor, choice.response)
                }
            }
        } catch (failure: Exception) {
            adapter.stop("INTEGRITY_FAILURE", "PILOT_CHOICE_EXCEPTION: ${failure::class.java.name}: ${failure.message}")
            throw failure
        }
        return adapter.process(action)
    }
}
