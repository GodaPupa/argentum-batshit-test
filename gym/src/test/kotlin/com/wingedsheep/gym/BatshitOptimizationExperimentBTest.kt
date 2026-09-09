package com.wingedsheep.gym

import com.wingedsheep.sdk.model.Deck
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.time.Duration.Companion.minutes

/** Optimization Experiment B: -1 Razortrap Gorge / +1 Mountain. */
class BatshitOptimizationExperimentBTest : FunSpec({
    val enabled = System.getenv("BATSHIT_OPTIMIZATION_B") == "true"

    test("Experiment B declares exactly one Gorge removal and one Mountain addition") {
        val control = batshitDeck()
        val variant = variantB(control)
        control.cards.count { it == "Razortrap Gorge" } shouldBe 4
        variant.cards.count { it == "Razortrap Gorge" } shouldBe 3
        variant.cards.count { it == "Mountain" } shouldBe 10
        assertOnlyDeclaredDeckDifference(control, variant, experimentBDifference)
    }

    test("paired raw trace contains complete mana-development inputs") {
        val trace = playLoggedGame(fullRegistry(), 1, 0xBEEFB002L, 0).log
        listOf("Kept Batshit:", "Play/draw:", "TURN 1", "Tapped Razortrap Gorge entries:").forEach {
            trace.contains(it).shouldBeTrue()
        }
        // Every land and spell play is turn-stamped; Gorge entries additionally report tapped state.
        trace.lineSequence().filter { it.contains(" play ") }.all { it.startsWith("T") }.shouldBeTrue()
        trace.lineSequence().filter { it.contains(" cast ") }.all { it.startsWith("T") }.shouldBeTrue()
    }

    test("100 frozen paired seeds for control and Variant B").config(
        enabled = enabled,
        timeout = 90.minutes,
    ) {
        val seedPath = Path.of("src", "test", "resources", "batshit-optimization-b-seeds.csv")
        val seeds = readExperimentBSeeds(seedPath)
        seeds.size shouldBe 100
        seeds.distinct().size shouldBe 100
        seeds.none(previouslyUsedOptimizationSeeds(seedPath)::contains).shouldBeTrue()

        val control = batshitDeck()
        val variant = variantB(control)
        assertOnlyDeclaredDeckDifference(control, variant, experimentBDifference)
        val framework = BatshitPairedCounterfactualFramework(
            registry = fullRegistry(), control = control, variant = variant,
            declaredDifference = experimentBDifference,
        )
        val results = framework.run(seeds)
        results.size shouldBe 100
        results.forEach { pair ->
            pair.control.completed.shouldBeTrue()
            pair.variant.completed.shouldBeTrue()
            pair.control.actions shouldBeGreaterThan 0
            pair.variant.actions shouldBeGreaterThan 0
            assertTriggerSummaryMatchesRawEvents(pair.control.log)
            assertTriggerSummaryMatchesRawEvents(pair.variant.log)
        }
        val output = Path.of("build", "reports", "batshit-optimization-b")
        framework.writeArtifacts(results, output)
        Files.copy(seedPath, output.resolve(seedPath.fileName), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
})

private val experimentBDifference = DeclaredDeckDifference(
    mainboardRemoved = mapOf("Razortrap Gorge" to 1),
    mainboardAdded = mapOf("Mountain" to 1),
)

internal fun variantB(control: Deck): Deck = control.copy(
    cards = control.cards.toMutableList().also { cards ->
        val index = cards.indexOfLast { it == "Razortrap Gorge" }
        check(index >= 0) { "Frozen control is missing Razortrap Gorge" }
        cards[index] = "Mountain"
    },
)

private fun readExperimentBSeeds(path: Path): List<Long> = Files.readAllLines(path)
    .drop(1).filter(String::isNotBlank).map { it.substringAfterLast(',').toLong() }

private fun previouslyUsedOptimizationSeeds(current: Path): Set<Long> {
    val csvSeeds = Files.list(current.parent).use { paths ->
        paths.filter { it != current && it.name.contains("seed") && it.name.endsWith(".csv") }
            .toList().flatMap(::readExperimentBSeeds).toSet()
    }
    return csvSeeds + setOf(
        0xBA75_0001L, 0xBA75_0002L, 0xBA75_0003L, 0xBA75_0004L, 0xBA75_0005L,
        0x033D_F483_8D71_94AL, 0x0B97_FCDF_E979_9C87L, 0x0ED3_B535_8E12_DED3L,
        0x0AB1_B251_D164_E979L, 0x0A48_B5B9_0FAF_A44CL, 0x0679_B6F9_EEF7_74BCL,
        0x0766_3C65_A87D_6DD5L, 0x03A2_548F_BBB2_DECL, 0x0F3B_7E43_F328_8345L,
        0x0906_6DF1_0204_FD56L,
    )
}
