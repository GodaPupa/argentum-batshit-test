package com.wingedsheep.gym.pest

import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.gym.actorinput.ActorChoiceSupport
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ActorProposal
import com.wingedsheep.gym.actorinput.UnsupportedPolicyInput
import com.wingedsheep.sdk.model.EntityId

/** A decision-only seam for the two frozen seats; no runner or gameplay authority is attached. */
fun interface PestMonsterPairDecisionPilot {
    /** Null means the policy has no qualified answer. It is never an implicit pass or default. */
    fun respond(input: ActorInput): ActorProposal?
}

/**
 * Both pilots receive the same detached actor contract. This source-only seam rejects a missing
 * answer, mismatched actor/epoch and a decision answer outside the typed current question.
 * London, priority actions, combat and the complete pair policies still require receiving work.
 */
class PestMonsterPairDecisionBoundary(
    private val expectedEpoch: ActorEpoch,
    private val pestPlayer: EntityId,
    private val monsterPlayer: EntityId,
    private val pestPilot: PestMonsterPairDecisionPilot,
    private val monsterPilot: PestMonsterPairDecisionPilot,
) {
    init {
        require(pestPlayer != monsterPlayer)
    }

    fun respond(input: ActorInput): ActorProposal {
        val pilot = when (input.actorId) {
            pestPlayer -> pestPilot
            monsterPlayer -> monsterPilot
            else -> throw UnsupportedPolicyInput("Actor is outside the frozen Pest/Monster pair")
        }
        input.verifyBinding(expectedEpoch, input.actorId)
        val question = input.decision ?: throw UnsupportedPolicyInput("Pair decision seam has no pending question")
        val proposal = pilot.respond(input)
            ?: throw UnsupportedPolicyInput("Pair pilot has no qualified response to this question")
        require(proposal.inputBindingHash == input.bindingHash) { "Proposal has a different actor input" }
        val action = proposal.action as? SubmitDecision
            ?: throw UnsupportedPolicyInput("Pair decision pilot did not return SubmitDecision")
        require(action.playerId == input.actorId && action.response.decisionId == question.id)
        require(ActorChoiceSupport.submit(input, action.response) == action) {
            "Pair answer differs from the typed current question"
        }
        return proposal
    }
}
