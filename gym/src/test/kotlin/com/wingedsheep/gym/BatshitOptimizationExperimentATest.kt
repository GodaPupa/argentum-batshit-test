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

/**
 * Optimization Experiment A: frozen Batshit Economics control versus exactly one variant,
 * -1 Not Dead After All / +1 Vampire's Kiss. This opt-in test is the only execution entry point.
 */
class BatshitOptimizationExperimentATest : FunSpec({
    val enabled = System.getenv("BATSHIT_OPTIMIZATION_A") == "true"

    test("Experiment A declares exactly one NDAA removal and one Vampire's Kiss addition") {
        val control = batshitDeck()
        val variant = variantA(control)
        control.cards.count { it == "Not Dead After All" } shouldBe 3
        variant.cards.count { it == "Not Dead After All" } shouldBe 2
        variant.cards.count { it == "Vampire's Kiss" } shouldBe 1
        assertOnlyDeclaredDeckDifference(
            control,
            variant,
            DeclaredDeckDifference(
                mainboardRemoved = mapOf("Not Dead After All" to 1),
                mainboardAdded = mapOf("Vampire's Kiss" to 1),
            ),
        )
    }

    test("100 frozen paired seeds for control and Variant A").config(
        enabled = enabled,
        timeout = 90.minutes,
    ) {
        val seedPath = Path.of("src", "test", "resources", "batshit-optimization-a-seeds.csv")
        val seeds = readSeedVector(seedPath)
        seeds.size shouldBe 100
        seeds.distinct().size shouldBe 100
        seeds.none(previouslyUsedSeeds(seedPath)::contains).shouldBeTrue()

        val control = batshitDeck()
        val variant = variantA(control)
        val declaredDifference = DeclaredDeckDifference(
            mainboardRemoved = mapOf("Not Dead After All" to 1),
            mainboardAdded = mapOf("Vampire's Kiss" to 1),
        )
        assertOnlyDeclaredDeckDifference(control, variant, declaredDifference)

        val framework = BatshitPairedCounterfactualFramework(
            registry = fullRegistry(),
            control = control,
            variant = variant,
            declaredDifference = declaredDifference,
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

        val outputDirectory = Path.of("build", "reports", "batshit-optimization-a")
        framework.writeArtifacts(results, outputDirectory)
        Files.copy(seedPath, outputDirectory.resolve(seedPath.fileName), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
})

internal fun variantA(control: Deck): Deck = control.copy(
    cards = control.cards.toMutableList().also { cards ->
        val index = cards.indexOfLast { it == "Not Dead After All" }
        check(index >= 0) { "Frozen control is missing Not Dead After All" }
        cards[index] = "Vampire's Kiss"
    },
)

private fun readSeedVector(path: Path): List<Long> = Files.readAllLines(path)
    .drop(1)
    .filter(String::isNotBlank)
    .map { line -> line.substringAfterLast(',').toLong() }

private fun previouslyUsedSeeds(currentSeedPath: Path): Set<Long> {
    val resourceDirectory = currentSeedPath.parent
    val csvSeeds = Files.list(resourceDirectory).use { paths ->
        paths.filter { path ->
            path != currentSeedPath && path.name.contains("seed") && path.name.endsWith(".csv")
        }.toList().flatMap(::readSeedVector).toSet()
    }
    val developmentAndSmokeSeeds = setOf(
        0xBA75_0001L, 0xBA75_0002L, 0xBA75_0003L, 0xBA75_0004L, 0xBA75_0005L,
        0x033D_F483_8D71_94AL, 0x0B97_FCDF_E979_9C87L, 0x0ED3_B535_8E12_DED3L,
        0x0AB1_B251_D164_E979L, 0x0A48_B5B9_0FAF_A44CL, 0x0679_B6F9_EEF7_74BCL,
        0x0766_3C65_A87D_6DD5L, 0x03A2_548F_BBB2_DECL, 0x0F3B_7E43_F328_8345L,
        0x0906_6DF1_0204_FD56L,
    )
    return csvSeeds + developmentAndSmokeSeeds
}
