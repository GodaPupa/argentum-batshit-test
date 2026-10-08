package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.gym.actorinput.*
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/**
 * Bounded deterministic policy: on basic-Island-only public boards, prefer the already proposed
 * Terror/Serpent cast spending less canonical mana, then greater visible power, then name/ID.
 * This is an explicit policy choice, not a claim of optimal strategy or general multicasting.
 */
internal object SphinxStageETerrorSerpentRanking {
    fun choose(input: ActorInput, epoch: ActorEpoch, actor: EntityId,
               proposals: List<SphinxStageEAdapterResult.Proposed>): SphinxStageEAdapterResult {
        input.verifyBinding(epoch, actor)
        fun unavailable() = SphinxStageEAdapterResult.Unqualified(input.bindingHash,
            "Cross-name ranking requires current Terror/Serpent proposals and a basic-Island-only board")
        if (proposals.size < 2 || input.decision != null || input.observation.stack.isNotEmpty() ||
            input.observation.activePlayerId != actor || input.observation.priorityPlayerId != actor ||
            !input.observation.step.isMainPhase || input.observation.players.size != 2 ||
            input.observation.players.any { it.hasLost }) return unavailable()
        val board = input.observation.zones.filter { it.zoneType == Zone.BATTLEFIELD }
        if (board.any { it.cards.size != it.size } ||
            board.flatMap { it.cards }.any { it.name !in setOf("Island", "Snow-Covered Island") })
            return unavailable()
        val casts = proposals.map { it.proposal.action as? CastSpell ?: return unavailable() }
        if (casts.map { it.cardId }.distinct().size != casts.size ||
            casts.any { it != CastSpell(actor, it.cardId) } ||
            proposals.any { it.proposal.inputBindingHash != input.bindingHash ||
                it.proposal.nextPolicyRngState != input.policyRngState }) return unavailable()
        val hand = input.observation.zones.singleOrNull { it.ownerId == actor && it.zoneType == Zone.HAND }
            ?: return unavailable()
        if (hand.size != hand.cards.size) return unavailable()
        val cards = casts.map { cast -> hand.cards.singleOrNull { it.entityId == cast.cardId } ?: return unavailable() }
        if (cards.map { it.name }.toSet() != setOf("Tolarian Terror", "Cryptic Serpent") ||
            cards.any { it.ownerId != actor || (it.power ?: 0) <= 0 || (it.toughness ?: 0) <= 0 })
            return unavailable()
        val costs = casts.map { cast ->
            val option = input.legalActions.singleOrNull { it.action == cast && it.affordable } ?: return unavailable()
            if (option.requiresTargets || option.hasXCost || option.additionalCostInfo != null) return unavailable()
            val payment = option.basicBluePayment ?: return unavailable()
            if (payment.status != ActorBasicBluePaymentStatus.PLANNED) return unavailable()
            payment.totalMana?.takeIf { it > 0 } ?: return unavailable()
        }
        val selected = casts.indices.minWith(compareBy<Int> { costs[it] }
            .thenByDescending { cards[it].power!! }.thenBy { cards[it].name }.thenBy { casts[it].cardId.value })
        return proposals[selected].copy(reason = "bounded Terror/Serpent ranking: mana spent, visible power, name, entity ID")
    }
}
