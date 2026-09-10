package com.wingedsheep.tooling.coverage.dashboard

import com.wingedsheep.tooling.coverage.Cards
import com.wingedsheep.tooling.coverage.Counter
import com.wingedsheep.tooling.coverage.Fidelity
import com.wingedsheep.tooling.coverage.Mtgish
import com.wingedsheep.tooling.coverage.Probe
import com.wingedsheep.tooling.coverage.Registry
import com.wingedsheep.tooling.coverage.Scryfall
import com.wingedsheep.tooling.coverage.emitter.Emitter
import com.wingedsheep.tooling.coverage.emitter.RenderResult
import kotlinx.serialization.json.JsonObject

/**
 * Data layer for the interactive coverage dashboard. Owns the (loaded-once) SDK capability registry
 * and the shared mtgish IR index, and turns them into the per-set / per-card / cross-set models the
 * TUI renders.
 *
 * It does NOT re-implement the coverage logic: the genuinely reusable per-card primitives already
 * live in [Probe.analyze] (coverable? + blockers) and [Emitter.renderCard] (whole-render? = the
 * AUTOGEN/SCAFFOLD split). This object only aggregates them per set and memoizes the result, so a
 * keypress never recomputes work it has already done. The single 29MB mtgish stream is paid once,
 * lazily, on the first set the user actually drills into.
 */
object Analyzer {
    lateinit var effects: Set<String>
        private set
    lateinit var keywords: Set<String>
        private set
    private var initialized = false

    fun init() {
        if (initialized) return
        effects = Registry.loadEffectSerialNames()
        keywords = Registry.loadKeywords()
        initialized = true
    }

    // --- set enumeration (implemented sets ∪ sets with a cached Scryfall payload) -----------------
    data class SetRef(val code: String, val name: String, val released: String?)

    private var setsCache: List<SetRef>? = null

    fun sets(): List<SetRef> {
        setsCache?.let { return it }
        val implemented = Scryfall.discoverSets().associate { it.code.uppercase() to it.displayName }
        val scryfallNames = Scryfall.setDisplayNames()
        val codes = (implemented.keys + Scryfall.cachedSetCodes()).toSortedSet()
        return codes.map { SetRef(it, implemented[it] ?: scryfallNames[it] ?: it, Scryfall.releaseDate(it)) }
            .also { setsCache = it }
    }

    // --- cheap counts (no mtgish needed): drives the set-list coverage% instantly ----------------
    data class Counts(val total: Int, val implemented: Int) {
        val pct: Double get() = if (total == 0) 0.0 else implemented * 100.0 / total
    }

    private val countsCache = HashMap<String, Counts>()

    fun counts(code: String): Counts = countsCache.getOrPut(code.uppercase()) {
        val (draft, extra) = Cards.canonicalNames(code)
        val canonical = (draft ?: emptySet()) + (extra ?: emptySet())
        val implemented = Cards.implementedNames(code).count { it in canonical }
        Counts(canonical.size, implemented)
    }

    // --- the shared mtgish index, loaded once for the union of every set's canonical names --------
    private var index: Map<String, JsonObject>? = null

    private fun index(): Map<String, JsonObject> {
        index?.let { return it }
        val union = sortedSetOf<String>()
        for (s in sets()) {
            val (draft, extra) = Cards.canonicalNames(s.code)
            ((draft ?: emptySet()) + (extra ?: emptySet())).forEach { union.add(it) }
        }
        return Mtgish.loadMtgishIndex(union).also { index = it }
    }

    /** Has the 29MB mtgish IR been streamed yet? Lets the TUI show a one-time loading frame. */
    fun indexLoaded(): Boolean = index != null

    // --- per-set detail (the combined coverage + autogen pass) ------------------------------------
    /** What the auto-generator can do with a card, independent of whether it's already implemented. */
    enum class Gen { WHOLE, SCAFFOLD, BLOCKED, NONE }

    data class CardVerdict(val name: String, val implemented: Boolean, val gen: Gen, val blockers: List<Probe.Blocker>)

    data class LeaderRow(val disc: String, val value: String, val count: Int, val verdict: String)

    data class Detail(
        val code: String,
        val name: String,
        val total: Int,
        val implemented: Int,
        // backlog breakdown — cards NOT yet implemented, by what the generator could do with them:
        val autogen: Int,
        val scaffold: Int,
        val blocked: Int,
        val unmatched: Int,
        // auto-gen coverage across ALL cards (implemented included) — answers "how much of this set
        // could the generator reproduce?", so even a 100%-implemented set has a meaningful number:
        val genWhole: Int,
        val genScaffold: Int,
        val genBlocked: Int,
        val cards: List<CardVerdict>,
        val leaderboard: List<LeaderRow>,
        val fidelity: Fidelity.FidelitySummary?,
    ) {
        /** Missing-but-fully-coverable: zero engine work needed (AUTOGEN drafts + SCAFFOLD hand-wires). */
        val free: Int get() = autogen + scaffold

        /** Implemented + free — the share of the set reachable with no new engine capability. */
        val reachable: Int get() = implemented + free

        /** % of all cards the generator renders whole — the set's auto-gen coverage. */
        val genCoveragePct: Double get() = if (total == 0) 0.0 else genWhole * 100.0 / total
    }

    private val detailCache = HashMap<String, Detail>()

    fun isComputed(code: String): Boolean = detailCache.containsKey(code.uppercase())

    fun detail(code: String): Detail = detailCache.getOrPut(code.uppercase()) { compute(code.uppercase()) }

    fun verdictFor(code: String, name: String): CardVerdict? = detail(code).cards.firstOrNull { it.name == name }

    private fun compute(code: String): Detail {
        val (draft, extra) = Cards.canonicalNames(code)
        val canonical = ((draft ?: emptySet()) + (extra ?: emptySet())).toSortedSet()
        val implemented = Cards.implementedNames(code)
        val ix = index()

        val cards = ArrayList<CardVerdict>(canonical.size)
        val leaderboard = Counter<Pair<String, String>>()
        val leaderboardVerdict = HashMap<Pair<String, String>, String>()
        var nImpl = 0; var nAuto = 0; var nScaffold = 0; var nBlocked = 0; var nUnmatched = 0
        var genWhole = 0; var genScaffold = 0; var genBlocked = 0

        for (name in canonical) {
            val isImpl = name in implemented
            val card = ix[name]
            if (card == null) {
                cards.add(CardVerdict(name, isImpl, Gen.NONE, emptyList()))
                if (isImpl) nImpl++ else nUnmatched++
                continue
            }
            val analysis = Probe.analyze(card, effects, keywords)
            val gen = when {
                !analysis.coverable -> Gen.BLOCKED
                Emitter.renderCard(card, Cards.scryfallCard(code, name), effects, keywords).complete -> Gen.WHOLE
                else -> Gen.SCAFFOLD
            }
            cards.add(CardVerdict(name, isImpl, gen, if (gen == Gen.BLOCKED) analysis.blockers else emptyList()))

            when (gen) { Gen.WHOLE -> genWhole++; Gen.SCAFFOLD -> genScaffold++; Gen.BLOCKED -> genBlocked++; Gen.NONE -> {} }
            if (isImpl) {
                nImpl++
            } else when (gen) {
                Gen.WHOLE -> nAuto++
                Gen.SCAFFOLD -> nScaffold++
                Gen.BLOCKED -> {
                    nBlocked++
                    for (b in analysis.blockers) { leaderboard.add(b.disc to b.value); leaderboardVerdict[b.disc to b.value] = b.verdict }
                }
                Gen.NONE -> nUnmatched++
            }
        }

        val rows = leaderboard.mostCommon().map { (key, c) ->
            LeaderRow(key.first, key.second, c, leaderboardVerdict[key] ?: "")
        }
        val fidelity = runCatching { Fidelity.summarizeOrNull(code, effects, keywords, ix) }.getOrNull()
        return Detail(
            code = code,
            name = sets().firstOrNull { it.code == code }?.name ?: code,
            total = canonical.size,
            implemented = nImpl, autogen = nAuto, scaffold = nScaffold, blocked = nBlocked, unmatched = nUnmatched,
            genWhole = genWhole, genScaffold = genScaffold, genBlocked = genBlocked,
            cards = cards, leaderboard = rows, fidelity = fidelity,
        )
    }

    // --- per-card drill-down ----------------------------------------------------------------------
    data class CardReport(
        val name: String,
        val coverable: Boolean,
        val unmatched: Boolean,
        val reqs: List<Probe.Req>,
        val implementedIn: Set<String>,
    )

    private val renderCache = HashMap<String, RenderResult?>()

    /**
     * The emitter's PARTIAL render of [name] for [setCode]: every part that maps is emitted and each
     * un-renderable part becomes a located `// TODO(hole)` line, so the result carries a renderable
     * fraction + the list of holes. This is the dashboard view — "how much of this card could be
     * implemented, and which parts can't". (The autogen WRITE path stays non-partial: it still declines
     * a card to a scaffold rather than ship a half-card.) Null when the card has no mtgish IR entry.
     * Memoized so scrolling the code view never re-renders.
     */
    fun cardRender(setCode: String, name: String): RenderResult? = renderCache.getOrPut("$setCode/$name") {
        val card = index()[name] ?: return@getOrPut null
        Emitter.renderCard(
            card, Cards.scryfallCard(setCode, name), effects, keywords,
            pkg = "com.wingedsheep.mtg.sets.generated.${setCode.lowercase()}.cards",
            partial = true,
        )
    }

    /** The generated `cardDef` DSL text from the partial render (with `// TODO(hole)` lines in place of
     *  the parts that don't map yet). Null when the card has no mtgish IR entry. */
    fun cardSource(setCode: String, name: String): String? = cardRender(setCode, name)?.text

    fun cardReport(name: String): CardReport {
        val card = index()[name]
        val implementedIn = Cards.implementedNamesForCard(name)
        if (card == null) return CardReport(name, coverable = false, unmatched = true, reqs = emptyList(), implementedIn)
        val analysis = Probe.analyze(card, effects, keywords)
        return CardReport(name, analysis.coverable, unmatched = false, reqs = analysis.reqs, implementedIn)
    }

    // --- cross-set capability index (aggregate every set's blocked-feature leaderboard) ----------
    /** A missing-capability card the index ranks: which set it lives in and its name. */
    data class CrossCard(val set: String, val name: String)
    data class CrossRow(
        val disc: String,
        val value: String,
        val count: Int,
        val sets: Int,
        val verdict: String,
        /** The blocked, unimplemented cards this capability would unlock — set-qualified, drill-down fodder. */
        val cards: List<CrossCard>,
    )

    private var crossCache: List<CrossRow>? = null

    /** Forces [detail] for every set (the slow path) — [progress] is invoked as (done, total). */
    fun crossSet(progress: (Int, Int) -> Unit): List<CrossRow> {
        crossCache?.let { return it }
        val agg = Counter<Pair<String, String>>()
        val setCount = HashMap<Pair<String, String>, Int>()
        val verdict = HashMap<Pair<String, String>, String>()
        val cardsByKey = HashMap<Pair<String, String>, MutableList<CrossCard>>()
        val all = sets()
        for ((i, ref) in all.withIndex()) {
            progress(i + 1, all.size)
            val d = detail(ref.code)
            for (row in d.leaderboard) {
                val key = row.disc to row.value
                agg.add(key, row.count)
                setCount[key] = (setCount[key] ?: 0) + 1
                verdict[key] = row.verdict
            }
            // The same blocked-unimplemented cards the leaderboard counts, kept per capability so the
            // TUI can drill from a row into the actual cards adding that mapping would unlock.
            for (cv in d.cards) {
                if (cv.implemented || cv.gen != Gen.BLOCKED) continue
                for (b in cv.blockers) cardsByKey.getOrPut(b.disc to b.value) { mutableListOf() }.add(CrossCard(ref.code, cv.name))
            }
        }
        return agg.mostCommon()
            .map { (key, c) -> CrossRow(key.first, key.second, c, setCount[key] ?: 0, verdict[key] ?: "", cardsByKey[key] ?: emptyList()) }
            .also { crossCache = it }
    }

    /** Drop memoized analysis for [code] (and the shared index + cross-set roll-up) so `r` recomputes. */
    fun invalidate(code: String) {
        countsCache.remove(code.uppercase())
        detailCache.remove(code.uppercase())
        renderCache.clear()
        crossCache = null
        index = null
    }
}
