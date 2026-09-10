package com.wingedsheep.tooling.coverage

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Cross-set printing lookup — decides whether a given set is a card's *canonical* home (its earliest
 * real-expansion printing) or a later reprint.
 *
 * This is the generator's half of the rule enforced by `scripts/check-card-printing.py`: a card's
 * `CardDefinition` must live in its earliest real printing, and every later set contributes only a
 * `Printing(...)` row — never a second, colliding canonical. We use the same Scryfall `unique=prints`
 * query, the same scaffoldable-set-type rule, and the same `~/.cache/scryfall/printings/<slug>.json`
 * cache file (and on-disk schema) as that script, so the two share state and never double-fetch.
 */
object Printings {
    // Set types that count as a real "home" for a canonical CardDefinition. Promo / token /
    // memorabilia printings are noted but never displace the actual expansion debut. Kept in sync
    // with SCAFFOLDABLE_SET_TYPES in scripts/check-card-printing.py.
    private val SCAFFOLDABLE_SET_TYPES = setOf(
        "core", "expansion", "draft_innovation", "masters", "commander",
        "starter", "duel_deck", "from_the_vault", "premium_deck", "spellbook",
        "planechase", "archenemy", "vanguard", "treasure_chest", "alchemy", "funny", "remastered",
    )
    private val IGNORED_SET_CODES = setOf("om1")

    private val CACHE_ROOT = File(System.getProperty("user.home"), ".cache/scryfall/printings")
    private const val CACHE_TTL_DAYS = 30L

    /** One printing of a card. Field set + JSON keys mirror `check-card-printing.py`'s cache rows. */
    data class PrintInfo(
        val setCode: String,        // lowercase Scryfall set code
        val setName: String,
        val setType: String,
        val collectorNumber: String,
        val releasedAt: String,
        val rarity: String,
        val oracleId: String?,
        val scryfallId: String?,
    )

    private fun slug(name: String): String =
        Regex("[^a-z0-9]+").replace(name.lowercase(), "-").trim('-')

    private fun cachePath(name: String) = File(CACHE_ROOT, "${slug(name)}.json")

    private fun parseRow(el: JsonElement): PrintInfo? {
        val o = el.asObj ?: return null
        return PrintInfo(
            setCode = o.strField("set_code") ?: return null,
            setName = o.strField("set_name") ?: "",
            setType = o.strField("set_type") ?: "",
            collectorNumber = o.strField("collector_number") ?: "",
            releasedAt = o.strField("released_at") ?: "",
            rarity = o.strField("rarity") ?: "",
            oracleId = o.strField("oracle_id"),
            scryfallId = o.strField("scryfall_id"),
        )
    }

    private fun toJson(prints: List<PrintInfo>): JsonArray = buildJsonArray {
        for (p in prints) add(buildJsonObject {
            put("set_code", p.setCode)
            put("set_name", p.setName)
            put("set_type", p.setType)
            put("collector_number", p.collectorNumber)
            put("released_at", p.releasedAt)
            put("rarity", p.rarity)
            put("oracle_id", p.oracleId)
            put("scryfall_id", p.scryfallId)
        })
    }

    private fun readCache(name: String): List<PrintInfo>? {
        val path = cachePath(name)
        if (!path.isFile) return null
        val ageDays = (System.currentTimeMillis() - path.lastModified()) / 86_400_000.0
        if (ageDays >= CACHE_TTL_DAYS) return null
        return runCatching {
            (J.parseToJsonElement(path.readText()) as JsonArray).mapNotNull { parseRow(it) }
        }.getOrNull()
    }

    private fun fetch(name: String): List<PrintInfo>? {
        val q = URLEncoder.encode("!\"$name\"", StandardCharsets.UTF_8).replace("+", "%20")
        var url: String? = "https://api.scryfall.com/cards/search?q=$q&unique=prints&order=released&dir=asc"
        val out = mutableListOf<PrintInfo>()
        try {
            while (url != null) {
                val data = Scryfall.scryfallGet(url)
                for (cardEl in data["data"].asArr ?: JsonArray(emptyList())) {
                    val card = cardEl.asObj ?: continue
                    out.add(
                        PrintInfo(
                            setCode = card.strField("set") ?: "",
                            setName = card.strField("set_name") ?: "",
                            setType = card.strField("set_type") ?: "",
                            collectorNumber = card.strField("collector_number") ?: "",
                            releasedAt = card.strField("released_at") ?: "",
                            rarity = card.strField("rarity") ?: "",
                            oracleId = card.strField("oracle_id"),
                            scryfallId = card.strField("id"),
                        )
                    )
                }
                url = if (data["has_more"] == kotlinx.serialization.json.JsonPrimitive(true)) data.strField("next_page") else null
            }
        } catch (e: Scryfall.ScryfallHttpError) {
            System.err.println("printings: Scryfall lookup for \"$name\" failed: $e")
            return null
        }
        CACHE_ROOT.mkdirs()
        cachePath(name).writeText(J.encodeToString(JsonElement.serializer(), toJson(out)))
        return out
    }

    /** All printings of [name], oldest→newest. Empty when the lookup fails offline with no cache. */
    fun printingsOf(name: String): List<PrintInfo> =
        readCache(name) ?: fetch(name) ?: emptyList()

    /**
     * The set code (lowercase) of the card's earliest real-expansion printing — i.e. where the
     * canonical `CardDefinition` belongs. `null` when we have no Scryfall data at all (offline,
     * no cache), in which case callers fall back to treating the set as canonical (old behaviour).
     */
    fun earliestRealSet(name: String): String? {
        val prints = printingsOf(name)
        for (p in prints) {
            if (p.setCode in IGNORED_SET_CODES) continue
            if (p.setType in SCAFFOLDABLE_SET_TYPES) return p.setCode
        }
        return prints.firstOrNull()?.setCode
    }

    /** This card's printing in [setCode], or null if Scryfall doesn't list it for that set. */
    fun printingFor(name: String, setCode: String): PrintInfo? =
        printingsOf(name).firstOrNull { it.setCode.equals(setCode, ignoreCase = true) }
}
