package com.wingedsheep.gym.pest

import java.security.MessageDigest

/** A deterministic physical handle; copyOrdinal is one-based within the frozen submitted list. */
internal data class PestPhaseBPhysicalCard(
    val entryIndex: Int,
    val copyOrdinal: Int,
    val name: String,
)

/**
 * One exact physical representative of a frozen seven-card name multiset.
 * No truth, raw controller, GameState, RNG, or hidden library order is consulted here.
 */
internal data class PestPhaseBPlannedRow(
    val context: PestPhaseBRowContext,
    val orderedHand: List<PestPhaseBPhysicalCard>,
)

internal data class PestPhaseBDeckSpec(
    val entries: List<Pair<String, Int>>,
    val lands: Set<String>,
    val cmc: Map<String, Int>,
    val expectedDeckSha256: String,
    val expectedNameMultisets: Long,
)

/**
 * Streaming Phase-B plan. It expands only the physical order dimensions the frozen raw/lawful
 * bottom rules can observe: land order when excess lands can be bottomed and stable-order ties
 * inside the descending-CMC spell groups that may be reached by the requested bottom count.
 *
 * Interleaving lands with spells and reordering distinct CMC groups are intentionally quotiented
 * out: both frozen implementations first partition land/spell cards, then order spell candidates
 * by descending CMC with physical order only as the equal-CMC tie breaker.
 */
internal object PestPhaseBPhysicalPlan {
    private val pest = PestPhaseBDeckSpec(
        entries = listOf(
            "Essence Warden" to 4, "Carrier Thrall" to 4, "Blood Researcher" to 4, "Pest Mascot" to 4,
            "Fierce Witchstalker" to 4, "Generous Ent" to 3, "Follow the Lumarets" to 4,
            "Weather the Storm" to 4, "Cast Down" to 4, "Bone Shards" to 2, "Chainer's Edict" to 2,
            "Forest" to 10, "Swamp" to 7, "Jungle Hollow" to 4,
        ),
        lands = setOf("Forest", "Swamp", "Jungle Hollow"),
        cmc = mapOf(
            "Essence Warden" to 1, "Carrier Thrall" to 2, "Blood Researcher" to 3, "Pest Mascot" to 3,
            "Fierce Witchstalker" to 4, "Generous Ent" to 6, "Follow the Lumarets" to 2,
            "Weather the Storm" to 2, "Cast Down" to 2, "Bone Shards" to 1, "Chainer's Edict" to 2,
        ),
        expectedDeckSha256 = "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5",
        expectedNameMultisets = 71271,
    )
    private val monster = PestPhaseBDeckSpec(
        entries = listOf(
            "Rooftop Percher" to 2, "Generous Ent" to 2, "Boulderbranch Golem" to 2, "Bramble Wurm" to 4,
            "Maelstrom Colossus" to 4, "Ancient Stirrings" to 4, "Crop Rotation" to 3, "Breath Weapon" to 2,
            "Unfathomable Truths" to 2, "Barrels of Blasting Jelly" to 1, "Candy Trail" to 2,
            "Expedition Map" to 4, "Giant's Boulder" to 4, "Bonder's Ornament" to 2,
            "Pinnacle Kill-Ship" to 4, "Bojuka Bog" to 1, "Conduit Pylons" to 2, "Forest" to 2,
            "Haunted Fengraf" to 1, "Urza's Mine" to 4, "Urza's Power Plant" to 4, "Urza's Tower" to 4,
        ),
        lands = setOf("Bojuka Bog", "Conduit Pylons", "Forest", "Haunted Fengraf", "Urza's Mine", "Urza's Power Plant", "Urza's Tower"),
        cmc = mapOf(
            "Rooftop Percher" to 5, "Generous Ent" to 6, "Boulderbranch Golem" to 7, "Bramble Wurm" to 7,
            "Maelstrom Colossus" to 8, "Ancient Stirrings" to 1, "Crop Rotation" to 1, "Breath Weapon" to 3,
            "Unfathomable Truths" to 5, "Barrels of Blasting Jelly" to 1, "Candy Trail" to 1,
            "Expedition Map" to 1, "Giant's Boulder" to 1, "Bonder's Ornament" to 3, "Pinnacle Kill-Ship" to 7,
        ),
        expectedDeckSha256 = "79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f",
        expectedNameMultisets = 882297,
    )

    fun spec(deck: String): PestPhaseBDeckSpec = when (deck) {
        "pest" -> pest
        "monster" -> monster
        else -> error("Unfrozen Phase-B deck: $deck")
    }.also(::verifySpec)

    fun nameMultisets(deck: String): Sequence<Pair<Long, IntArray>> {
        val spec = spec(deck)
        return sequence {
            val counts = IntArray(spec.entries.size)
            var ordinal = 0L
            suspend fun SequenceScope<Pair<Long, IntArray>>.walk(index: Int, left: Int) {
                if (index == counts.size) {
                    if (left == 0) yield(ordinal++ to counts.copyOf())
                    return
                }
                val cap = minOf(spec.entries[index].second, left)
                for (n in 0..cap) {
                    counts[index] = n
                    walk(index + 1, left - n)
                }
                counts[index] = 0
            }
            walk(0, 7)
            check(ordinal == spec.expectedNameMultisets) {
                "Frozen name-multiset denominator drift for $deck: $ordinal"
            }
        }
    }

    fun rows(deck: String): Sequence<PestPhaseBPlannedRow> = sequence {
        val spec = spec(deck)
        for ((nameOrdinal, counts) in nameMultisets(deck)) {
            val cards = physicalCards(spec, counts)
            check(cards.size == 7)
            for (mulligans in 0..2) {
                var physicalOrdinal = 0L
                for (ordered in physicalRepresentatives(spec, cards, mulligans)) {
                    for (seat in 0..1) for (onPlay in listOf(false, true)) {
                        yield(PestPhaseBPlannedRow(
                            PestPhaseBRowContext(deck, seat, onPlay, mulligans, nameOrdinal, physicalOrdinal),
                            ordered,
                        ))
                    }
                    physicalOrdinal++
                }
                check(physicalOrdinal > 0)
            }
        }
    }

    private fun verifySpec(spec: PestPhaseBDeckSpec) {
        check(spec.entries.sumOf { it.second } == 60)
        check(spec.entries.map { it.first }.distinct().size == spec.entries.size)
        check(spec.lands.all { land -> spec.entries.any { it.first == land } })
        check(spec.cmc.keys == spec.entries.map { it.first }.filterNot(spec.lands::contains).toSet())
        val text = spec.entries.joinToString("") { (name, count) -> "$name,$count\n" }
        check(sha256(text.toByteArray()) == spec.expectedDeckSha256)
    }

    private fun physicalCards(spec: PestPhaseBDeckSpec, counts: IntArray): List<PestPhaseBPhysicalCard> = buildList {
        counts.forEachIndexed { entryIndex, count ->
            repeat(count) { copy -> add(PestPhaseBPhysicalCard(entryIndex, copy + 1, spec.entries[entryIndex].first)) }
        }
    }

    private fun physicalRepresentatives(
        spec: PestPhaseBDeckSpec,
        cards: List<PestPhaseBPhysicalCard>,
        mulligans: Int,
    ): Sequence<List<PestPhaseBPhysicalCard>> {
        require(mulligans in 0..2)
        if (mulligans == 0) return sequenceOf(cards)

        val lands = cards.filter { it.name in spec.lands }
        val spells = cards.filterNot { it.name in spec.lands }
        val targetLands = if (7 - mulligans <= 5) 2 else 3
        val excess = (lands.size - targetLands).coerceAtLeast(0)
        val landOrders = if (excess > 0) permutations(lands) else sequenceOf(lands)

        var need = (mulligans - excess).coerceAtLeast(0)
        val groups = spells.groupBy { spec.cmc.getValue(it.name) }
            .toSortedMap(compareByDescending { it })
            .entries.map { it.key to it.value }
        val sensitive = mutableSetOf<Int>()
        for ((cmc, group) in groups) {
            if (need <= 0) break
            sensitive += cmc
            need -= group.size
        }

        fun spellOrders(index: Int, prefix: List<PestPhaseBPhysicalCard>): Sequence<List<PestPhaseBPhysicalCard>> = sequence {
            if (index == groups.size) {
                yield(prefix)
                return@sequence
            }
            val (cmc, group) = groups[index]
            val options = if (cmc in sensitive && group.size > 1) permutations(group) else sequenceOf(group)
            for (option in options) yieldAll(spellOrders(index + 1, prefix + option))
        }

        return sequence {
            for (landOrder in landOrders) for (spellOrder in spellOrders(0, emptyList())) {
                val ordered = landOrder + spellOrder
                check(ordered.size == 7 && ordered.toSet().size == 7)
                yield(ordered)
            }
        }
    }

    private fun <T> permutations(values: List<T>): Sequence<List<T>> = sequence {
        if (values.size <= 1) {
            yield(values)
            return@sequence
        }
        values.indices.forEach { index ->
            val head = values[index]
            val rest = values.take(index) + values.drop(index + 1)
            for (tail in permutations(rest)) yield(listOf(head) + tail)
        }
    }

    private fun sha256(raw: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(raw).joinToString("") { "%02x".format(it.toInt() and 255) }
}
