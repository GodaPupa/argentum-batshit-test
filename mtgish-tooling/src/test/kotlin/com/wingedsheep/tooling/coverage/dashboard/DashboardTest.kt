package com.wingedsheep.tooling.coverage.dashboard

import com.wingedsheep.tooling.coverage.MTGISH_LINES
import com.wingedsheep.tooling.coverage.SDK_EFFECTS
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * Dashboard data-layer + TUI-primitive tests. The [Analyzer] check is guarded on data availability
 * exactly like [com.wingedsheep.tooling.coverage.EmitterSmokeTest] (the 29 MB mtgish IR + the shared
 * Scryfall cache) so a clean CI box skips rather than fails; the [Ansi] cell-fitting test is pure and
 * always runs, since exact cell widths are what keep the two-pane layout aligned.
 */
class DashboardTest : StringSpec({

    val porCache = File(System.getProperty("user.home"), ".cache/scryfall/por.json")
    val dataAvailable = MTGISH_LINES.exists() && porCache.isFile && SDK_EFFECTS.isDirectory

    "Analyzer classifies a set's cards into a partition that sums to the whole".config(enabled = dataAvailable) {
        Analyzer.init()
        val d = Analyzer.detail("POR")

        // implemented + the missing-card buckets partition every canonical card — none double-counted.
        (d.implemented + d.autogen + d.scaffold + d.blocked + d.unmatched) shouldBe d.total
        d.cards.size shouldBe d.total
        d.total shouldBeGreaterThan 0
        d.implemented shouldBeGreaterThan 0 // Portal is an implemented set
        d.free shouldBe (d.autogen + d.scaffold)

        // Auto-gen coverage spans ALL cards (implemented included), so a fully-implemented set still
        // has a non-zero generatable count — the whole point of the cross-implementation metric.
        d.genWhole shouldBeGreaterThan 0
        (d.genWhole + d.genScaffold + d.genBlocked) shouldBeLessThanOrEqual d.total

        // The leaderboard only ranks real blockers, so every row has a positive count.
        d.leaderboard.forEach { it.count shouldBeGreaterThan 0 }

        // An implemented Portal card reports its home set in the per-card drill-down.
        val implementedCard = d.cards.first { it.implemented }
        Analyzer.cardReport(implementedCard.name).implementedIn shouldContain "POR"
    }

    "cross-set index carries the blocked cards behind each ranked capability".config(enabled = dataAvailable) {
        Analyzer.init()
        val rows = Analyzer.crossSet { _, _ -> }
        rows.shouldNotBeEmpty()
        rows.forEach { row ->
            // The drill-down card list is exactly what the count ranks — set-qualified, no phantom rows.
            row.cards.size shouldBe row.count
            row.cards.map { it.set }.toSet().size shouldBeLessThanOrEqual row.sets
        }
    }

    "Ansi.fit pads or ellipsis-truncates to an exact visible width" {
        Ansi.fit("abc", 5) shouldBe "abc  "
        Ansi.fit("abc", 3) shouldBe "abc"
        Ansi.fit("abcdef", 4) shouldBe "abc…"
        Ansi.fit("ab", 1) shouldBe "…"
        Ansi.fit("anything", 0) shouldBe ""
    }
})
