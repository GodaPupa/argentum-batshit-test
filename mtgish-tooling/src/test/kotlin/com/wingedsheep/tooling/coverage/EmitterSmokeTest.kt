package com.wingedsheep.tooling.coverage

import com.wingedsheep.tooling.coverage.emitter.Emitter
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import java.io.File

/**
 * End-to-end emitter smoke test: render every Portal card the bridge covers and assert it produces
 * a whole, well-formed `card(...)` source. Portal is the emitter's tuned target (≈100% whole-render),
 * so a regression in the bridge / emitter handlers shows up here as a drop in the complete-render
 * rate — the in-suite counterpart to the out-of-suite `just coverage-verify` (emit → compile → golden).
 *
 * Guarded on data availability: the 29 MB mtgish IR is downloaded on first tooling run and the
 * Scryfall set cache is shared with `scripts/card-status`. When neither is present (a clean CI box
 * with no network), the test skips rather than fails — run any `just coverage*` recipe once to
 * populate the caches and it activates.
 */
class EmitterSmokeTest : StringSpec({

    val porCache = File(System.getProperty("user.home"), ".cache/scryfall/por.json")
    val dataAvailable = MTGISH_LINES.exists() && porCache.isFile && SDK_EFFECTS.isDirectory

    "Portal renders a high rate of whole, well-formed cards".config(enabled = dataAvailable) {
        val (draft, extra) = Cards.canonicalNames("POR")
        val names = ((draft ?: emptySet()) + (extra ?: emptySet())).sorted()
        names.isEmpty() shouldBe false

        val idx = Mtgish.loadMtgishIndex(names.toSet())
        val effects = Registry.loadEffectSerialNames()
        val keywords = Registry.loadKeywords()

        var matched = 0
        var complete = 0
        for (name in names) {
            val card = idx[name] ?: continue
            matched++
            val res = Emitter.renderCard(card, Cards.scryfallCard("POR", name), effects, keywords)
            if (res.complete) {
                complete++
                // A complete render is a real card definition with a package + the card DSL entry point.
                check(res.text.contains("val ") && res.text.contains("card(\"")) {
                    "complete render for \"$name\" is missing the card(...) DSL:\n${res.text.take(200)}"
                }
            }
        }

        matched shouldBe names.size  // every Portal card joins the mtgish corpus by name
        val rate = complete.toDouble() / matched
        rate shouldBeGreaterThanOrEqual 0.90  // Portal is the emitter's tuned set
    }
})
