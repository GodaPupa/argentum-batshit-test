package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.gym.actorinput.*
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/** Current identical Ponder or Preordain copies only; component draw/reserve checks run first. */
internal object SphinxStageEEquivalentSetupRanking {
    fun choose(input: ActorInput, epoch: ActorEpoch, actor: EntityId,
               proposals: List<SphinxStageEAdapterResult.Proposed>): SphinxStageEAdapterResult {
        input.verifyBinding(epoch, actor)
        fun unavailable() = SphinxStageEAdapterResult.Unqualified(input.bindingHash,
            "Setup tie-break requires equivalent current Ponder or Preordain copies on a basic-Island main-phase board")
        if (proposals.size < 2 || input.decision != null || input.observation.stack.isNotEmpty() ||
            input.observation.activePlayerId != actor || input.observation.priorityPlayerId != actor ||
            !input.observation.step.isMainPhase || input.observation.players.size != 2 ||
            input.observation.players.any { it.hasLost }) return unavailable()
        val board = input.observation.zones.filter { it.zoneType == Zone.BATTLEFIELD }
        if (board.any { it.cards.size != it.size } || board.flatMap { it.cards }
                .any { it.name !in setOf("Island", "Snow-Covered Island") }) return unavailable()
        val casts = proposals.map { it.proposal.action as? CastSpell ?: return unavailable() }
        if (casts.map { it.cardId }.distinct().size != casts.size || casts.any { it != CastSpell(actor, it.cardId) } ||
            proposals.any { it.proposal.inputBindingHash != input.bindingHash ||
                it.proposal.nextPolicyRngState != input.policyRngState }) return unavailable()
        val hand = input.observation.zones.singleOrNull { it.zoneType == Zone.HAND && it.ownerId == actor }
            ?: return unavailable()
        if (hand.size != hand.cards.size) return unavailable()
        val cards = casts.map { cast -> hand.cards.singleOrNull { it.entityId == cast.cardId } ?: return unavailable() }
        val first = cards.first()
        if (first.name !in setOf("Ponder", "Preordain") || cards.any {
                it.ownerId != actor || it.copy(entityId = first.entityId) != first }) return unavailable()
        val options = casts.map { cast -> input.legalActions.singleOrNull { it.action == cast && it.affordable }
            ?: return unavailable() }
        val anchor = casts.first()
        if (options.any { it.requiresTargets || it.hasXCost || it.additionalCostInfo != null ||
                it.basicBluePayment?.status != ActorBasicBluePaymentStatus.PLANNED ||
                it.copy(action = anchor) != options.first().copy(action = anchor) }) return unavailable()
        return proposals.minBy { (it.proposal.action as CastSpell).cardId.value }
            .copy(reason = "stable visible-entity tie-break between equivalent current setup copies")
    }
}
