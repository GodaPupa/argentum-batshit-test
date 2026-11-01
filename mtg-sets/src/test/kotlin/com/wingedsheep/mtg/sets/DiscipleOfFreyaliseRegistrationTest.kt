package com.wingedsheep.mtg.sets

import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.CardLayout
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.serialization.CardExporter
import com.wingedsheep.sdk.serialization.CardLoader
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

/** Additive card-only registration checks; candidate output never rewrites a protected snapshot. */
class DiscipleOfFreyaliseRegistrationTest : FunSpec({
    fun actual() = MtgSetCatalog.requireByCode("MH3").cards.single { it.name == "Disciple of Freyalise" }
    fun normalized(value: String): String {
        val ids = linkedMapOf<String, String>()
        return Regex("""ability_\d+""").replace(value) { match ->
            ids.getOrPut(match.value) { "ability_${ids.size + 1}" }
        }
    }
    test("DR01 exactly one canonical MH3 registration and no Garden singleton or reprint") {
        MtgSetCatalog.all.flatMap { set -> set.cards.filter { it.name == "Disciple of Freyalise" }.map { set.code } } shouldBe listOf("MH3")
        MtgSetCatalog.all.flatMap { it.cards }.count { it.name == "Garden of Freyalise" } shouldBe 0
        MtgSetCatalog.all.flatMap { it.printings }.count { it.name in setOf("Disciple of Freyalise", "Garden of Freyalise", "Disciple of Freyalise // Garden of Freyalise") } shouldBe 0
    }
    test("DR02 canonical front and land back retain distinct characteristics") {
        val front = actual()
        val back = front.backFace!!
        front.layout shouldBe CardLayout.MODAL_DFC
        front.name shouldBe "Disciple of Freyalise"
        front.manaCost.cmc shouldBe 6
        front.typeLine.isCreature shouldBe true
        front.typeLine.isLand shouldBe false
        front.colors shouldBe setOf(Color.GREEN)
        front.colorIdentity shouldBe setOf(Color.GREEN)
        back.name shouldBe "Garden of Freyalise"
        back.manaCost.cmc shouldBe 0
        back.typeLine.isLand shouldBe true
        back.typeLine.isCreature shouldBe false
        back.colors shouldBe emptySet<Color>()
        back.colorIdentity shouldBe setOf(Color.GREEN)
    }
    test("DR03 both faces use canonical collector artist rarity and exact original images") {
        val front = actual()
        val back = front.backFace!!
        for (face in listOf(front, back)) {
            face.metadata.collectorNumber shouldBe "250"
            face.metadata.rarity shouldBe Rarity.UNCOMMON
            face.metadata.artist shouldBe "Valera Lutfullina"
        }
        front.metadata.imageUri shouldBe "https://cards.scryfall.io/normal/front/a/8/a8e9ea5a-5e10-4b77-baef-0352ff035483.jpg?1783911228"
        back.metadata.imageUri shouldBe "https://cards.scryfall.io/normal/back/a/8/a8e9ea5a-5e10-4b77-baef-0352ff035483.jpg?1783911228"
    }
    test("DR04 both faces retain the seven archived rulings") {
        val front = actual()
        front.metadata.rulings.size shouldBe 7
        front.backFace!!.metadata.rulings shouldBe front.metadata.rulings
        // Exact seven ruling bytes are also bound to the archived printing source by the receiver.
    }
    test("DR05 both face definitions survive export load and reexport") {
        val exported = CardExporter.exportToJson(actual())
        val restored = CardLoader.fromJson(exported)
        restored.layout shouldBe CardLayout.MODAL_DFC
        restored.backFace!!.metadata.collectorNumber shouldBe "250"
        normalized(CardExporter.exportToJson(restored)) shouldBe normalized(exported)
    }
    test("DR06 export additive MH3 candidate without rewriting protected original") {
        val set = MtgSetCatalog.requireByCode("MH3")
        set.cards.size shouldBe 25
        val card = actual()
        val directory = Path.of("build", "disciple-freyalise-candidate")
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("DiscipleOfFreyalise.block.json"), "// Disciple of Freyalise\n${normalized(CardExporter.exportToJson(card))}")
        Files.writeString(directory.resolve("MH3.candidate.json"), set.cards.sortedBy { it.name }.joinToString("\n\n") {
            "// ${it.name}\n${normalized(CardExporter.exportToJson(it))}"
        }.trimEnd() + "\n")
    }
})
