package com.wingedsheep.tooling.coverage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Hermetic, per-card emitter regression net. For every set with a committed fixture
 * ([Fixtures.committedSets]) it re-emits each card from the vendored slice and diffs the result
 * against the committed golden. Unlike [EmitterSmokeTest] (which needs the 29 MB IR + Scryfall cache
 * and only asserts a completion *rate*), this runs entirely off committed resources — no network, no
 * download, no Gradle compile — so a bridge/handler change that silently alters a card's output fails
 * here with the exact card and the first divergent line.
 *
 * The emitter still reads the SDK's effect/keyword/import vocabulary from live source (via [Registry]),
 * so an intentional SDK or emitter change can legitimately shift the golden. Re-bless via the
 * deterministic CLI (NOT `-DupdateSnapshots` — Gradle's configuration cache stale-caches that
 * forwarding for this module):
 *
 *   just coverage-fixtures --rebless      # re-render golden from the committed slice (no real data)
 *   just coverage-fixtures POR            # full refresh of slice + golden from real data
 */
class EmitterGoldenTest : StringSpec({

    val effects = Registry.loadEffectSerialNames()
    val keywords = Registry.loadKeywords()

    val sets = Fixtures.committedSets()
    if (sets.isEmpty()) {
        "no committed emitter fixtures (run: just coverage-fixtures POR)".config(enabled = false) {}
    }

    for (code in sets) {
        "$code: emitter output matches the committed golden, card-for-card" {
            val fs = Fixtures.load(code)
            val actual = Fixtures.renderGolden(fs, effects, keywords)
            val goldenFile = Fixtures.goldenFile(code)
            check(goldenFile.isFile) {
                "no golden at ${goldenFile.relativeTo(REPO_ROOT)} — generate it with: just coverage-fixtures $code"
            }
            val expected = goldenFile.readText()
            if (actual == expected) {
                actual shouldBe expected  // record the assertion on the happy path
            } else {
                check(false) {
                    "$code emitter output drifted from golden.\n${firstDivergence(actual, expected)}\n\n" +
                        "If this change is intentional, re-bless:  just coverage-fixtures --rebless"
                }
            }
        }
    }
})

/** Pinpoint the first card whose render changed, so a failure reads as a card diff, not a 250 KB
 *  string mismatch. */
private fun firstDivergence(actual: String, expected: String): String {
    val got = Fixtures.goldenBlocks(actual)
    val want = Fixtures.goldenBlocks(expected)
    val changed = (got.keys + want.keys).sorted().firstOrNull { got[it] != want[it] }
        ?: return "card set changed (same per-card blocks)"
    return buildString {
        append("first changed card: $changed\n")
        when {
            changed !in want -> append("  (new card not in golden)")
            changed !in got -> append("  (card dropped from emitter output)")
            else -> {
                val g = got.getValue(changed).lines()
                val w = want.getValue(changed).lines()
                val i = (0..maxOf(g.size, w.size)).first { g.getOrNull(it) != w.getOrNull(it) }
                append("  line ${i + 1}:\n")
                append("    golden:  ${w.getOrNull(i) ?: "<missing>"}\n")
                append("    emitted: ${g.getOrNull(i) ?: "<missing>"}")
            }
        }
    }
}
