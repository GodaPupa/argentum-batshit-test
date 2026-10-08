package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.gym.actorinput.*

/**
 * Trusted, in-memory, single typed Preordain resolution step. Reuses existing WholeActor choices.
 * No durable attempt lifecycle, generalized resolution coverage or gameplay admission is implied.
 * Construct only at the trusted boundary; no raw state reaches the pilot.
 */
internal class SphinxStageEPreordainBoundary(
    private val state: GameState,
    private val pilot: SphinxStageEInitializedSeat,
    private val epoch: ActorEpoch,
    private val policyRngState: Long,
    private val registry: CardRegistry,
) {
    private var consumed = false

    @Synchronized
    fun executeOnce(input: ActorInput, proposal: ActorProposal): ExecutionResult {
        check(!consumed) { "Preordain boundary consumed" }
        consumed = true
        input.verifyBinding(epoch, pilot.actorId)
        val question = requireNotNull(state.pendingDecision)
        require(question is SelectCardsDecision || question is ReorderLibraryDecision)
        require(question.playerId == pilot.actorId && question.context.sourceName == "Preordain" &&
            question.context.phase == DecisionPhase.RESOLUTION)
        val current = ObservationAdapter(registry).build(state, pilot.actorId,
            completeActorLegalActions(state, pilot.actorId, LegalActionEnumerator.create(registry)),
            epoch, policyRngState)
        require(current.canonicalJson() == input.canonicalJson()) { "Current trusted projection differs" }
        val expected = SphinxStageEWholeActor.decide(current, epoch, pilot)
            as? SphinxStageEAdapterResult.Proposed ?: error("Preordain choice is unqualified")
        require(proposal == expected.proposal && proposal.inputBindingHash == input.bindingHash) {
            "Proposal differs from current composed pilot choice"
        }
        val action = proposal.action as? SubmitDecision ?: error("Expected typed Preordain response")
        require(action.playerId == pilot.actorId && action.response.decisionId == question.id)
        val response = ActionProcessor(registry).process(state, action).result
        require(response.error == null) { "Engine rejected Preordain response: ${response.error}" }
        return response
    }
}
