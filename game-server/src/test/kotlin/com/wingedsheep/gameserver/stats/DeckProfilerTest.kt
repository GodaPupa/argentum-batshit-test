package com.wingedsheep.gameserver.stats

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DeckProfilerTest : FunSpec({

    fun creature(name: String, cost: String, set: String?) =
        CardDefinition.creature(name, ManaCost.parse(cost), setOf(Subtype("Human")), 1, 1).copy(setCode = set)

    val registry = CardRegistry().apply {
        register(listOf(
            creature("Whitey", "{W}", "AAA"),
            creature("Bluey", "{U}", "BBB"),
            creature("Greeny", "{1}{G}", "AAA"),
            creature("Colorless Golem", "{2}", "CCC"),
        ))
    }
    val profiler = DeckProfiler(registry)

    test("colors are emitted in WUBRG order and sets are distinct") {
        val profile = profiler.profile(listOf("Bluey", "Whitey", "Whitey", "Greeny"))
        profile.colors shouldBe "WUG"
        profile.setCodes shouldBe "BBB,AAA" // insertion order of distinct sets as cards are seen
    }

    test("a colorless deck yields an empty color string") {
        profiler.profile(listOf("Colorless Golem")).colors shouldBe ""
    }

    test("unknown cards are skipped and the fallback set is used when none resolve") {
        val profile = profiler.profile(listOf("Not A Real Card"), fallbackSetCode = "FALLBACK")
        profile.colors shouldBe ""
        profile.setCodes shouldBe "FALLBACK"
    }

    test("collector-number pins resolve to the canonical card") {
        profiler.profile(listOf("Whitey#123")).colors shouldBe "W"
    }
})
