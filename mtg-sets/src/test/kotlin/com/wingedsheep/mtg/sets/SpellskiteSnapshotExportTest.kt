package com.wingedsheep.mtg.sets

import com.wingedsheep.sdk.serialization.CardExporter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

/** Exports review candidates only. Never updates a source golden. */
class SpellskiteSnapshotExportTest : FunSpec({
    test("SR09 export discovered NPH and CLB card trees") {
        MtgSetCatalog.requireByCode("NPH").cards.count { it.name == "Spellskite" } shouldBe 1
        for (code in listOf("NPH", "CLB")) {
            val actual = MtgSetCatalog.requireByCode(code).cards.sortedBy { it.name }.joinToString("\n\n") { card ->
                val ids = LinkedHashMap<String, String>()
                val tree = Regex("ability_\\d+").replace(CardExporter.exportToJson(card)) { m ->
                    ids.getOrPut(m.value) { "ability_${ids.size + 1}" }
                }
                "// ${card.name}\n$tree"
            }.trimEnd() + "\n"
            File("build/spellskite-export/$code.json").apply { parentFile.mkdirs(); writeText(actual) }
        }
    }
})
