package com.wingedsheep.gym.pest

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe

/**
 * Non-behavioral preflight for the production Phase-B registry and deterministic fixtures.
 *
 * This test resolves the frozen deck definitions and constructs deterministic fixtures directly;
 * it does not consume accepted comparison inputs or enter the bank comparison path.
 */
class PestPhaseBProductionRegistryPreflightTest : FunSpec({
    test("production registry resolves every frozen Phase-B card including basics") {
        val registry = PestPhaseBProductionRegistry.build()
        val requiredNames = listOf("pest", "monster")
            .flatMap { deck -> PestPhaseBPhysicalPlan.spec(deck).entries.map { it.first } }
            .toSet()

        requiredNames shouldContain "Forest"
        requiredNames.forEach { name ->
            registry.requireCard(name).name shouldBe name
        }
        registry.requireCard("Forest").name shouldBe "Forest"
    }

    test("production registry constructs one deterministic fixture from each frozen deck") {
        val registry = PestPhaseBProductionRegistry.build()

        listOf("pest", "monster").forEach { deck ->
            val row = PestPhaseBPhysicalPlan.rows(deck).first()
            val fixture = PestPhaseBDeterministicFixtureFactory.build(registry, row)

            fixture.orderedHandIds.size shouldBe 7
            fixture.orderedHandIds.distinct().size shouldBe 7
            fixture.canonicalLibraryIds.size shouldBe 53
            (fixture.orderedHandIds + fixture.canonicalLibraryIds).distinct().size shouldBe 60
        }
    }
})
