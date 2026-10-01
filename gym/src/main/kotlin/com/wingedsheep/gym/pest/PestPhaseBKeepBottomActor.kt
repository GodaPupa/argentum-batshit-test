package com.wingedsheep.gym.pest

internal data class PestPhaseBKeepDecision(val keep: Boolean?, val reason: String)
internal data class PestPhaseBBottomDecision(val orderedIds: List<String>?, val reason: String)

/**
 * Prospective Phase-B decision consumer of the unchanged own/public predicate vector.
 * This has no raw-controller, GameState, opponent information, library order or RNG capability.
 * The trusted comparator must still bind the current vector, physical IDs, seat/start context
 * and exact accepted truth inputs and compare against the actual frozen controller. Synthetic
 * tests of this component do not establish finite-bank equivalence or pilot admission.
 */
internal object PestPhaseBKeepBottomActor {
    fun keep(vector: PestLondonPredicateVector): PestPhaseBKeepDecision {
        require(vector.physicalLandCount in 0..7)
        require(!vector.guaranteedSecondLandAccess || vector.physicalLandCount == 1)
        require(vector.earlySpellIds.distinct().size == vector.earlySpellIds.size)
        if (vector.forcedKeep) return PestPhaseBKeepDecision(true, "M0_FORCED_KEEP")
        // Empty M3 is directly observed: no M5 atom is queried or defaulted in this case.
        // This preserves the raw existential early-spell predicate's empty-set result.
        if (vector.earlySpellIds.isEmpty()) return PestPhaseBKeepDecision(false, "M3_EMPTY_NO_M5_ATOM_REQUIRED")
        if (vector.developmentFunctional == null) return PestPhaseBKeepDecision(null, "FAIL_CLOSED_UNKNOWN_M5")
        val effectiveLands = vector.physicalLandCount + if (vector.guaranteedSecondLandAccess) 1 else 0
        return PestPhaseBKeepDecision(
            effectiveLands in 2..5 && vector.colorFunctional && vector.developmentFunctional,
            "M1_M2_M4_M5_COMPOSITION",
        )
    }

    fun bottom(
        vector: PestLondonPredicateVector,
        visibleHandIds: List<String>,
        cardsToBottom: Int,
    ): PestPhaseBBottomDecision {
        val hand = visibleHandIds.toList()
        require(hand.size in 1..7 && hand.all { it.isNotBlank() } && hand.distinct().size == hand.size)
        require(cardsToBottom in 0..hand.size)
        require(vector.bottomTargetLands == if (hand.size - cardsToBottom <= 5) 2 else 3)
        val protected = vector.protectedVisibleIds.toSet()
        require(hand.containsAll(protected))
        val ranks = listOf(vector.excessLandIdsInPhysicalOrder.toList(), vector.expensiveSpellIds.toList(),
            vector.fallbackVisibleIdsInPhysicalOrder.toList())
        ranks.forEach { ids ->
            require(ids.distinct().size == ids.size && ids.all { it in hand && it !in protected }) {
                "Bottom candidates must be distinct current own unprotected physical IDs"
            }
        }
        require(ranks[0].intersect(ranks[1].toSet()).isEmpty()) { "Land and spell ranks overlap" }
        val chosen = linkedSetOf<String>()
        for (rank in ranks) for (id in rank) if (chosen.size < cardsToBottom) chosen.add(id)
        if (chosen.size != cardsToBottom) return PestPhaseBBottomDecision(null, "FAIL_CLOSED_INCOMPLETE_BOTTOM_SET")
        return PestPhaseBBottomDecision(chosen.toList(), "B2_EXCESS_THEN_B3_COST_THEN_B4_PHYSICAL")
    }
}
