package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.gym.actorinput.*
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/** Tie-break only already proposed, observationally equivalent copies of one deployment. */
internal object SphinxStageEEquivalentDeploymentRanking {
    fun choose(input: ActorInput, epoch: ActorEpoch, actor: EntityId,
               proposals: List<SphinxStageEAdapterResult.Proposed>): SphinxStageEAdapterResult {
        input.verifyBinding(epoch, actor)
        fun unavailable() = SphinxStageEAdapterResult.Unqualified(input.bindingHash,
            "Multiple accepted casts require ranking beyond equivalent deployment copies")
        if (proposals.size < 2 || input.decision != null || input.observation.stack.isNotEmpty())
            return unavailable()
        val casts = proposals.map { it.proposal.action as? CastSpell ?: return unavailable() }
        if (casts.map { it.cardId }.distinct().size != casts.size ||
            casts.any { it.playerId != actor || it.useAlternativeCost } ||
            proposals.any { it.proposal.inputBindingHash != input.bindingHash ||
                it.proposal.nextPolicyRngState != input.policyRngState }) return unavailable()
        val hand = input.observation.zones.singleOrNull { it.zoneType == Zone.HAND && it.ownerId == actor }
            ?: return unavailable()
        if (hand.cards.size != hand.size) return unavailable()
        val cards = casts.map { cast -> hand.cards.singleOrNull { it.entityId == cast.cardId }
            ?: return unavailable() }
        val anchor = casts.first()
        val firstCard = cards.first()
        if (firstCard.name !in setOf("Tolarian Terror", "Cryptic Serpent", "Goliath Sphinx") ||
            cards.any { it.ownerId != actor || it.copy(entityId = firstCard.entityId) != firstCard } ||
            casts.any { it.copy(cardId = anchor.cardId) != anchor }) return unavailable()
        val options = casts.map { cast -> input.legalActions.singleOrNull { it.action == cast && it.affordable }
            ?: return unavailable() }
        if (options.any { it.copy(action = anchor) != options.first().copy(action = anchor) })
            return unavailable()
        return proposals.minBy { (it.proposal.action as CastSpell).cardId.value }.copy(
            reason = "stable visible-entity tie-break between equivalent current deployment copies")
    }
}
