package com.wingedsheep.gym.matchup

import com.wingedsheep.sdk.core.Zone

/**
 * Bounded own/public London keep candidate. Null means this opening is outside the qualified
 * payment/development domain, not a mulligan. The raw controller remains the frozen comparator.
 * No hidden library handle, future draw or GameState enters this actor decision.
 */
internal object PestMonsterLondonKeepPolicy {
    fun decide(setup: PestMonsterLondonSetup, mulligansTaken: Int): Boolean? {
        val input = setup.input
        input.verifyBinding(input.epoch, input.actorId)
        val hand = input.observation.zones.single {
            it.ownerId == input.actorId && it.zoneType == Zone.HAND
        }
        require(!hand.hidden && hand.size == setup.ownHandOrder.size &&
            setup.ownHandOrder.distinct().size == hand.size &&
            hand.cards.map { it.entityId }.toSet() == setup.ownHandOrder.toSet()) {
            "London actor hand must bind every physical own card"
        }
        if (hand.size <= 5 || mulligansTaken >= 2) return true
        if (hand.size != 7 || mulligansTaken != 0) return null
        val lands = hand.cards.filter { "LAND" in it.types }
        if (lands.isEmpty()) return false
        // A separate real-engine receipt established green Ancient Stirrings payment from a
        // Forest. Other land entries and additional-cost spells need their own actor development
        // policy before this function can answer them.
        if (lands.size != 2 || lands.map { it.name }.toSet() !=
            setOf("Forest", "Urza's Tower") ||
            hand.cards.none { it.name == "Ancient Stirrings" }) return null
        val early = hand.cards.filter { "LAND" !in it.types && it.manaCost.isNotBlank() &&
            it.manaValue <= 2 }
        val greenCompatible = early.count { card ->
            Regex("\\{([WUBRG])\\}").findAll(card.manaCost)
                .all { it.groupValues[1] == "G" }
        }
        val colorFunctional = early.isNotEmpty() &&
            (early.size - greenCompatible < 3 || greenCompatible >= 2)
        return colorFunctional
    }
}
