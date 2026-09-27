package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.BottomCards
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.gym.actorinput.ActorChoiceSupport
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.contract.EntityFeatures
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/**
 * A seedless source candidate for the opening decisions shared by all four frozen Stage-E 60s.
 * The initialized seat binds list identity. This policy sees only the sealed actor input.
 * It deliberately leaves spell ranking, pending effect choices, combat and ordinary priority
 * unqualified until a complete, prospective, equal-competence fixture inventory is reviewed.
 */
object SphinxStageEOpeningActor {
    fun decide(
        input: ActorInput,
        expectedEpoch: ActorEpoch,
        expectedActor: EntityId,
    ): SphinxStageEAdapterResult {
        input.verifyBinding(expectedEpoch, expectedActor)
        if (input.decision != null) return unqualified(input, "A pending typed effect choice needs its own pilot")
        if (input.observation.players.size != 2 || input.observation.players.any { it.hasLost }) {
            return unqualified(input, "Opening policy requires two active duel seats")
        }
        val handView = input.observation.zones.singleOrNull {
            it.ownerId == expectedActor && it.zoneType == Zone.HAND
        } ?: return unqualified(input, "Current own hand view is missing")
        if (handView.size != handView.cards.size ||
            handView.cards.map { it.entityId }.distinct().size != handView.cards.size) {
            return unqualified(input, "Current own hand is incomplete")
        }
        val hand = handView.cards
        val resources = input.observation.turnResources.singleOrNull { it.playerId == expectedActor }
            ?: return unqualified(input, "Current actor resources are missing")
        val keep = input.legalActions.singleOrNull { it.action is KeepHand && it.affordable }
        val take = input.legalActions.singleOrNull { it.action is TakeMulligan && it.affordable }
        if (keep != null) {
            if (resources.hasKept != false || hand.size != 7) {
                return unqualified(input, "London choice does not match current opening resources")
            }
            val lands = hand.count(::isBasicIsland)
            val choice = if (lands in 2..5 || take == null) keep else take
            return proposed(input, choice!!.action, "bounded opening land-count choice")
        }
        if (take != null) return unqualified(input, "Mulligan offered without a matching keep option")
        val bottom = input.legalActions.singleOrNull { it.action is BottomCards && it.affordable }
        if (bottom != null) {
            val count = resources.cardsToBottom
                ?: return unqualified(input, "London bottom count is missing")
            if (resources.hasKept != true || count !in 1..hand.size) {
                return unqualified(input, "London bottom count does not match current resources")
            }
            val landCount = hand.count(::isBasicIsland)
            // The same deterministic ordering applies to every frozen list. Current hand facts
            // determine surplus lands and duplicate names; no hidden library card is inspected.
            val duplicates = hand.groupingBy { it.name }.eachCount()
            val chosen = hand.sortedWith(
                compareBy<EntityFeatures> {
                    when {
                        isBasicIsland(it) && landCount > 3 -> 0
                        !isBasicIsland(it) && (duplicates[it.name] ?: 0) > 1 -> 1
                        !isBasicIsland(it) -> 2
                        else -> 3
                    }
                }.thenByDescending { it.manaValue }.thenBy { it.name }.thenBy { it.entityId.value }
            ).take(count).map { it.entityId }
            return proposed(input, BottomCards(expectedActor, chosen), "current-hand London bottom choice")
        }
        val lands = input.legalActions.filter { it.action is PlayLand && it.affordable }
            .filter { option ->
                val id = (option.action as PlayLand).cardId
                hand.any { it.entityId == id && isBasicIsland(it) }
            }.sortedBy { (it.action as PlayLand).cardId.value }
        if (lands.isNotEmpty()) {
            if (resources.landDropsRemaining <= 0) {
                return unqualified(input, "Land action conflicts with current land-drop count")
            }
            return proposed(input, lands.first().action, "current offered basic Island land play")
        }
        return unqualified(input, "No qualified opening or basic land action on this input")
    }

    private fun isBasicIsland(card: EntityFeatures): Boolean =
        card.name == "Island" || card.name == "Snow-Covered Island"

    private fun proposed(input: ActorInput, action: com.wingedsheep.engine.core.GameAction, reason: String) =
        SphinxStageEAdapterResult.Proposed(ActorChoiceSupport.proposal(input, action), reason)

    private fun unqualified(input: ActorInput, reason: String) =
        SphinxStageEAdapterResult.Unqualified(input.bindingHash, reason)
}
