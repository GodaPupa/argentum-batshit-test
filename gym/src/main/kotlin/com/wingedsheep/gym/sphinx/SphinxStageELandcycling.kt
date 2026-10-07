package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.TypecycleCard
import com.wingedsheep.gym.actorinput.ActorChoiceSupport
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/** A bounded land-development choice, not general activation/cast/mana ranking. */
internal object SphinxStageELandcycling {
    fun decide(input: ActorInput, epoch: ActorEpoch, actor: EntityId,
               ownDeck: SphinxStageEOwnDeck): SphinxStageEAdapterResult {
        input.verifyBinding(epoch, actor)
        fun unsupported(reason: String) = SphinxStageEAdapterResult.Unqualified(input.bindingHash, reason)
        val view = input.observation
        if (input.decision != null || view.players.size != 2 || view.players.any { it.hasLost } ||
            view.activePlayerId != actor || view.priorityPlayerId != actor ||
            !view.step.isMainPhase || view.stack.isNotEmpty()) {
            return unsupported("Landcycling requires current own main-phase priority and an empty stack")
        }
        val hand = view.zones.singleOrNull { it.ownerId == actor && it.zoneType == Zone.HAND }
            ?: return unsupported("Own hand missing")
        if (hand.size != hand.cards.size || hand.cards.map { it.entityId }.distinct().size != hand.size) {
            return unsupported("Own hand incomplete")
        }
        val boardViews = view.zones.filter { it.zoneType == Zone.BATTLEFIELD }
        if (boardViews.isEmpty() || boardViews.any { it.size != it.cards.size }) {
            return unsupported("Public battlefield incomplete")
        }
        val board = boardViews.flatMap { it.cards }
        val basicNames = setOf("Island", "Snow-Covered Island")
        // Restrict the initial seam to public basic-Island boards: no permanent cost/trigger
        // interactions or acquired cards are silently admitted through a mana affordability flag.
        if (board.any { it.name !in basicNames || it.faceDown } ||
            board.count { it.controllerId == actor } !in 1..2 ||
            hand.cards.any { "LAND" in it.types }) {
            return unsupported("Landcycling is bounded to one or two own Islands and no hand land")
        }
        val offers = input.legalActions.filter { it.action is TypecycleCard }
        val offer = offers.singleOrNull() ?: return unsupported("Multiple landcycling offers require ranking")
        val action = offer.action as TypecycleCard
        val card = hand.cards.singleOrNull { it.entityId == action.cardId }
        if (action.playerId != actor || card?.name != "Lórien Revealed" ||
            card.ownerId != actor || card.faceDown || "Lórien Revealed" !in ownDeck.cards ||
            !offer.affordable || offer.hasXCost || offer.requiresTargets ||
            !offer.targetRequirements.isNullOrEmpty() || offer.additionalCostInfo != null) {
            return unsupported("No exact affordable current own-list Lórien landcycling offer")
        }
        if (input.legalActions.any { it.affordable && it !== offer &&
                it.action !is PassPriority && !it.isManaAbility }) {
            return unsupported("Competing action requires a separate ranking policy")
        }
        return SphinxStageEAdapterResult.Proposed(
            ActorChoiceSupport.proposal(input, action), "single current Lórien landcycling land-development offer")
    }
}
