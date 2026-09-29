package com.wingedsheep.gym.pest

/**
 * Canonical grouping machinery for the future exhaustive London parity bank.
 *
 * A logical equivalence key intentionally discards physical entity identities while retaining every
 * reviewed raw-controller predicate axis plus the submitted-seat/opening context and duplicate-name
 * multiplicities. Physical bottom-order permutations are retained separately and must all be
 * exercised before a logical group can be considered covered.
 *
 * This type does not generate openings, call the raw controller, or grant parity by itself.
 */
data class PestLondonLogicalEquivalenceKey(
    val seat: Int,
    val onPlay: Boolean,
    val mulligansTaken: Int,
    val handSize: Int,
    val forcedKeep: Boolean,
    val physicalLandCount: Int,
    val guaranteedSecondLandAccess: Boolean,
    val earlySpellNames: List<String>,
    val colorFunctional: Boolean,
    val developmentFunctional: Boolean?,
    val bottomTargetLands: Int,
    val protectedPairPresent: Boolean,
    val duplicateNameMultiplicities: Map<String, Int>,
)

data class PestLondonPhysicalPermutationKey(
    val handIdsInPhysicalOrder: List<String>,
    val protectedIdsInPhysicalOrder: List<String>,
    val excessLandIdsInPhysicalOrder: List<String>,
    val expensiveSpellIdsInBottomOrder: List<String>,
    val fallbackIdsInPhysicalOrder: List<String>,
)

data class PestLondonParityBankEntry(
    val logical: PestLondonLogicalEquivalenceKey,
    val physicalPermutations: Set<PestLondonPhysicalPermutationKey>,
)

object PestMonsterLondonParityBank {
    fun logicalKey(
        seat: Int,
        onPlay: Boolean,
        mulligansTaken: Int,
        hand: List<PestLondonCardFacts>,
        vector: PestLondonPredicateVector,
    ): PestLondonLogicalEquivalenceKey {
        require(seat in 0..1)
        require(mulligansTaken >= 0)
        val factsById = hand.associateBy { it.id }
        require(factsById.size == hand.size)
        val earlyNames = vector.earlySpellIds.map { id ->
            requireNotNull(factsById[id]) { "Early-spell handle absent from visible hand" }.name
        }.sorted()
        val duplicates = hand.groupingBy { it.name }.eachCount()
            .filterValues { it > 1 }.toSortedMap()
        return PestLondonLogicalEquivalenceKey(
            seat = seat,
            onPlay = onPlay,
            mulligansTaken = mulligansTaken,
            handSize = hand.size,
            forcedKeep = vector.forcedKeep,
            physicalLandCount = vector.physicalLandCount,
            guaranteedSecondLandAccess = vector.guaranteedSecondLandAccess,
            earlySpellNames = earlyNames,
            colorFunctional = vector.colorFunctional,
            developmentFunctional = vector.developmentFunctional,
            bottomTargetLands = vector.bottomTargetLands,
            protectedPairPresent = vector.protectedVisibleIds.isNotEmpty(),
            duplicateNameMultiplicities = duplicates,
        )
    }

    fun physicalKey(
        hand: List<PestLondonCardFacts>,
        vector: PestLondonPredicateVector,
    ): PestLondonPhysicalPermutationKey {
        val ids = hand.map { it.id }
        require(ids.distinct().size == ids.size)
        require(vector.protectedVisibleIds.all { it in ids })
        val protected = ids.filter { it in vector.protectedVisibleIds }
        return PestLondonPhysicalPermutationKey(
            handIdsInPhysicalOrder = ids,
            protectedIdsInPhysicalOrder = protected,
            excessLandIdsInPhysicalOrder = vector.excessLandIdsInPhysicalOrder,
            expensiveSpellIdsInBottomOrder = vector.expensiveSpellIds,
            fallbackIdsInPhysicalOrder = vector.fallbackVisibleIdsInPhysicalOrder,
        )
    }

    fun group(
        rows: List<Triple<PestLondonLogicalEquivalenceKey, PestLondonPhysicalPermutationKey, String>>,
    ): List<PestLondonParityBankEntry> {
        require(rows.map { it.third }.distinct().size == rows.size) {
            "Every prospective bank row needs a unique case id"
        }
        return rows.groupBy { it.first }.entries
            .sortedBy { it.key.toString() }
            .map { (logical, members) ->
                PestLondonParityBankEntry(logical, members.map { it.second }.toSet())
            }
    }
}
