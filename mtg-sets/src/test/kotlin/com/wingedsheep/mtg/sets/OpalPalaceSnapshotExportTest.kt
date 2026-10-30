package com.wingedsheep.mtg.sets

import com.wingedsheep.sdk.serialization.CardExporter
import com.wingedsheep.sdk.serialization.CardLoader
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

/** Produce a reviewable candidate only; never rewrite any committed snapshot. */
class OpalPalaceSnapshotExportTest : FunSpec({
    test("Opal Palace canonical discovery and lossless snapshot candidate") {
        val c13 = MtgSetCatalog.all.single { it.code == "C13" }
        val card = c13.cards.single { it.name == "Opal Palace" }
        fun normalized(value: String): String {
            val ids = linkedMapOf<String, String>()
            return Regex("""ability_\d+""").replace(value) { match ->
                ids.getOrPut(match.value) { "ability_${ids.size + 1}" }
            }
        }
        val exported = CardExporter.exportToJson(card)
        normalized(CardExporter.exportToJson(CardLoader.fromJson(exported))) shouldBe normalized(exported)
        val directory = Path.of("build", "opal-palace-candidate")
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("OpalPalace.block.json"), "// Opal Palace\n${normalized(exported)}")
        Files.writeString(directory.resolve("C13.candidate.json"), c13.cards.sortedBy { it.name }.joinToString("\n\n") {
            "// ${it.name}\n${normalized(CardExporter.exportToJson(it))}"
        })
    }
})
