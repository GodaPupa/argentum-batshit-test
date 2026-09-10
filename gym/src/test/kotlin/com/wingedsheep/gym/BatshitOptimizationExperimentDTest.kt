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

/** Optimization Experiment D: Variant C incumbent, then -1 NDAA / +1 Greedy Freebooter. */
class BatshitOptimizationExperimentDTest : FunSpec({
    val enabled = System.getenv("BATSHIT_OPTIMIZATION_D") == "true"
    val difference = DeclaredDeckDifference(
        mainboardRemoved = mapOf("Not Dead After All" to 1),
        mainboardAdded = mapOf("Greedy Freebooter" to 1),
    )

    test("Experiment D declares exactly one NDAA removal and one Freebooter addition from incumbent C") {
        val incumbent = variantC(batshitDeck())
        val variant = variantD(incumbent)
        incumbent.cards.count { it == "Village Rites" } shouldBe 2
        variant.cards.count { it == "Village Rites" } shouldBe 2
        incumbent.cards.count { it == "Cast Down" } shouldBe 2
        variant.cards.count { it == "Cast Down" } shouldBe 2
        incumbent.cards.count { it == "Not Dead After All" } shouldBe 3
        variant.cards.count { it == "Not Dead After All" } shouldBe 2
        incumbent.cards.count { it == "Greedy Freebooter" } shouldBe 0
        variant.cards.count { it == "Greedy Freebooter" } shouldBe 1
        assertOnlyDeclaredDeckDifference(incumbent, variant, difference)
    }

    test("100 frozen paired seeds for incumbent C and Variant D").config(
        enabled = enabled,
        timeout = 90.minutes,
    ) {
        val seedPath = Path.of("src", "test", "resources", "batshit-optimization-d-seeds.csv")
        val seeds = readExperimentDSeeds(seedPath)
        seeds.size shouldBe 100
        seeds.distinct().size shouldBe 100
        seeds.none(previouslyUsedExperimentDSeeds(seedPath)::contains).shouldBeTrue()

        val incumbent = variantC(batshitDeck())
        val variant = variantD(incumbent)
        assertOnlyDeclaredDeckDifference(incumbent, variant, difference)
        val framework = BatshitPairedCounterfactualFramework(
            registry = fullRegistry(),
            control = incumbent,
            variant = variant,
            declaredDifference = difference,
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
            assertRawPlayAndCastLinesAreTurnStamped(pair.control.log)
            assertRawPlayAndCastLinesAreTurnStamped(pair.variant.log)
        }

        val output = Path.of("build", "reports", "batshit-optimization-d")
        framework.writeArtifacts(results, output)
        Files.copy(
            seedPath,
            output.resolve(seedPath.fileName),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
    }
})

private fun variantD(incumbent: Deck): Deck = incumbent.copy(
    cards = incumbent.cards.toMutableList().also { cards ->
        val index = cards.indexOfLast { it == "Not Dead After All" }
        check(index >= 0) { "Variant C incumbent is missing Not Dead After All" }
        cards[index] = "Greedy Freebooter"
    },
)

private fun readExperimentDSeeds(path: Path): List<Long> = Files.readAllLines(path)
    .drop(1).filter(String::isNotBlank).map { it.substringAfterLast(',').toLong() }

private fun previouslyUsedExperimentDSeeds(current: Path): Set<Long> {
    val csvSeeds = Files.list(current.parent).use { paths ->
        paths.filter { it != current && it.name.contains("seed") && it.name.endsWith(".csv") }
            .toList().flatMap(::readExperimentDSeeds).toSet()
    }
    return csvSeeds + setOf(
        0xBA75_0001L, 0xBA75_0002L, 0xBA75_0003L, 0xBA75_0004L, 0xBA75_0005L,
        0x033D_F483_8D71_94AL, 0x0B97_FCDF_E979_9C87L, 0x0ED3_B535_8E12_DED3L,
        0x0AB1_B251_D164_E979L, 0x0A48_B5B9_0FAF_A44CL, 0x0679_B6F9_EEF7_74BCL,
        0x0766_3C65_A87D_6DD5L, 0x03A2_548F_BBB2_DECL, 0x0F3B_7E43_F328_8345L,
        0x0906_6DF1_0204_FD56L,
    )
}

