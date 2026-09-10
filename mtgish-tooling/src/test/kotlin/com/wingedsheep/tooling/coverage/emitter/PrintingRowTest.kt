package com.wingedsheep.tooling.coverage.emitter

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The generator emits a `Printing(...)` row (not a second `card(...)`) for any card whose canonical
 * home is an earlier set — the rule enforced by `scripts/check-card-printing.py`. This pins the
 * shape of that row so a regression can't quietly start emitting a colliding canonical again.
 */
class PrintingRowTest : StringSpec({

    "reprint row carries set metadata and points at the canonical set" {
        val meta = buildJsonObject {
            put("rarity", "uncommon")
            put("collector_number", "83")
            put("artist", "Ted Naifeh")
            put("image_uri", "https://cards.scryfall.io/normal/front/4/4/x.jpg")
        }
        val src = reprintRowSource(
            cardName = "Bog Wraith",
            pkg = "com.wingedsheep.mtg.sets.definitions.por.cards",
            canonicalSetCode = "lea",
            setCode = "por",
            oracleId = "508248d1-09a4-4e41-a4c9-286618e5061e",
            releaseDate = "1997-05-01",
            meta = meta,
        )

        src shouldContain "val BogWraithReprint = Printing("
        src shouldContain "oracleId = \"508248d1-09a4-4e41-a4c9-286618e5061e\""
        src shouldContain "name = \"Bog Wraith\""
        src shouldContain "setCode = \"POR\""
        src shouldContain "collectorNumber = \"83\""
        src shouldContain "artist = \"Ted Naifeh\""
        src shouldContain "releaseDate = \"1997-05-01\""
        src shouldContain "rarity = Rarity.UNCOMMON"
        src shouldContain "LEA's `cards/` package"
        // It must NOT emit a colliding canonical definition.
        src shouldNotContain "card(\"Bog Wraith\")"
    }

    "missing artist / image fields are simply omitted, rarity defaults to COMMON" {
        val src = reprintRowSource(
            cardName = "Grizzly Bears",
            pkg = "p",
            canonicalSetCode = "lea",
            setCode = "por",
            oracleId = "oid",
            releaseDate = null,
            meta = buildJsonObject { put("collector_number", "169") },
        )
        src shouldContain "rarity = Rarity.COMMON"
        src shouldNotContain "artist ="
        src shouldNotContain "imageUri ="
        src shouldNotContain "releaseDate ="
    }

    "val name is the PascalCase identifier of multi-word names" {
        val src = reprintRowSource(
            cardName = "Merfolk of the Pearl Trident",
            pkg = "p", canonicalSetCode = "lea", setCode = "por",
            oracleId = "oid", releaseDate = null, meta = null,
        )
        src shouldContain "val MerfolkOfThePearlTridentReprint = Printing("
        // meta == null -> rarity falls back to COMMON, collectorNumber emitted empty (required field).
        src shouldContain "collectorNumber = \"\""
    }

    "rarity mapping covers the Scryfall vocabulary" {
        RARITY_DSL["mythic"] shouldBe "MYTHIC"
        RARITY_DSL["special"] shouldBe "SPECIAL"
    }
})
