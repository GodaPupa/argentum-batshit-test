package com.wingedsheep.engine.limited

import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Printing
import com.wingedsheep.sdk.model.ScryfallMetadata
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.random.Random

/**
 * Pins the booster variant slot: [BoosterGenerator.applyVariantPrintings] re-skins a generated
 * card with one of its showcase / borderless printings at a configured rate, leaving oracle
 * identity untouched and only ever selecting alternate-frame printings.
 */
class BoosterGeneratorVariantTest : DescribeSpec({

    fun card(name: String, cn: String) = CardDefinition.creature(
        name = name,
        manaCost = ManaCost.parse("{1}{G}"),
        subtypes = emptySet(),
        power = 2,
        toughness = 2,
        metadata = ScryfallMetadata(collectorNumber = cn, imageUri = "canonical/$name.jpg"),
    ).copy(setCode = "ECL")

    fun showcase(name: String, cn: String) = Printing(
        oracleId = name,
        name = name,
        setCode = "ECL",
        collectorNumber = cn,
        imageUri = "showcase/$name.jpg",
        frameEffects = listOf("showcase"),
        borderColor = "borderless",
    )

    val oko = card("Oko", "61")
    val elf = card("Elf", "5")
    val okoShowcase = showcase("Oko", "287")

    describe("applyVariantPrintings") {

        it("is a no-op at chance 0.0") {
            val cards = listOf(oko, elf)
            BoosterGenerator.applyVariantPrintings(cards, listOf(okoShowcase), 0.0, Random(1)) shouldContainExactly cards
        }

        it("re-skins every card that has a variant at chance 1.0") {
            val result = BoosterGenerator.applyVariantPrintings(listOf(oko, elf), listOf(okoShowcase), 1.0, Random(1))
            // Oko swapped to its showcase art; Elf (no variant) untouched.
            result[0].metadata.imageUri shouldBe "showcase/Oko.jpg"
            result[0].metadata.collectorNumber shouldBe "287"
            result[1] shouldBe elf
        }

        it("keeps the card's oracle identity when re-skinning") {
            val result = BoosterGenerator.applyVariantPrintings(listOf(oko), listOf(okoShowcase), 1.0, Random(1))
            result[0].name shouldBe "Oko"
            result[0].manaCost shouldBe oko.manaCost
            result[0].typeLine shouldBe oko.typeLine
        }

        it("never selects a non-alternate-frame printing") {
            val plainReprint = Printing(
                oracleId = "Oko", name = "Oko", setCode = "ECL", collectorNumber = "999",
                imageUri = "plain/Oko.jpg", borderColor = "black",
            )
            // Only a canonical reprint is available — even at chance 1.0 the card is left alone.
            val result = BoosterGenerator.applyVariantPrintings(listOf(oko), listOf(plainReprint), 1.0, Random(1))
            result[0] shouldBe oko
        }

        it("rolls independently per card at roughly the configured rate") {
            val cards = List(4000) { oko }
            val result = BoosterGenerator.applyVariantPrintings(cards, listOf(okoShowcase), 0.15, Random(42))
            val swapped = result.count { it.metadata.collectorNumber == "287" }
            val rate = swapped.toDouble() / cards.size
            rate shouldBeGreaterThan 0.12
            rate shouldBeLessThan 0.18
        }
    }
})
