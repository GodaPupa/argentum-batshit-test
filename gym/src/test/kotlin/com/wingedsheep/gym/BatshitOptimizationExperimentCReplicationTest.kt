package com.wingedsheep.gym

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import kotlin.time.Duration.Companion.minutes

/** Independent replication of Optimization Experiment C: -1 Village Rites / +1 Shambling Ghast. */
class BatshitOptimizationExperimentCReplicationTest : FunSpec({
    val enabled = System.getenv("BATSHIT_OPTIMIZATION_C_REPLICATION") == "true"
    val difference = DeclaredDeckDifference(
        mainboardRemoved = mapOf("Village Rites" to 1),
        mainboardAdded = mapOf("Shambling Ghast" to 1),
    )

    test("Experiment C replication preserves the exact declared deck difference") {
        val control = batshitDeck()
        val variant = variantC(control)
        control.cards.count { it == "Village Rites" } shouldBe 3
        variant.cards.count { it == "Village Rites" } shouldBe 2
        control.cards.count { it == "Shambling Ghast" } shouldBe 3
        variant.cards.count { it == "Shambling Ghast" } shouldBe 4
        assertOnlyDeclaredDeckDifference(control, variant, difference)
    }

    test("100 frozen paired seeds for Experiment C replication").config(
        enabled = enabled,
        timeout = 90.minutes,
    ) {
        val seedPath = Path.of(
            "src", "test", "resources", "batshit-optimization-c-replication-seeds.csv"
        )
        val seeds = readReplicationSeeds(seedPath)
        seeds.size shouldBe 100
        seeds.distinct().size shouldBe 100
        seeds.none(previouslyUsedReplicationSeeds(seedPath)::contains).shouldBeTrue()

        val control = batshitDeck()
        val variant = variantC(control)
        assertOnlyDeclaredDeckDifference(control, variant, difference)
        val framework = BatshitPairedCounterfactualFramework(
            registry = fullRegistry(),
            control = control,
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

        val output = Path.of("build", "reports", "batshit-optimization-c-replication")
        framework.writeArtifacts(results, output)
        Files.copy(
            seedPath,
            output.resolve(seedPath.fileName),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING,
        )
    }
})

private fun readReplicationSeeds(path: Path): List<Long> = Files.readAllLines(path)
    .drop(1).filter(String::isNotBlank).map { it.substringAfterLast(',').toLong() }

private fun previouslyUsedReplicationSeeds(current: Path): Set<Long> {
    val csvSeeds = Files.list(current.parent).use { paths ->
        paths.filter { it != current && it.name.contains("seed") && it.name.endsWith(".csv") }
            .toList().flatMap(::readReplicationSeeds).toSet()
    }
    return csvSeeds + setOf(
        0xBA75_0001L, 0xBA75_0002L, 0xBA75_0003L, 0xBA75_0004L, 0xBA75_0005L,
        0x033D_F483_8D71_94AL, 0x0B97_FCDF_E979_9C87L, 0x0ED3_B535_8E12_DED3L,
        0x0AB1_B251_D164_E979L, 0x0A48_B5B9_0FAF_A44CL, 0x0679_B6F9_EEF7_74BCL,
        0x0766_3C65_A87D_6DD5L, 0x03A2_548F_BBB2_DECL, 0x0F3B_7E43_F328_8345L,
        0x0906_6DF1_0204_FD56L,
    )
}
