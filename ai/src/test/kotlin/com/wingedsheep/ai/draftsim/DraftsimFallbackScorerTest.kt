package com.wingedsheep.ai.draftsim

import com.wingedsheep.ai.llm.CardSummary
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain

/**
 * Stage 2: the fallback scorer `aX`/`oX` (the default, archetype-free path). Drives the scoring
 * mechanics with explicit ratings so the assertions don't depend on a specific set's numbers.
 */
class DraftsimFallbackScorerTest : FunSpec({

    fun card(name: String, cost: String, rarity: String = "common", type: String = "Creature") =
        CardSummary(name = name, manaCost = cost, typeLine = type, rarity = rarity).toScorerCard()

    fun scorer(vararg ratings: Pair<String, Double>) =
        DraftsimScorer(DraftsimSetTables(ratings = ratings.toMap(), removal = emptySet(), archetypes = emptyMap()))

    test("a higher-rated card outscores a lower-rated one in an empty pool") {
        val s = scorer("bomb" to 4.5, "filler" to 1.5)
        val bomb = s.scoreFallback(card("Bomb", "{3}{R}"), emptyList())
        val filler = s.scoreFallback(card("Filler", "{1}{G}"), emptyList())

        bomb.total shouldBeGreaterThan filler.total
        bomb.rawRating shouldBe 4.5
        bomb.reasons.first() shouldContain "Rating"
    }

    test("unrated cards fall back to the rarity ladder") {
        val s = scorer()  // no file → everything uses the rarity fallback
        s.scoreFallback(card("X", "{R}", rarity = "mythic"), emptyList()).rawRating shouldBe 4.5
        s.scoreFallback(card("Y", "{R}", rarity = "common"), emptyList()).rawRating shouldBe 1.5
    }

    test("once two colors are committed, an on-color card beats an off-color one") {
        val s = scorer("g1" to 4.0, "g2" to 4.0, "r1" to 4.0, "r2" to 4.0, "subject" to 2.5)
        // Pool: two green + two red, all rated 4.0 ⇒ green & red weights both exceed the threshold.
        val pool = listOf(card("g1", "{G}"), card("g2", "{G}"), card("r1", "{R}"), card("r2", "{R}"))

        val onColor = s.scoreFallback(card("subject", "{1}{G}"), pool)   // green = committed
        val offColor = s.scoreFallback(card("subject", "{1}{U}"), pool)  // blue = off

        onColor.total shouldBeGreaterThan offColor.total
        onColor.reasons.any { it.contains("on-color") } shouldBe true
    }

    test("a multi-pip off-color card is penalized") {
        val s = scorer("g1" to 4.0, "g2" to 4.0, "r1" to 4.0, "r2" to 4.0, "ub" to 2.5)
        val pool = listOf(card("g1", "{G}"), card("g2", "{G}"), card("r1", "{R}"), card("r2", "{R}"))
        val offTwoPips = s.scoreFallback(card("ub", "{U}{B}"), pool)  // both pips off-color
        offTwoPips.total shouldBeGreaterThan -1.0   // sanity: not the basic-land sentinel
        offTwoPips.total shouldBeGreaterThan 0.0
        // A two-off-pip card loses the -max(0, pips-1) penalty.
        offTwoPips.reasons.any { it.contains("off-color") } shouldBe true
    }

    test("booster scoring attaches a one-line summary to the argmax only") {
        val s = scorer("bomb" to 4.6, "ok" to 2.5, "weak" to 1.2)
        val pack = listOf(card("Bomb", "{2}{R}"), card("Ok", "{1}{G}"), card("Weak", "{4}{B}"))
        val scores = s.scoreBoosterFallback(pack, emptyList())

        scores.size shouldBe 3
        scores.getValue("Bomb").summary shouldNotBe null
        scores.getValue("Ok").summary shouldBe null
        // The bomb is the argmax.
        s.argmax(pack, scores)!!.name shouldBe "Bomb"
    }

    test("totals are rounded to two decimals") {
        val s = scorer("g1" to 4.0, "g2" to 4.0, "x" to 3.33)
        val pool = listOf(card("g1", "{G}"), card("g2", "{G}"))
        val score = s.scoreFallback(card("x", "{1}{G}"), pool)
        (Math.round(score.total * 100.0).toDouble() / 100.0) shouldBe score.total
    }
})
