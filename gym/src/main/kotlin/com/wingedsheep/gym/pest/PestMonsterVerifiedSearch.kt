package com.wingedsheep.gym.pest

import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ActorLegalAction
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.UnsupportedPolicyInput
import com.wingedsheep.gym.actorinput.verifiedAuthorizedLibrarySearchOrder
import com.wingedsheep.sdk.model.EntityId

/**
 * Trusted receiving boundary for a single current Map/Crop library search. The engine state is
 * used only here to check the shared typed search proof and build a detached actor input. The
 * pilot receives the resulting input and this opaque same-question witness, never GameState,
 * continuation data, unoffered library cards or a library index.
 */
internal class PestMonsterVerifiedSearch private constructor(
    val input: ActorInput,
    private val questionId: String,
    private val sourceId: EntityId,
    private val offeredHandles: List<EntityId>,
) {
    internal fun matches(other: ActorInput, question: SelectCardsDecision): Boolean =
        input.bindingHash == other.bindingHash && input.actorId == other.actorId &&
            question.id == questionId && question.context.sourceId == sourceId &&
            question.options == offeredHandles

    companion object {
        internal fun project(
            adapter: ObservationAdapter,
            state: GameState,
            actor: EntityId,
            legalActions: List<ActorLegalAction>,
            epoch: ActorEpoch,
            policyRngState: Long,
        ): PestMonsterVerifiedSearch {
            val raw = state.pendingDecision as? SelectCardsDecision
                ?: throw UnsupportedPolicyInput("No current typed library search")
            if (!verifiedAuthorizedLibrarySearchOrder(state, actor, raw)) {
                throw UnsupportedPolicyInput("Current question has no valid authorized library search proof")
            }
            val source = raw.context.sourceId
                ?: throw UnsupportedPolicyInput("Current search lacks a bound source")
            val input = adapter.build(state, actor, legalActions, epoch, policyRngState)
            input.verifyBinding(epoch, actor)
            val question = input.decision as? SelectCardsDecision
                ?: throw UnsupportedPolicyInput("Projected search changed question type")
            if (question.id != raw.id || question.context.sourceId != source ||
                question.options != raw.options || question.cardInfo?.keys != raw.options.toSet()) {
                throw UnsupportedPolicyInput("Projected search differs from the proof-bound offer")
            }
            return PestMonsterVerifiedSearch(input, question.id, source, question.options)
        }
    }
}
