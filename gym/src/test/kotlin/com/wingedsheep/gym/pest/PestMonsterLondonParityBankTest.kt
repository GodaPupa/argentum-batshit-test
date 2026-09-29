package com.wingedsheep.gym.pest

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/** Bank-geometry qualification only; no raw-controller parity is asserted here. */
class PestMonsterLondonParityBankTest : FunSpec({
    val deck = mapOf("Forest" to 12, "Generous Ent" to 4, "Other" to 44)

    fun hand(prefix: String) = listOf(
        PestLondonCardFacts("${prefix}f1", "Forest", true, 0, colorsProduced = setOf('G')),
        PestLondonCardFacts("${prefix}e1", "Generous Ent", false, 6,
            typedCyclingTargets = setOf("Forest"), typedCyclingPayableBySoleLand = true,
            deterministicDevelopmentPayable = false),
        PestLondonCardFacts("${prefix}o1", "Other", false, 2, colorsRequired = setOf('G'),
            deterministicDevelopmentPayable = true),
        PestLondonCardFacts("${prefix}o2", "Other", false, 2, colorsRequired = setOf('G'),
            deterministicDevelopmentPayable = true),
        PestLondonCardFacts("${prefix}o3", "Other", false, 4, deterministicDevelopmentPayable = false),
        PestLondonCardFacts("${prefix}o4", "Other", false, 5, deterministicDevelopmentPayable = false),
        PestLondonCardFacts("${prefix}o5", "Other", false, 6, deterministicDevelopmentPayable = false),
    )

    test("physical handle renaming preserves the logical eleven-axis equivalence class") {
        val a = hand("a")
        val b = hand("b")
        val av = PestMonsterLondonPredicateVectorExtractor.extract(deck, a, 0, 0)
        val bv = PestMonsterLondonPredicateVectorExtractor.extract(deck, b, 0, 0)
        PestMonsterLondonParityBank.logicalKey(0, true, 0, a, av) shouldBe
            PestMonsterLondonParityBank.logicalKey(0, true, 0, b, bv)
        PestMonsterLondonParityBank.physicalKey(a, av) shouldNotBe
            PestMonsterLondonParityBank.physicalKey(b, bv)
    }

    test("same-name duplicate physical-order permutations stay separate inside one logical group") {
        val a = hand("x")
        val swapped = a.toMutableList().also { list ->
            val i = list.indexOfFirst { it.id == "xo1" }
            val j = list.indexOfFirst { it.id == "xo2" }
            val tmp = list[i]; list[i] = list[j]; list[j] = tmp
        }
        val av = PestMonsterLondonPredicateVectorExtractor.extract(deck, a, 0, 1)
        val sv = PestMonsterLondonPredicateVectorExtractor.extract(deck, swapped, 0, 1)
        val logicalA = PestMonsterLondonParityBank.logicalKey(1, false, 0, a, av)
        val logicalS = PestMonsterLondonParityBank.logicalKey(1, false, 0, swapped, sv)
        logicalA shouldBe logicalS
        val grouped = PestMonsterLondonParityBank.group(listOf(
            Triple(logicalA, PestMonsterLondonParityBank.physicalKey(a, av), "P1"),
            Triple(logicalS, PestMonsterLondonParityBank.physicalKey(swapped, sv), "P2"),
        ))
        grouped.size shouldBe 1
        grouped.single().physicalPermutations.size shouldBe 2
    }

    test("different deterministic development certificates cannot collapse into one logical key") {
        val yes = hand("d")
        val no = yes.map {
            if (it.name == "Other") it.copy(deterministicDevelopmentPayable = false) else it
        }
        val yv = PestMonsterLondonPredicateVectorExtractor.extract(deck, yes, 0, 0)
        val nv = PestMonsterLondonPredicateVectorExtractor.extract(deck, no, 0, 0)
        yv.developmentFunctional shouldBe true
        nv.developmentFunctional shouldBe false
        PestMonsterLondonParityBank.logicalKey(0, true, 0, yes, yv) shouldNotBe
            PestMonsterLondonParityBank.logicalKey(0, true, 0, no, nv)
    }

    test("seat start and mulligan context remain part of the equivalence key") {
        val h = hand("c")
        val v = PestMonsterLondonPredicateVectorExtractor.extract(deck, h, 0, 0)
        val a = PestMonsterLondonParityBank.logicalKey(0, true, 0, h, v)
        PestMonsterLondonParityBank.logicalKey(1, true, 0, h, v) shouldNotBe a
        PestMonsterLondonParityBank.logicalKey(0, false, 0, h, v) shouldNotBe a
        PestMonsterLondonParityBank.logicalKey(0, true, 1, h, v) shouldNotBe a
    }
})
