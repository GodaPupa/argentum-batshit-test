package com.wingedsheep.tooling.coverage

import com.wingedsheep.tooling.coverage.emitter.Emitter
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Partial-render regression net — the hole feature. Runs entirely off the committed POR fixture slice
 * (no 29 MB IR, no network), mirroring [EmitterGoldenTest]. Two invariants make the feature safe and
 * useful at once:
 *
 *  1. WHOLE cards are untouched — a card the default path renders complete renders byte-identically in
 *     partial mode, with zero holes and a 1.0 fraction. So switching the dashboard to the partial path
 *     can never degrade a card that already rendered.
 *  2. SCAFFOLD cards become LOCATED — a card the default path declines to a scaffold instead renders
 *     its mapped parts and surfaces the rest as `// TODO(hole)` lines, a sub-1.0 fraction, and a
 *     non-empty hole list. That is exactly the "how much / which parts" the TUI shows.
 */
class EmitterPartialTest : StringSpec({

    val effects = Registry.loadEffectSerialNames()
    val keywords = Registry.loadKeywords()
    val fs = if ("POR" in Fixtures.committedSets()) Fixtures.load("POR") else null

    if (fs == null) {
        "no committed POR fixture (run: just coverage-fixtures POR)".config(enabled = false) {}
    } else {
        "a WHOLE card renders identically in partial mode, with no holes" {
            var checkedWhole = 0
            for (name in fs.names) {
                val ir = fs.mtgish[name] ?: continue
                val sf = fs.scryfall[name]
                val full = Emitter.renderCard(ir, sf, effects, keywords)
                if (!full.complete) continue
                checkedWhole++
                val partial = Emitter.renderCard(ir, sf, effects, keywords, partial = true)
                partial.holes shouldBe emptyList()
                partial.complete shouldBe true
                partial.renderableFraction shouldBe 1.0
                partial.text shouldBe full.text  // byte-identical: holes only appear for declined parts
            }
            (checkedWhole > 0).shouldBeTrue()
        }

        // POR's committed slice is 100% AUTO, so to exercise the located-holes path hermetically we
        // inject one bogus rule into a known-complete card: the default path would now decline the WHOLE
        // card, but the partial path must keep every real part and hole only the injected one.
        "an injected un-renderable rule holes ONLY that part; the rest still renders" {
            val name = fs.names.first { fs.mtgish[it]?.let { ir -> Emitter.renderCard(ir, fs.scryfall[it], effects, keywords).complete } == true }
            val ir = fs.mtgish.getValue(name)
            val sf = fs.scryfall[name]
            val whole = Emitter.renderCard(ir, sf, effects, keywords, partial = true)  // baseline: no holes

            val bogus = buildJsonObject { put("_Rule", JsonPrimitive("TotallyUnknownRule_zzz")) }
            val rules = (ir["Rules"] as JsonArray).toMutableList().apply { add(bogus) }
            val injected = JsonObject(ir.toMutableMap().apply { put("Rules", JsonArray(rules)) })

            // Default path: the whole card declines to a scaffold (one bad rule poisons it).
            Emitter.renderCard(injected, sf, effects, keywords).complete shouldBe false

            // Partial path: exactly one located hole for the injected rule; every other part survives.
            val partial = Emitter.renderCard(injected, sf, effects, keywords, partial = true)
            partial.complete shouldBe false
            partial.holes shouldBe listOf("TotallyUnknownRule_zzz")
            partial.parts shouldBe whole.parts + 1            // baseline parts + the one injected
            partial.renderableFraction shouldBeLessThan 1.0
            partial.text shouldContain "// TODO(hole): TotallyUnknownRule_zzz"
            // The real abilities the baseline rendered are still present (holes are additive, not a reset).
            whole.holes shouldBe emptyList()
            partial.holes.shouldNotBeEmpty()
        }
    }
})
