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
        // The real-engine receiving receipts established both a direct Forest-funded
        // Stirrings cast and Tower -> Ent typecycling -> Forest -> Stirrings. Only these
        // deterministic development lines are answered here; all other entries/costs remain
        // unqualified. The typecycling plan consumes only the Boolean printed-Forest fact.
        val direct = lands.size == 2 &&
            lands.map { it.name }.toSet() == setOf("Forest", "Urza's Tower")
        val cycled = lands.size == 1 &&
            lands.single().name == "Urza's Tower" &&
            PestMonsterLondonTypecyclingPlanner.firstTwoDrops(setup) != null
        if ((!direct && !cycled) ||
            hand.cards.none { it.name == "Ancient Stirrings" }) return null
        val early = hand.cards.filter { "LAND" !in it.types && it.manaCost.isNotBlank() &&
            it.manaValue <= 2 }
        val greenCompatible = early.count { card ->
            Regex("\\{([WUBRG])\\}").findAll(card.manaCost)
                .all { direct && it.groupValues[1] == "G" }
        }
        val colorFunctional = early.isNotEmpty() &&
            (early.size - greenCompatible < 3 || greenCompatible >= 2)
        return colorFunctional
    }
}
