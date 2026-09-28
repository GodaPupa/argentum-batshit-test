package com.wingedsheep.gym.pest

/**
 * Lawful own/public London predicate representation for Pest/Monster receiving.
 *
 * This source accepts only the submitted own-deck multiset, the actor's visible own hand and
 * explicit deterministic certificates. It has no GameState, library entity ids/order, RNG or
 * opponent hidden information. Unknown development evidence fails closed.
 */
data class PestLondonCardFacts(
    val id: String,
    val name: String,
    val isLand: Boolean,
    val cmc: Int,
    val colorsRequired: Set<Char> = emptySet(),
    val colorsProduced: Set<Char> = emptySet(),
    val typedCyclingTargets: Set<String> = emptySet(),
    val typedCyclingPayableBySoleLand: Boolean = false,
    val deterministicDevelopmentPayable: Boolean? = null,
)

data class PestLondonPredicateVector(
    val forcedKeep: Boolean,                  // M0
    val physicalLandCount: Int,               // M1
    val guaranteedSecondLandAccess: Boolean,  // M2
    val earlySpellIds: List<String>,          // M3
    val colorFunctional: Boolean,             // M4
    val developmentFunctional: Boolean?,      // M5; null => fail closed
    val bottomTargetLands: Int,                // B0
    val protectedVisibleIds: Set<String>,      // B1
    val excessLandIdsInPhysicalOrder: List<String>, // B2
    val expensiveSpellIds: List<String>,      // B3
    val fallbackVisibleIdsInPhysicalOrder: List<String>, // B4
) {
    val qualifiedForKeepDecision: Boolean get() = forcedKeep || developmentFunctional != null
}

object PestMonsterLondonPredicateVectorExtractor {
    fun extract(
        submittedDeck: Map<String, Int>,
        hand: List<PestLondonCardFacts>,
        mulliganCount: Int,
        cardsToBottom: Int = 0,
    ): PestLondonPredicateVector {
        require(submittedDeck.values.all { it >= 0 } && submittedDeck.values.sum() == 60)
        require(hand.map { it.id }.distinct().size == hand.size)
        require(hand.all { (submittedDeck[it.name] ?: 0) >= hand.count { h -> h.name == it.name } })
        require(cardsToBottom in 0..hand.size)

        val forcedKeep = hand.size <= 5 || mulliganCount >= 2
        val lands = hand.filter { it.isLand }
        val spells = hand.filterNot { it.isLand }

        val soleLand = lands.singleOrNull()
        val acquisition = if (soleLand != null) spells.firstOrNull { card ->
            card.typedCyclingPayableBySoleLand &&
                card.typedCyclingTargets.any { target ->
                    (submittedDeck[target] ?: 0) - hand.count { it.name == target } > 0
                }
        } else null
        val guaranteed = soleLand != null && acquisition != null
        val effectiveLandCount = lands.size + if (guaranteed) 1 else 0
        val earlyHorizon = effectiveLandCount.coerceAtMost(3)
        val early = spells.filter { it.cmc <= earlyHorizon }
        val produced = lands.flatMapTo(mutableSetOf()) { it.colorsProduced }
        val castable = early.count { card ->
            card.colorsRequired.isEmpty() || card.colorsRequired.any { it in produced }
        }
        val mismatch = early.size - castable
        val colorFunctional = early.isNotEmpty() && (mismatch < 3 || castable >= 2)

        // Raw M5 is existential. If at least one exact deterministic certificate is true, M5=true.
        // If every early candidate is explicitly false, M5=false. Otherwise M5 is unknown.
        val development = when {
            early.any { it.deterministicDevelopmentPayable == true } -> true
            early.isNotEmpty() && early.all { it.deterministicDevelopmentPayable == false } -> false
            else -> null
        }

        val targetLands = if (hand.size - cardsToBottom <= 5) 2 else 3
        val protected = buildSet {
            soleLand?.takeIf { guaranteed }?.let { add(it.id) }
            acquisition?.let { add(it.id) }
        }
        val excess = if (lands.size > targetLands) lands.drop(targetLands).map { it.id } else emptyList()
        val expensive = spells.filterNot { it.id in protected }
            .sortedWith(compareByDescending<PestLondonCardFacts> { it.cmc }.thenBy { hand.indexOf(it) })
            .map { it.id }
        val fallback = hand.filterNot { it.id in protected }.map { it.id }

        return PestLondonPredicateVector(
            forcedKeep, lands.size, guaranteed, early.map { it.id }, colorFunctional, development,
            targetLands, protected, excess, expensive, fallback,
        )
    }
}
