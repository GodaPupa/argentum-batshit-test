package com.wingedsheep.sdk.model

import com.wingedsheep.sdk.core.ManaCost
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json

/**
 * Pins the multi-printing data-model surface introduced in Phase 1: the [Printing] /
 * [PrintingRef] pair, their JSON round-trip, and the derived
 * [CardDefinition.defaultPrintingRef] getter that lets existing card definitions
 * advertise a default printing without per-card edits.
 */
class PrintingTest : DescribeSpec({

    val json = Json { ignoreUnknownKeys = true }

    describe("PrintingRef") {
        it("formats identifier as SET-CN") {
            PrintingRef("M10", "146").identifier() shouldBe "M10-146"
        }

        it("round-trips through JSON") {
            val ref = PrintingRef("2X2", "117")
            val encoded = json.encodeToString(PrintingRef.serializer(), ref)
            val decoded = json.decodeFromString(PrintingRef.serializer(), encoded)
            decoded shouldBe ref
        }
    }

    describe("Printing") {
        it("exposes a PrintingRef view") {
            val printing = Printing(
                oracleId = "abc-123",
                name = "Lightning Bolt",
                setCode = "M10",
                collectorNumber = "146",
            )
            printing.ref shouldBe PrintingRef("M10", "146")
        }

        it("round-trips through JSON with optional metadata") {
            val printing = Printing(
                oracleId = "abc-123",
                name = "Lightning Bolt",
                setCode = "M10",
                collectorNumber = "146",
                artist = "Christopher Moeller",
                imageUri = "https://example.test/m10/146.jpg",
                rarity = Rarity.COMMON,
                frameEffects = listOf("showcase"),
                borderColor = "borderless",
            )
            val encoded = json.encodeToString(Printing.serializer(), printing)
            val decoded = json.decodeFromString(Printing.serializer(), encoded)
            decoded shouldBe printing
            decoded.borderColor shouldBe "borderless"
        }

        it("treats showcase frame as alternate") {
            Printing("o", "n", "ECL", "1", frameEffects = listOf("showcase"))
                .isAlternateFrame shouldBe true
        }

        it("treats borderless border as alternate even without a showcase frame") {
            Printing("o", "n", "ECL", "2", frameEffects = listOf("inverted"), borderColor = "borderless")
                .isAlternateFrame shouldBe true
        }

        it("treats plain black-border / full-art printings as not alternate") {
            Printing("o", "n", "ECL", "3", isFullArt = true, borderColor = "black")
                .isAlternateFrame shouldBe false
        }
    }

    describe("CardDefinition.withPrinting") {
        val bear = CardDefinition.creature(
            name = "Bear",
            manaCost = ManaCost.parse("{1}{G}"),
            subtypes = emptySet(),
            power = 2,
            toughness = 2,
            metadata = ScryfallMetadata(collectorNumber = "100", imageUri = "https://example.test/canonical.jpg"),
        ).copy(setCode = "ECL")

        val showcase = Printing(
            oracleId = "bear-oracle",
            name = "Bear",
            setCode = "ECL",
            collectorNumber = "300",
            scryfallId = "scry-300",
            artist = "Showcase Artist",
            imageUri = "https://example.test/showcase.jpg",
            frameEffects = listOf("showcase"),
            borderColor = "borderless",
        )

        it("overlays the printing's presentation onto the card") {
            val reskinned = bear.withPrinting(showcase)
            reskinned.metadata.imageUri shouldBe "https://example.test/showcase.jpg"
            reskinned.metadata.collectorNumber shouldBe "300"
            reskinned.metadata.artist shouldBe "Showcase Artist"
            reskinned.defaultPrintingRef shouldBe PrintingRef("ECL", "300")
        }

        it("leaves oracle identity untouched") {
            val reskinned = bear.withPrinting(showcase)
            reskinned.name shouldBe bear.name
            reskinned.manaCost shouldBe bear.manaCost
            reskinned.typeLine shouldBe bear.typeLine
            reskinned.creatureStats shouldBe bear.creatureStats
        }

        it("ignores a printing's back-face image on a single-faced card") {
            val withBack = showcase.copy(backFaceImageUri = "https://example.test/back.jpg")
            bear.withPrinting(withBack).backFace.shouldBeNull()
        }

        it("overlays the back-face art on a genuine double-faced card") {
            val backFace = CardDefinition.creature(
                name = "Bear, Awakened",
                manaCost = ManaCost.parse(""),
                subtypes = emptySet(),
                power = 4,
                toughness = 4,
                metadata = ScryfallMetadata(imageUri = "https://example.test/canonical-back.jpg"),
            )
            val dfc = bear.copy(backFace = backFace)
            val dfcShowcase = showcase.copy(backFaceImageUri = "https://example.test/showcase-back.jpg")

            val reskinned = dfc.withPrinting(dfcShowcase)
            reskinned.metadata.imageUri shouldBe "https://example.test/showcase.jpg"
            reskinned.backFace?.metadata?.imageUri shouldBe "https://example.test/showcase-back.jpg"
            reskinned.backFace?.name shouldBe "Bear, Awakened"
        }
    }

    describe("CardDefinition.defaultPrintingRef") {
        it("derives from setCode + metadata.collectorNumber") {
            val card = CardDefinition.creature(
                name = "Bear",
                manaCost = ManaCost.parse("{1}{G}"),
                subtypes = emptySet(),
                power = 2,
                toughness = 2,
                metadata = ScryfallMetadata(collectorNumber = "146"),
            ).copy(setCode = "M10")

            card.defaultPrintingRef shouldBe PrintingRef("M10", "146")
        }

        it("is null when setCode missing") {
            val card = CardDefinition.creature(
                name = "Bear",
                manaCost = ManaCost.parse("{1}{G}"),
                subtypes = emptySet(),
                power = 2,
                toughness = 2,
                metadata = ScryfallMetadata(collectorNumber = "146"),
            )
            card.defaultPrintingRef.shouldBeNull()
        }

        it("is null when collector number missing") {
            val card = CardDefinition.creature(
                name = "Bear",
                manaCost = ManaCost.parse("{1}{G}"),
                subtypes = emptySet(),
                power = 2,
                toughness = 2,
            ).copy(setCode = "M10")
            card.defaultPrintingRef.shouldBeNull()
        }
    }
})
