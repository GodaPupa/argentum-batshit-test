package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.DecisionPhase
import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.gym.actorinput.ActorChoiceSupport
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ActorProposal
import com.wingedsheep.gym.actorinput.ActorPublicCards
import com.wingedsheep.sdk.model.EntityId

/**
 * One accepted actor-visible Ponder reorder carried to its immediately following shuffle choice.
 * The trusted runner creates this only after the real engine accepted the recorded proposal,
 * journals that acceptance and discards this value after the shuffle response. Replay must verify
 * that transition; this component grants no runner, replay or gameplay admission.
 */
class SphinxStageEPonderMemory private constructor(
    val priorInputBindingHash: String,
    val priorDecisionId: String,
    val ownDeckSha256: String,
    val sourceId: EntityId,
    val actorId: EntityId,
    val priorEpoch: ActorEpoch,
    val orderedVisibleCards: List<EntityId>,
    val shuffle: Boolean,
    private val nextPolicyRngState: Long,
) {
    internal fun decide(input: ActorInput, epoch: ActorEpoch, actor: EntityId,
                        ownDeck: SphinxStageEOwnDeck): SphinxStageEAdapterResult {
        input.verifyBinding(epoch, actor)
        fun uncovered(reason: String) = SphinxStageEAdapterResult.Unqualified(input.bindingHash, reason)
        if (actor != actorId || ownDeck.sha256 != ownDeckSha256 ||
            epoch.sourceVersion != priorEpoch.sourceVersion || epoch.trialId != priorEpoch.trialId ||
            priorEpoch.step == Long.MAX_VALUE || epoch.step != priorEpoch.step + 1 ||
            input.policyRngState != nextPolicyRngState) {
            return uncovered("Ponder memory does not bind the immediately following own actor epoch")
        }
        val q = input.decision as? YesNoDecision
            ?: return uncovered("Ponder memory requires its next shuffle question")
        if (q.id == priorDecisionId || q.playerId != actor || q.context.phase != DecisionPhase.RESOLUTION ||
            q.context.sourceId != sourceId || q.context.sourceName != "Ponder" ||
            !q.prompt.contains("shuffle", ignoreCase = true)) {
            return uncovered("Ponder memory belongs to a different source or typed continuation")
        }
        val ownSource = input.observation.stack.any {
            it.view.entityId == sourceId && it.view.name == "Ponder" && it.spell?.ownerId == actor
        } || input.observation.zones.flatMap { it.cards }.any {
            it.entityId == sourceId && it.name == "Ponder" && it.ownerId == actor
        }
        if (!ownSource) return uncovered("Ponder's currently visible owned source is absent")
        return SphinxStageEAdapterResult.Proposed(
            ActorChoiceSupport.proposal(input, ActorChoiceSupport.submit(input, YesNoResponse(q.id, shuffle))),
            "remembered visible Ponder reorder: shuffle only when every offered card scored below30",
        )
    }

    companion object {
        internal fun afterAcceptedReorder(input: ActorInput, epoch: ActorEpoch, actor: EntityId,
                                          ownDeck: SphinxStageEOwnDeck,
                                          accepted: ActorProposal): SphinxStageEPonderMemory {
            input.verifyBinding(epoch, actor)
            val q = input.decision as? ReorderLibraryDecision
                ?: error("Accepted Ponder memory requires a reorder input")
            require(q.context.sourceName == "Ponder" && q.context.phase == DecisionPhase.RESOLUTION)
            val expected = SphinxStageEVisibleChoice.decide(input, epoch, actor, ownDeck)
                as? SphinxStageEAdapterResult.Proposed ?: error("The visible Ponder reorder is unqualified")
            require(accepted == expected.proposal) { "Accepted action differs from the bound visible reorder" }
            val action = accepted.action as? SubmitDecision ?: error("Expected a typed decision submission")
            val response = action.response as? OrderedResponse ?: error("Expected the ordered response")
            val cards = ActorPublicCards(input)
            val shuffle = q.cards.all { SphinxStageEVisibleChoice.score(q.cardInfo.getValue(it).name, cards) < 30 }
            return SphinxStageEPonderMemory(input.bindingHash, q.id, ownDeck.sha256,
                requireNotNull(q.context.sourceId), actor, epoch, response.orderedObjects.toList(),
                shuffle, accepted.nextPolicyRngState)
        }
    }
}
