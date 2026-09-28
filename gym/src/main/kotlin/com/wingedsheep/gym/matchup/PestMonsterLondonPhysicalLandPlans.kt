package com.wingedsheep.gym.matchup

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/**
 * The physical-land portion of the frozen controller's three-drop horizon. This is a detached
 * own-hand computation: no deck order, library handle, opponent hand, or imagined draw enters it.
 * Typecycling and actual entry/payment semantics require separate receiving authority.
 */
internal object PestMonsterLondonPhysicalLandPlans {
    fun enumerate(setup: PestMonsterLondonSetup): List<List<EntityId>> {
        val input = setup.input
        input.verifyBinding(input.epoch, input.actorId)
        val hand = input.observation.zones.single {
            it.ownerId == input.actorId && it.zoneType == Zone.HAND
        }
        require(hand.size == setup.ownHandOrder.size &&
            setup.ownHandOrder.distinct().size == hand.size &&
            hand.cards.map { it.entityId }.toSet() == setup.ownHandOrder.toSet()) {
            "Physical London hand is not bound to the actor view"
        }
        val features = hand.cards.associateBy { it.entityId }
        val lands = setup.ownHandOrder.filter { "LAND" in features.getValue(it).types }
        val horizon = lands.size.coerceAtMost(3)
        if (horizon == 0) return emptyList()
        return permutations(lands, horizon)
    }

    private fun permutations(cards: List<EntityId>, length: Int): List<List<EntityId>> {
        if (length == 0) return listOf(emptyList())
        return cards.flatMapIndexed { index, card ->
            permutations(cards.take(index) + cards.drop(index + 1), length - 1)
                .map { listOf(card) + it }
        }
    }
}
