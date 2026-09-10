package com.wingedsheep.tooling.coverage.emitter

import com.wingedsheep.tooling.coverage.asciiIdentifier
import com.wingedsheep.tooling.coverage.strField
import kotlinx.serialization.json.JsonObject

/**
 * Render a `Printing(...)` reprint row instead of a full `card(...)` definition.
 *
 * The generator writes one of these when the target set is NOT the card's earliest real printing:
 * a later set must contribute only presentation metadata (set, collector number, art) and let the
 * canonical `CardDefinition` stay in the earlier set's `cards/` package. Emitting a second
 * `card(...)` would register a colliding canonical (last-registration-wins in `CardRegistry`) and
 * fail `scripts/check-card-printing.py`. The row is picked up by `CardDiscovery.findPrintingsIn`.
 */
internal fun reprintRowSource(
    cardName: String,
    pkg: String,
    canonicalSetCode: String,   // earliest real printing, e.g. "LEA" — where the canonical lives
    setCode: String,            // target set, e.g. "POR"
    oracleId: String,
    releaseDate: String?,
    meta: JsonObject?,          // per-set Scryfall metadata: rarity / collector_number / artist / image_uri
): String {
    val valName = asciiIdentifier(cardName) + "Reprint"
    val rarity = RARITY_DSL[(meta?.strField("rarity") ?: "").lowercase()] ?: "COMMON"
    val collector = meta?.strField("collector_number").orEmpty()
    val artist = meta?.strField("artist")?.takeIf { it.isNotEmpty() }
    val image = meta?.strField("image_uri")?.takeIf { it.isNotEmpty() }
    val target = setCode.uppercase()
    val canon = canonicalSetCode.uppercase()

    val lines = mutableListOf(
        "package $pkg",
        "",
        "import com.wingedsheep.sdk.model.Printing",
        "import com.wingedsheep.sdk.model.Rarity",
        "",
        "/**",
        " * $cardName reprint in $target.",
        " *",
        " * The canonical [com.wingedsheep.sdk.model.CardDefinition] (script, types, P/T) lives in",
        " * $canon's `cards/` package (the card's earliest real printing). This file contributes only",
        " * the $target-specific presentation row — set, collector number, art — picked up automatically",
        " * by `CardDiscovery.findPrintingsIn` and surfaced via the set's `printings`.",
        " */",
        "val $valName = Printing(",
        "    oracleId = \"${ktStr(oracleId)}\",",
        "    name = \"${ktStr(cardName)}\",",
        "    setCode = \"$target\",",
        "    collectorNumber = \"${ktStr(collector)}\",",
    )
    if (artist != null) lines.add("    artist = \"${ktStr(artist)}\",")
    if (image != null) lines.add("    imageUri = \"${ktStr(image)}\",")
    if (!releaseDate.isNullOrEmpty()) lines.add("    releaseDate = \"${ktStr(releaseDate)}\",")
    lines.add("    rarity = Rarity.$rarity,")
    lines.add(")")
    return lines.joinToString("\n") + "\n"
}
