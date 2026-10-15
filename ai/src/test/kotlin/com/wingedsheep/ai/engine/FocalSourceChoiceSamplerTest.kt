package com.wingedsheep.ai.engine

import io.kotest.core.spec.style.FunSpec

/** Excluded source-choice sampler tests; no engine, state, case or gameplay. */
class FocalSourceChoiceSamplerTest : FunSpec({
    fun exhaustive(n: Int, k: Int): List<List<Int>> {
        val out = mutableListOf<List<Int>>()
        val selected = mutableListOf<Int>()
        fun collect(start: Int, count: Int) {
            if (count == 0) { out += selected.toList(); return }
            for (i in start..n-count) {
                selected.add(i)
                collect(i+1, count-1)
                selected.removeAt(selected.lastIndex)
            }
        }
        collect(0,k)
        return out
    }
    test("small combinations preserve every original payment choice and order") {
        for (n in 1..8) for (k in 1..n) {
            val old = exhaustive(n,k)
            if (old.size <= FocalSourceChoiceSampler.MAX_EXPLICIT_CHOICES) {
                check(FocalSourceChoiceSampler.combinations(n,k) == old)
            }
        }
    }
    test("exact bound retains all 16 singleton payments") {
        check(FocalSourceChoiceSampler.combinations(16,1) == exhaustive(16,1))
    }
    test("large 16-choose-8 board stays at or below 16 representatives") {
        val options = FocalSourceChoiceSampler.combinations(16,8)
        check(options.isNotEmpty() && options.size <= 16)
        check(options.all { it.size == 8 })
    }
    test("original lexicographic first and last payments remain represented") {
        for ((n,k) in listOf(9 to 4, 16 to 8, 20 to 10, 33 to 1)) {
            val sampled = FocalSourceChoiceSampler.combinations(n,k)
            check((0 until k).toList() in sampled)
            check((n-k until n).toList() in sampled)
        }
    }
    test("dense payment sampling can use or reserve every source") {
        val sampled = FocalSourceChoiceSampler.combinations(16,8)
        for (i in 0 until 16) {
            check(sampled.any { i in it })
            check(sampled.any { i !in it })
        }
    }
    test("samples are unique sorted valid and stable") {
        for ((n,k) in listOf(9 to 4, 16 to 8, 20 to 10, 7 to 6, 64 to 31)) {
            val actual = FocalSourceChoiceSampler.combinations(n,k)
            check(actual == FocalSourceChoiceSampler.combinations(n,k))
            check(actual.size == actual.distinct().size)
            check(actual.all { c -> c == c.sorted() && c.distinct().size == k && c.all { it in 0 until n } })
        }
    }
    test("complete same-source payment remains exact") {
        check(FocalSourceChoiceSampler.combinations(30,30) == listOf((0 until 30).toList()))
    }
    test("large source counts never expand exponential lists") {
        val options = FocalSourceChoiceSampler.combinations(75,33)
        check(options.isNotEmpty() && options.size <= FocalSourceChoiceSampler.MAX_EXPLICIT_CHOICES)
    }
    test("large candidate sample includes mixed-source alternatives") {
        val options = FocalSourceChoiceSampler.combinations(16,8)
        check(options.any { c -> c.zipWithNext().count { (a,b) -> b-a > 1 } >= 2 })
    }
    test("invalid input is rejected before source enumeration") {
        for ((n,k) in listOf(0 to 0, 4 to 0, 3 to 4, -1 to 1)) {
            check(runCatching { FocalSourceChoiceSampler.combinations(n,k) }.isFailure)
        }
    }
})
