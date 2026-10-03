package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.sdk.scripting.targets.TargetRequirement

/** Pure announcement structure. Never consults target legality to guess a target's group. */
object AnnouncedTargetGroups {
    fun counts(requirements: List<TargetRequirement>, total: Int, declared: List<Int>? = null,
               maxima: List<Int> = requirements.map { if (it.unlimited || (it is com.wingedsheep.sdk.scripting.targets.TargetObject && it.dynamicMaxCount != null)) total else it.count }): List<Int>? {
        if (maxima.size != requirements.size || total < 0) return null
        fun valid(values: List<Int>) = values.size == requirements.size && values.sumOf { it.toLong() } == total.toLong() &&
            values.indices.all { values[it] >= requirements[it].effectiveMinCount && values[it] <= maxima[it] }
        if (declared != null) return declared.takeIf(::valid)
        // Legacy flat callers are accepted only if the cardinalities have exactly one solution.
        // Two solutions require an explicit announcement, even if only one matches today's board.
        var solutions = mapOf(0 to listOf(emptyList<Int>()))
        for (i in requirements.indices) {
            val next = mutableMapOf<Int, MutableList<List<Int>>>()
            for ((used, paths) in solutions) {
                val min = requirements[i].effectiveMinCount
                val max = minOf(maxima[i], total - used)
                for (count in min..max) {
                    val out = next.getOrPut(used + count) { mutableListOf() }
                    for (path in paths) if (out.size < 2) out += path + count
                }
            }
            solutions = next
        }
        return solutions[total]?.singleOrNull()
    }
}
