package com.wingedsheep.ai.engine

/**
 * Bounded source-selection representatives for the Strategist's OPTIONAL
 * same-turn land/setup/focal counterfactual heuristic. These are not legal
 * actions by themselves; the authoritative simulator still validates every
 * proposed payment and the original automatic-payment action is retained.
 *
 * For a small combination universe, preserve exact exhaustive ordering.
 * Above the bound, use a fixed, seed-free diversity sample. The bounded
 * branch is APPROXIMATE: it may miss a legal strategically valuable payment.
 * It therefore requires independent behavioral and performance qualification
 * before any production adoption.
 */
internal object FocalSourceChoiceSampler {
    const val MAX_EXPLICIT_CHOICES = 16

    fun combinations(sourceCount: Int, chooseCount: Int): List<List<Int>> {
        require(sourceCount >= 0 && chooseCount in 1..sourceCount)
        if (isExhaustiveWithinBound(sourceCount, chooseCount)) {
            val full = ArrayList<List<Int>>()
            val chosen = ArrayList<Int>(chooseCount)
            fun addAll(start: Int, remaining: Int) {
                if (remaining == 0) {
                    full.add(chosen.toList())
                    return
                }
                for (index in start..sourceCount - remaining) {
                    chosen.add(index)
                    addAll(index + 1, remaining - 1)
                    chosen.removeAt(chosen.lastIndex)
                }
            }
            addAll(0, chooseCount)
            return full
        }

        val sampled = LinkedHashSet<List<Int>>(MAX_EXPLICIT_CHOICES)
        fun include(raw: List<Int>) {
            if (sampled.size == MAX_EXPLICIT_CHOICES) return
            val choice = raw.sorted()
            if (choice.size == chooseCount && choice.distinct().size == chooseCount &&
                choice.all { it in 0 until sourceCount }) sampled.add(choice)
        }

        // Keep the two original enumeration extremes. Each has a different
        // set of lands untapped for a possible follow-up spell.
        include((0 until chooseCount).toList())
        include((sourceCount - chooseCount until sourceCount).toList())

        // Rotating contiguous payment windows provide inclusion/exclusion
        // coverage even when copies of a land occur together in source order.
        val windows = minOf(sourceCount, 8)
        for (slot in 0 until windows) {
            val start = slot * sourceCount / windows
            include((0 until chooseCount).map { (start + it) % sourceCount })
        }

        // Hybrid bookend payments preserve some candidates that leave either
        // the earliest or latest sources available for the next spell.
        for (head in 1 until chooseCount) {
            if (sampled.size == MAX_EXPLICIT_CHOICES) break
            include((0 until head).toList() +
                (sourceCount - (chooseCount - head) until sourceCount).toList())
        }

        // Interleaved candidates cover mixed sources rather than only adjacent
        // source runs. No randomness, time-based cutoff, or exhaustive materialization.
        for (stride in listOf(3, 5, 7, 2)) {
            if (sampled.size == MAX_EXPLICIT_CHOICES) break
            for (offset in 0 until sourceCount) {
                if (sampled.size == MAX_EXPLICIT_CHOICES) break
                val proposed = ArrayList<Int>(chooseCount)
                val seen = HashSet<Int>(chooseCount)
                for (j in 0 until sourceCount) {
                    val idx = ((offset.toLong() + j.toLong() * stride) % sourceCount).toInt()
                    if (seen.add(idx)) proposed.add(idx)
                    if (proposed.size == chooseCount) break
                }
                include(proposed)
            }
        }
        return sampled.toList()
    }

    private fun isExhaustiveWithinBound(n: Int, k: Int): Boolean {
        val smaller = minOf(k, n - k)
        var count = 1L
        for (i in 1..smaller) {
            count = count * (n - smaller + i) / i
            if (count > MAX_EXPLICIT_CHOICES) return false
        }
        return true
    }
}
