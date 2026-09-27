package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.DecisionPhase
import com.wingedsheep.engine.core.DecisionResponse
import com.wingedsheep.engine.core.OrderedResponse
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.core.SearchLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.gym.actorinput.ActorChoiceSupport
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ActorPublicCards
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.core.Zone

/**
 * Seedless candidate for currently visible cantrip, Approach, Snap and Island-search choices. The same scoring
 * rule is applied to all four frozen 60s. This does not handle Ponder's later shuffle question:
 * its earlier look information needs a replayable actor-memory contract before that choice.
 * Every other typed decision remains unqualified until a separately reviewed finite bank.
 */
internal object SphinxStageEVisibleChoice {
    private class Unqualified(message: String) : IllegalArgumentException(message)

    fun decide(input: ActorInput, epoch: ActorEpoch, actor: EntityId,
               ownDeck: SphinxStageEOwnDeck): SphinxStageEAdapterResult {
        input.verifyBinding(epoch, actor)
        val question = input.decision
            ?: return SphinxStageEAdapterResult.Unqualified(input.bindingHash, "No typed question is pending")
        return try {
            if (question.playerId != actor || question.context.phase != DecisionPhase.RESOLUTION) {
                unsupported("This is not an actor-owned resolution choice")
            }
            val source = question.context.sourceName
                ?: unsupported("The choice source name is absent")
            if (source !in ownDeck.cards || !ownedSourceVisible(input, question.context.sourceId, actor, source)) {
                unsupported("The choice is not bound to a currently visible own-list source")
            }
            val cards = ActorPublicCards(input)
            val response: DecisionResponse = when (question) {
                is SelectCardsDecision -> select(question, source, cards, ownDeck)
                is SearchLibraryDecision -> search(question, source)
                is ReorderLibraryDecision -> reorder(question, source, cards)
                is YesNoDecision -> may(question, source, cards, ownDeck)
                else -> unsupported("This typed choice is outside the visible-choice candidate")
            }
            SphinxStageEAdapterResult.Proposed(
                ActorChoiceSupport.proposal(input, ActorChoiceSupport.submit(input, response)),
                "current source-bound visible choice")
        } catch (failure: Unqualified) {
            SphinxStageEAdapterResult.Unqualified(input.bindingHash, requireNotNull(failure.message))
        }
    }

    private fun ownedSourceVisible(input: ActorInput, id: EntityId?, actor: EntityId,
                                   expectedName: String): Boolean {
        if (id == null) return false
        if (input.observation.stack.any {
                it.view.entityId == id && it.view.name == expectedName && it.spell?.ownerId == actor
            }) return true
        return input.observation.zones.flatMap { it.cards }.any {
            it.entityId == id && it.ownerId == actor && it.name == expectedName
        }
    }

    private fun select(q: SelectCardsDecision, source: String,
                       cards: ActorPublicCards, ownDeck: SphinxStageEOwnDeck): CardsSelectedResponse {
        plainSelection(q.minSelections, q.maxSelections, q.options, q.nonSelectableOptions)
        if (q.ordered || q.onePerCardType || q.onePerColor || q.onePerCardName ||
            q.onePerBasicLandType || q.onePerPower || q.maxTotalManaValue != null ||
            q.minTotalManaValue != null || q.maxTotalPower != null ||
            q.conditionalMinimums.isNotEmpty()) {
            unsupported("This selection constraint needs a separate policy")
        }
        val names = q.options.associateWith { id ->
            q.cardInfo?.get(id)?.name ?: cards.cardOrNull(id)?.name
                ?: unsupported("The current option lacks an actor-visible card name")
        }
        val chosen = when (source) {
            "Brainstorm" -> {
                if (q.minSelections != 2 || q.maxSelections != 2 ||
                    q.options.any { it !in cards.hand.map { c -> c.entityId } }) {
                    unsupported("Brainstorm's current two-card own-hand selection changed")
                }
                q.options.sortedWith(compareBy<EntityId> { score(names.getValue(it), cards) }
                    .thenBy { it.value }).take(2)
            }
            "Preordain" -> {
                if (q.minSelections != 0 || q.maxSelections > 2 || q.options.size > 2) {
                    unsupported("Preordain's current scry-bottom choice changed")
                }
                q.options.filter { score(names.getValue(it), cards) < 30 }
            }
            "Lórien Revealed" -> islandSearch(q.options, names, q.minSelections, q.maxSelections)
            "Sphinx's Approach" -> {
                val graveyardIds = cards.graveyard.map { it.entityId }.toSet()
                if (q.minSelections == 4 && q.maxSelections == 4 &&
                    q.options.all { it in graveyardIds && names[it] == "Sphinx's Approach" }) {
                    if (q.options.size < 4 || unseenSphinx(cards, ownDeck) == 0) {
                        unsupported("Approach payment has no four current graveyard copies or unseen Sphinx")
                    }
                    q.options.sortedBy { it.value }.take(4)
                } else if (q.minSelections in 0..1 && q.maxSelections == 1 &&
                    q.options.none { it in graveyardIds } &&
                    q.options.all { names[it] == "Goliath Sphinx" }) {
                    q.options.sortedBy { it.value }.take(1).also {
                        if (it.isEmpty() && q.minSelections == 1) unsupported("Required Sphinx is not offered")
                    }
                } else unsupported("Approach choice is neither exact payment nor Sphinx search")
            }
            "Snap" -> {
                val board = cards.allBoard.associateBy { it.entityId }
                if (q.minSelections != 0 || q.maxSelections !in 1..2 ||
                    q.options.any { board[it]?.types?.contains("LAND") != true }) {
                    unsupported("Snap's current public land untap domain changed")
                }
                q.options.filter { board.getValue(it).controllerId == cards.actorId &&
                    board.getValue(it).tapped }.sortedBy { it.value }.take(2)
            }
            else -> unsupported("This source has no qualified selection policy")
        }
        return CardsSelectedResponse(q.id, chosen)
    }

    private fun search(q: SearchLibraryDecision, source: String): CardsSelectedResponse {
        if (source != "Lórien Revealed" && source != "Sphinx's Approach") {
            unsupported("This library search source is unqualified")
        }
        val names = q.options.associateWith { id ->
            q.cards[id]?.name ?: unsupported("Island-search metadata is absent")
        }
        val selected = if (source == "Lórien Revealed") {
            islandSearch(q.options, names, q.minSelections, q.maxSelections)
        } else {
            if (q.minSelections !in 0..1 || q.maxSelections != 1 ||
                q.options.any { names[it] != "Goliath Sphinx" }) {
                unsupported("Approach's Sphinx search domain changed")
            }
            q.options.sortedBy { it.value }.take(1).also {
                if (it.isEmpty() && q.minSelections == 1) unsupported("Required Sphinx is not offered")
            }
        }
        return CardsSelectedResponse(q.id, selected)
    }

    private fun may(q: YesNoDecision, source: String, cards: ActorPublicCards,
                    ownDeck: SphinxStageEOwnDeck): YesNoResponse {
        if (source != "Sphinx's Approach" ||
            !q.prompt.contains("exile", ignoreCase = true)) {
            unsupported("This May question is not the Approach exile/search choice")
        }
        val currentCopies = cards.graveyard.count { it.name == "Sphinx's Approach" }
        return YesNoResponse(q.id, currentCopies >= 4 && unseenSphinx(cards, ownDeck) > 0)
    }

    private fun unseenSphinx(cards: ActorPublicCards, ownDeck: SphinxStageEOwnDeck): Int {
        val initial = ownDeck.cards["Goliath Sphinx"] ?: 0
        val outsideIds = (cards.hand + cards.graveyard + cards.zone(cards.actorId, Zone.EXILE) +
            cards.allBoard.filter { it.ownerId == cards.actorId })
            .filter { it.name == "Goliath Sphinx" }.map { it.entityId }
        val stackIds = cards.input.observation.stack.filter {
            it.spell?.ownerId == cards.actorId && it.view.name == "Goliath Sphinx"
        }.map { it.view.entityId }
        val outside = (outsideIds + stackIds).distinct().size
        if (outside > initial) unsupported("Known own Sphinx count exceeds the frozen list")
        return initial - outside
    }

    private fun islandSearch(options: List<EntityId>, names: Map<EntityId, String>,
                             minimum: Int, maximum: Int): List<EntityId> {
        if (minimum !in 0..1 || maximum != 1) unsupported("Island search cardinality changed")
        val candidates = options.filter { names[it] == "Island" || names[it] == "Snow-Covered Island" }
            .sortedWith(compareBy<EntityId> { if (names[it] == "Island") 0 else 1 }
                .thenBy { it.value })
        if (candidates.isEmpty() && minimum > 0) unsupported("Required Island is not offered")
        return candidates.take(1)
    }

    private fun reorder(q: ReorderLibraryDecision, source: String,
                        cards: ActorPublicCards): OrderedResponse {
        val expected = when (source) {
            "Brainstorm", "Preordain" -> 2
            "Ponder" -> 3
            else -> unsupported("This source has no qualified top-of-library reordering")
        }
        if (q.cards.size > expected || q.cards.distinct().size != q.cards.size ||
            q.cards.any { it !in q.cardInfo }) {
            unsupported("Top-of-library reordering metadata or size changed")
        }
        return OrderedResponse(q.id, q.cards.sortedWith(
            compareByDescending<EntityId> { score(q.cardInfo.getValue(it).name, cards) }
                .thenBy { it.value }))
    }

    private fun score(name: String, cards: ActorPublicCards): Int {
        val handLands = cards.hand.count { it.name == "Island" || it.name == "Snow-Covered Island" }
        val landsInPlay = cards.ownBoard.count { it.name == "Island" || it.name == "Snow-Covered Island" }
        return when (name) {
            "Island", "Snow-Covered Island" -> when {
                handLands + landsInPlay < 3 -> 100
                handLands + landsInPlay < 5 -> 55
                else -> 5
            }
            "Counterspell", "Spell Pierce", "Dispel" -> 70
            "Mental Note", "Thought Scour", "Ponder", "Preordain", "Brainstorm" -> 58
            "Tolarian Terror", "Cryptic Serpent" -> 50
            "Lórien Revealed" -> 48
            "Sphinx's Approach" -> if (cards.graveyard.count { it.name == name } >= 4) 65 else 35
            "Snap", "Deem Inferior", "Sleep of the Dead" -> if (cards.opponentBoard.isNotEmpty()) 45 else 15
            "Goliath Sphinx" -> if (landsInPlay >= 6) 50 else 12
            else -> 30
        }
    }

    private fun plainSelection(minimum: Int, maximum: Int, options: List<EntityId>,
                               nonSelectable: List<EntityId>) {
        if (minimum < 0 || maximum < minimum || options.distinct().size != options.size ||
            nonSelectable.any { it in options }) unsupported("Selection domain is inconsistent")
    }

    private fun unsupported(reason: String): Nothing = throw Unqualified(reason)
}
