package com.wingedsheep.gym.pest

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import java.nio.file.Files

class PestPhaseBV5DiagnosticRegressionTest : FunSpec({
    test("preserved first-mismatch ordinal 9910 requires every frozen M4 color") {
        val spec = PestPhaseBPhysicalPlan.spec("pest")
        val (ordinal, counts) = PestPhaseBPhysicalPlan.nameMultisets("pest")
            .first { it.first == 9_910L }

        ordinal shouldBe 9_910L
        val observed = buildMap {
            counts.forEachIndexed { index, count ->
                if (count > 0) put(spec.entries[index].first, count)
            }
        }
        observed shouldBe mapOf(
            "Pest Mascot" to 1,
            "Weather the Storm" to 1,
            "Chainer's Edict" to 2,
            "Forest" to 3,
        )

        val hand = listOf(
            PestLondonCardFacts("forest-1", "Forest", true, 0, colorsProduced = setOf('G')),
            PestLondonCardFacts("forest-2", "Forest", true, 0, colorsProduced = setOf('G')),
            PestLondonCardFacts("forest-3", "Forest", true, 0, colorsProduced = setOf('G')),
            PestLondonCardFacts(
                "mascot", "Pest Mascot", false, 3,
                colorsRequired = setOf('B', 'G'),
                deterministicDevelopmentPayable = true,
            ),
            PestLondonCardFacts(
                "weather", "Weather the Storm", false, 2,
                colorsRequired = setOf('G'),
                deterministicDevelopmentPayable = true,
            ),
            PestLondonCardFacts(
                "edict-1", "Chainer's Edict", false, 2,
                colorsRequired = setOf('B'),
                deterministicDevelopmentPayable = false,
            ),
            PestLondonCardFacts(
                "edict-2", "Chainer's Edict", false, 2,
                colorsRequired = setOf('B'),
                deterministicDevelopmentPayable = false,
            ),
        )
        val submitted = spec.entries.toMap()
        val vector = PestMonsterLondonPredicateVectorExtractor.extract(
            submittedDeck = submitted,
            hand = hand,
            mulliganCount = 0,
            cardsToBottom = 0,
        )

        vector.physicalLandCount shouldBe 3
        vector.earlySpellIds.size shouldBe 4
        vector.developmentFunctional shouldBe true
        vector.colorFunctional shouldBe false
        PestPhaseBKeepBottomActor.keep(vector).keep shouldBe false
    }

    test("journal preserves non-null lawful keep and bottom instead of overwriting UNKNOWN") {
        val root = Files.createTempDirectory("pest-phase-b-v5-journal").toRealPath()
        val context = PestPhaseBRowContext(
            deck = "pest",
            seat = 0,
            onPlay = false,
            mulligans = 0,
            nameMultisetOrdinal = 9_910L,
            physicalRepresentativeOrdinal = 0L,
        )
        val ids = (1..7).map { "visible-$it" }
        val stateSha = "a".repeat(64)
        val journal = PestPhaseBComparisonJournal.create(
            root = root,
            runId = "diagnostic",
            sourceCommit = "0".repeat(40),
            planSha256 = "b".repeat(64),
            expectedRows = 1,
        )
        try {
            journal.compare(
                row = PestPhaseBPlannedRow(context, emptyList()),
                fixtureStateSha256 = stateSha,
                orderedHandIds = ids,
            ) {
                PestPhaseBRowComparison(
                    context = context,
                    orderedHandIds = ids,
                    orderedHandNames = listOf(
                        "Forest", "Forest", "Forest", "Pest Mascot",
                        "Weather the Storm", "Chainer's Edict", "Chainer's Edict",
                    ),
                    stateSha256 = stateSha,
                    rawKeep = false,
                    lawfulKeep = true,
                    rawBottomIds = emptyList(),
                    lawfulBottomIds = emptyList(),
                    keepReason = "M1_M2_M4_M5_COMPOSITION",
                    bottomReason = "B2_EXCESS_THEN_B3_COST_THEN_B4_PHYSICAL",
                )
            }
            journal.finish()
        } finally {
            journal.close()
        }

        val text = Files.readString(root.resolve("diagnostic").resolve("journal.txt"))
        text shouldContain "\"lawful_keep\":true"
        text shouldContain "\"lawful_bottom\":[]"
        text shouldNotContain "\"lawful_keep\":\"UNKNOWN\""
        text shouldNotContain "\"lawful_bottom\":\"UNKNOWN\""
    }
})
