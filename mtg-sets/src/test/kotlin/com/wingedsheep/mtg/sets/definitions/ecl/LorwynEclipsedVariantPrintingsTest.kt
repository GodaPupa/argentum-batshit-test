package com.wingedsheep.mtg.sets.definitions.ecl

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

/**
 * Validates the Lorwyn Eclipsed showcase / borderless variant printings wiring: the set opts into
 * the booster variant slot, every variant row is alternate-frame, names a real ECL card, and is
 * stamped with the ECL set code. Guards against a regenerated Scryfall dump drifting from the
 * implemented card pool.
 */
class LorwynEclipsedVariantPrintingsTest : FunSpec({

    val set = LorwynEclipsedSet
    val cardNames = set.cards.map { it.name }.toSet()
    val variants = set.printings.filter { it.isAlternateFrame }

    test("set opts into the booster variant slot at 15%") {
        set.boosterVariantChance shouldBe 0.15
    }

    test("contributes a non-empty pool of alternate-frame printings") {
        variants.size shouldBeGreaterThan 0
    }

    test("every variant printing is showcase or borderless") {
        variants.filterNot { it.isAlternateFrame }.shouldBeEmpty()
    }

    test("every variant printing names a real ECL card and carries the set code") {
        val orphans = variants.filter { it.name !in cardNames }.map { it.name }
        orphans shouldBe emptyList()
        variants.filterNot { it.setCode == "ECL" }.shouldBeEmpty()
    }

    test("excludes collector-only treatments: reversible shocklands, Japanese Showcase, Fracture Foil, serialized") {
        // The reversible shockland printings are the only variant treatment those land names have,
        // so excluding them must leave the shocklands with no variant row at all.
        val reversibleShocklands = setOf(
            "Blood Crypt", "Hallowed Fountain", "Overgrown Tomb", "Steam Vents", "Temple Garden",
        )
        variants.map { it.name }.filter { it in reversibleShocklands }.shouldBeEmpty()
        // Collector-only blocks by collector number: the serialized/headliner chase card (#352) and
        // the Japanese Showcase / Fracture Foil prints (#382–401). None survive.
        variants.filter { it.collectorNumber == "352" }.shouldBeEmpty()
        variants.filter { it.collectorNumber.toIntOrNull()?.let { n -> n in 382..401 } == true }.shouldBeEmpty()
    }
})
