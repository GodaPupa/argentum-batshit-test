package com.wingedsheep.gym.pest

/**
 * Lawful receiving seam from the accepted finite London M5 atom truth bank into the already
 * qualified predicate-vector representation.
 *
 * The lookup key contains only own/public facts already admitted by the M5 atom certificate:
 * exact physical land-name multiset, the deterministic M2 flag, and one early spell identity.
 * Missing truth remains unknown; this adapter never infers from library order, entity ids, RNG,
 * opponent hidden state, or neighboring atoms.
 */
internal data class PestLondonM5TruthKey(
    val physicalLands: List<Pair<String, Int>>,
    val m2OpeningCandidate: Boolean,
    val earlySpell: String,
)

internal class PestMonsterLondonM5TruthReceiving(
    private val truth: Map<PestLondonM5TruthKey, Boolean>,
) {
    fun bind(
        hand: List<PestLondonCardFacts>,
        m2OpeningCandidate: Boolean,
    ): List<PestLondonCardFacts> {
        val physicalLands = hand.asSequence()
            .filter { it.isLand }
            .groupingBy { it.name }
            .eachCount()
            .toSortedMap()
            .map { it.key to it.value }

        return hand.map { card ->
            if (card.isLand) {
                card
            } else {
                val value = truth[PestLondonM5TruthKey(physicalLands, m2OpeningCandidate, card.name)]
                if (value == null) card else card.copy(deterministicDevelopmentPayable = value)
            }
        }
    }
}
