package com.wingedsheep.tooling.coverage

import com.wingedsheep.tooling.coverage.emitter.Emitter
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

/**
 * Vendored emitter-regression fixtures.
 *
 * The real emitter inputs — the 29 MB mtgish IR and the Scryfall set cache — are gitignored and only
 * present after a tooling run downloads them, so the in-suite [EmitterSmokeTest] can only assert a
 * completion *rate* (it skips entirely on a clean box). To get a hermetic, per-card regression net we
 * vendor a small slice of those inputs for the calibrated sets as committed test resources, alongside
 * a golden of the emitter's output. A Kotest then re-emits from the slice and diffs the golden — no
 * network, no 29 MB download, no Gradle compile — so a handler/bridge change that silently alters a
 * card surfaces as a reviewable diff in `just test`.
 *
 * This is the FAST counterpart to `just coverage-verify` (emit → compile → gameplay-tree diff vs
 * golden), which stays the deeper, out-of-suite proof.
 *
 * Two files per set under [FIXTURES_DIR]:
 *  - `<code>.fixture.json` — the vendored inputs: the front-faced card name list plus, per card, its
 *    mtgish IR node and trimmed Scryfall metadata. Self-contained and human-reviewable.
 *  - `<code>.emitted.golden.txt` — the committed emitter output, one `########## <Name> ##########`
 *    block per card (a marker no Kotlin source contains, unlike the `// Name` blocks elsewhere).
 *
 * Two re-bless paths, both via the `fixtures` CLI (deterministic — no Gradle test task, so the
 * configuration cache can't stale-cache the result):
 *  - `just coverage-fixtures <CODE>`            — refresh inputs AND golden from real data (needs IR + cache).
 *  - `just coverage-fixtures --rebless`         — re-render the golden from the committed slice only,
 *                                                 after an intentional emitter change (needs neither).
 */
object Fixtures {
    val FIXTURES_DIR: File get() = File(TOOLING_DIR, "src/test/resources/fixtures")

    /** A line marker that cannot appear in generated Kotlin source, so block-splitting is unambiguous. */
    private const val MARKER_PREFIX = "########## "
    private const val MARKER_SUFFIX = " ##########"

    /** The vendored inputs for one set: a fixed, sorted card list and each card's emitter inputs. */
    data class FixtureSet(
        val code: String,
        val names: List<String>,
        val mtgish: Map<String, JsonObject>,
        val scryfall: Map<String, JsonObject>,
    )

    private val PRETTY = kotlinx.serialization.json.Json { prettyPrint = true; prettyPrintIndent = "  " }

    // ---------------------------------------------------------------------------
    // Golden rendering — the ONE routine both the generator and the test call, so the committed
    // golden and the test's recomputation are identical by construction.
    // ---------------------------------------------------------------------------
    fun renderGolden(fs: FixtureSet, effects: Set<String>, keywords: Set<String>): String {
        val sb = StringBuilder()
        for (name in fs.names) {
            val ir = fs.mtgish[name] ?: continue
            val res = Emitter.renderCard(ir, fs.scryfall[name], effects, keywords)
            sb.append(MARKER_PREFIX).append(name).append(MARKER_SUFFIX).append('\n')
            sb.append(res.text)
            if (!res.text.endsWith("\n")) sb.append('\n')
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Split a golden file into name→block, for a precise first-divergence message in the test. */
    fun goldenBlocks(text: String): LinkedHashMap<String, String> {
        val out = LinkedHashMap<String, String>()
        var name: String? = null
        val body = StringBuilder()
        fun flush() { if (name != null) out[name!!] = body.toString() }
        for (line in text.lineSequence()) {
            if (line.startsWith(MARKER_PREFIX) && line.endsWith(MARKER_SUFFIX)) {
                flush(); body.setLength(0)
                name = line.removePrefix(MARKER_PREFIX).removeSuffix(MARKER_SUFFIX)
            } else if (name != null) {
                body.append(line).append('\n')
            }
        }
        flush()
        return out
    }

    // ---------------------------------------------------------------------------
    // Disk IO — shared resource layout for both the CLI (writes) and the test (reads).
    // ---------------------------------------------------------------------------
    fun fixtureFile(code: String): File = File(FIXTURES_DIR, "${windowsSafeFileName(code.lowercase())}.fixture.json")
    fun goldenFile(code: String): File = File(FIXTURES_DIR, "${windowsSafeFileName(code.lowercase())}.emitted.golden.txt")

    /** Front-face codes that have a committed fixture, sorted — drives the test's set loop. */
    fun committedSets(): List<String> =
        FIXTURES_DIR.listFiles { f -> f.isFile && f.name.endsWith(".fixture.json") }
            ?.map { fromWindowsSafeFileName(it.name.removeSuffix(".fixture.json")).uppercase() }?.sorted()
            ?: emptyList()

    fun load(code: String): FixtureSet = parse(fixtureFile(code).readText())

    private fun parse(text: String): FixtureSet {
        val root = J.parseToJsonElement(text) as JsonObject
        val code = root.strField("code") ?: error("fixture missing 'code'")
        val names = (root["names"].asArr ?: error("fixture missing 'names'")).mapNotNull { it.asStr() }
        val cards = root["cards"].asObj ?: error("fixture missing 'cards'")
        val mtgish = LinkedHashMap<String, JsonObject>()
        val scryfall = LinkedHashMap<String, JsonObject>()
        for (name in names) {
            val entry = cards[name].asObj ?: error("fixture 'cards' missing '$name'")
            entry["mtgish"].asObj?.let { mtgish[name] = it }
            entry["scryfall"].asObj?.let { scryfall[name] = it }
        }
        return FixtureSet(code, names, mtgish, scryfall)
    }

    private fun serialize(fs: FixtureSet): String {
        val obj = buildJsonObject {
            put("code", fs.code)
            put("names", buildJsonArray { fs.names.forEach { add(it) } })
            put("cards", buildJsonObject {
                for (name in fs.names) {
                    put(name, buildJsonObject {
                        put("mtgish", fs.mtgish[name] ?: JsonNull)
                        put("scryfall", fs.scryfall[name] ?: JsonNull)
                    })
                }
            })
        }
        return PRETTY.encodeToString(JsonElement.serializer(), obj)
    }

    // ---------------------------------------------------------------------------
    // Generation from real data (needs the mtgish IR + Scryfall cache).
    // ---------------------------------------------------------------------------
    private fun buildFromRealData(code: String): FixtureSet {
        val (draft, extra) = Cards.canonicalNames(code)
        require(draft != null && extra != null) {
            "no Scryfall data for set ${code.uppercase()} (run: just card-status --set ${code.uppercase()})"
        }
        val canonical = draft + extra
        val idx = Mtgish.loadMtgishIndex(canonical)
        // Only IR-matched cards can be emitted; pin a fixed, sorted slice so the golden is deterministic.
        val names = canonical.filter { it in idx }.sorted()
        val mtgish = names.associateWith { idx.getValue(it) }
        val scryfall = names.mapNotNull { n -> Cards.scryfallCard(code, n)?.let { n to it } }.toMap()
        return FixtureSet(code.uppercase(), names, mtgish, scryfall)
    }

    /** Refresh a set's vendored inputs AND its golden from real data (needs the IR + Scryfall cache). */
    private fun refresh(code: String, effects: Set<String>, keywords: Set<String>) {
        val fs = buildFromRealData(code)
        FIXTURES_DIR.mkdirs()
        fixtureFile(code).writeText(serialize(fs))
        // Re-bless from the just-written slice so the golden is rendered from EXACTLY what the test reads.
        rebless(code, effects, keywords)
        val unmatched = (Cards.canonicalNames(code).let { (d, e) -> (d ?: emptySet()) + (e ?: emptySet()) }).size - fs.names.size
        println("fixtures ${fs.code}: ${fs.names.size} cards vendored -> ${fixtureFile(code).relativeTo(REPO_ROOT)}" +
            (if (unmatched > 0) "  ($unmatched canonical name(s) unmatched in mtgish IR — skipped)" else ""))
    }

    /** Re-render a set's golden from its committed slice — no real data needed. The deterministic
     *  re-bless for an intentional emitter change. */
    private fun rebless(code: String, effects: Set<String>, keywords: Set<String>) {
        goldenFile(code).writeText(renderGolden(load(code), effects, keywords))
    }

    // ---------------------------------------------------------------------------
    // CLI: `fixtures [CODE...]`            — refresh inputs + golden from real data (default POR).
    //      `fixtures --rebless [CODE...]`  — re-render golden from committed slices (default: all).
    // ---------------------------------------------------------------------------
    fun run(args: List<String>): Int {
        val reblessOnly = "--rebless" in args
        val codes = args.filterNot { it.startsWith("--") }
            .ifEmpty { if (reblessOnly) committedSets() else listOf("POR") }
        if (codes.isEmpty()) {
            System.err.println("fixtures --rebless: no committed fixtures to re-bless (run `fixtures <CODE>` first)")
            return 1
        }
        val effects = Registry.loadEffectSerialNames()
        val keywords = Registry.loadKeywords()
        for (code in codes) {
            runCatching {
                if (reblessOnly) {
                    rebless(code, effects, keywords)
                    println("fixtures ${code.uppercase()}: golden re-blessed from committed slice -> ${goldenFile(code).relativeTo(REPO_ROOT)}")
                } else {
                    refresh(code, effects, keywords)
                }
            }.onFailure { System.err.println("fixtures ${code.uppercase()}: ${it.message}"); return 1 }
        }
        return 0
    }
}
