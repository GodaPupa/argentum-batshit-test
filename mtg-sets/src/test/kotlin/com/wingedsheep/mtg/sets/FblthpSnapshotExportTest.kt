package com.wingedsheep.mtg.sets

import com.wingedsheep.sdk.serialization.CardExporter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.File

/** Produce reviewable candidate bytes without rewriting a protected snapshot. */
class FblthpSnapshotExportTest : FunSpec({
    test("export WAR candidate with exactly one canonical Fblthp") {
        val set = MtgSetCatalog.all.single { it.code == "WAR" }
        set.cards.count { it.name == "Fblthp, the Lost" } shouldBe 1
        val actual = set.cards.sortedBy { it.name }.joinToString("\n\n") { card ->
            val ids = LinkedHashMap<String, String>()
            val json = Regex("""ability_\d+""").replace(CardExporter.exportToJson(card)) {
                ids.getOrPut(it.value) { "ability_${ids.size + 1}" }
            }
            "// ${card.name}\n$json"
        }
        File("build/fblthp-export").mkdirs()
        File("build/fblthp-export/WAR.json").writeText(actual)
        File("build/fblthp-export/Fblthp.json").writeText(CardExporter.exportToJson(set.cards.single { it.name == "Fblthp, the Lost" }))
    }
})
