package com.wingedsheep.gym.pest

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PestPhaseBProtectedRemovalRegressionTest : FunSpec({
    fun card(spec: PestPhaseBDeckSpec, name: String, copy: Int): PestPhaseBPhysicalCard {
        val index = spec.entries.indexOfFirst { it.first == name }
        require(index >= 0)
        return PestPhaseBPhysicalCard(index, copy, name)
    }

    test("reviewer counterexample exposes Ent copies one two and three after one protected removal") {
        val spec = PestPhaseBPhysicalPlan.spec("pest")
        val hand = listOf(
            card(spec, "Forest", 1),
            card(spec, "Generous Ent", 1),
            card(spec, "Generous Ent", 2),
            card(spec, "Generous Ent", 3),
            card(spec, "Essence Warden", 1),
            card(spec, "Essence Warden", 2),
            card(spec, "Essence Warden", 3),
        )
        val bottomed = PestPhaseBPhysicalPlan.physicalRepresentatives(spec, hand, 1)
            .mapNotNull { ordered ->
                val ents = ordered.filter { it.name == "Generous Ent" }
                ents.getOrNull(1)?.copyOrdinal
            }.toSet()
        bottomed shouldBe setOf(1, 2, 3)
    }

    test("one-position protection shift is generic across equal-CMC physical identities") {
        val spec = PestPhaseBPhysicalPlan.spec("pest")
        val hand = listOf(
            card(spec, "Forest", 1),
            card(spec, "Pest Mascot", 1),
            card(spec, "Pest Mascot", 2),
            card(spec, "Pest Mascot", 3),
            card(spec, "Essence Warden", 1),
            card(spec, "Essence Warden", 2),
            card(spec, "Essence Warden", 3),
        )
        val second = PestPhaseBPhysicalPlan.physicalRepresentatives(spec, hand, 1)
            .mapNotNull { ordered ->
                ordered.filter { it.name == "Pest Mascot" }.getOrNull(1)?.copyOrdinal
            }.toSet()
        second shouldBe setOf(1, 2, 3)
    }
})
