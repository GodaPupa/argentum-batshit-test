package com.wingedsheep.gym.matchup

import com.wingedsheep.ai.engine.LimitedPickScorer
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/**
 * Candidate physical London bottom choice from only the bound own hand and printed-Forest
 * existence. The raw controller remains the frozen comparator; this code cannot inspect a
 * physical library card or simulated future GameState. Keep/mulligan parity is a separate gate.
 */
internal object PestMonsterLondonBottomPolicy {
    fun choose(setup: PestMonsterLondonSetup, count: Int): List<EntityId> {
        val input = setup.input
        input.verifyBinding(input.epoch, input.actorId)
        if (count <= 0) return emptyList()
        val hand = input.observation.zones.single {
            it.ownerId == input.actorId && it.zoneType == Zone.HAND
        }
        require(!hand.hidden && hand.size == setup.ownHandOrder.size &&
            setup.ownHandOrder.distinct().size == hand.size &&
            hand.cards.map { it.entityId }.toSet() == setup.ownHandOrder.toSet()) {
            "London physical hand does not bind the actor view"
        }
        require(count <= hand.size)
        val byId = hand.cards.associateBy { it.entityId }
        val lands = setup.ownHandOrder.filter { "LAND" in byId.getValue(it).types }
        val spells = setup.ownHandOrder.filterNot { it in lands }
        val targetLands = if (hand.size - count <= 5) 2 else 3
        val protected = PestMonsterLondonTypecyclingPlanner.firstTwoDrops(setup)?.let {
            setOf(it.firstLandId, it.acquisitionCardId)
        }.orEmpty()
        val bottom = mutableListOf<EntityId>()
        if (lands.size > targetLands) bottom.addAll(lands.drop(targetLands))
        if (bottom.size < count) {
            for (spell in spells.filterNot { it in protected }
                .sortedByDescending { LimitedPickScorer.parseCmc(byId.getValue(it).manaCost) }) {
                if (bottom.size >= count) break
                bottom += spell
            }
        }
        if (bottom.size < count) {
            for (card in setup.ownHandOrder) {
                if (bottom.size >= count) break
                if (card !in bottom && card !in protected) bottom += card
            }
        }
        return bottom.take(count)
    }
}
