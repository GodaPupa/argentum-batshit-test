package com.wingedsheep.engine.registry

import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Printing
import com.wingedsheep.sdk.model.PrintingRef
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.model.ScryfallMetadata
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class PrintingRegistryTest : FunSpec({

    fun bolt(setCode: String, collectorNumber: String, releaseDate: String? = null) = Printing(
        oracleId = "bolt-oracle",
        name = "Lightning Bolt",
        setCode = setCode,
        collectorNumber = collectorNumber,
        releaseDate = releaseDate,
    )

    test("getPrinting by ref round-trips") {
        val registry = PrintingRegistry()
        val m10 = bolt("M10", "146")
        registry.register(m10)
        registry.getPrinting(PrintingRef("M10", "146")) shouldBe m10
        registry.getPrinting("M10", "146") shouldBe m10
    }

    test("printingsOf returns every printing for a name") {
        val registry = PrintingRegistry()
        val a = bolt("M10", "146")
        val b = bolt("M11", "149")
        val c = bolt("2X2", "117")
        registry.register(listOf(a, b, c))

        registry.printingsOf("Lightning Bolt") shouldContainExactlyInAnyOrder listOf(a, b, c)
        registry.printingsOf("Counterspell") shouldBe emptyList()
    }

    test("printingsOfOracle groups by oracle id") {
        val registry = PrintingRegistry()
        val a = bolt("M10", "146")
        val b = bolt("M11", "149")
        registry.register(listOf(a, b))

        registry.printingsOfOracle("bolt-oracle") shouldContainExactlyInAnyOrder listOf(a, b)
        registry.printingsOfOracle("missing-oracle") shouldBe emptyList()
    }

    test("defaultPrinting prefers the newest release date") {
        val registry = PrintingRegistry()
        registry.register(bolt("M10", "146", releaseDate = "2009-07-17"))
        registry.register(bolt("2X2", "117", releaseDate = "2022-04-22"))
        registry.register(bolt("M11", "149", releaseDate = "2010-07-16"))

        registry.defaultPrinting("Lightning Bolt")?.setCode shouldBe "2X2"
    }

    test("defaultPrinting prefers the plain frame over a same-set alternate-frame variant") {
        val registry = PrintingRegistry()
        // Showcase variant registered first to prove order doesn't decide it.
        val showcase = bolt("ECL", "300", releaseDate = "2026-01-23")
            .copy(frameEffects = listOf("showcase"))
        val borderless = bolt("ECL", "350", releaseDate = "2026-01-23")
            .copy(borderColor = "borderless")
        val plain = bolt("ECL", "120", releaseDate = "2026-01-23")
        registry.register(listOf(showcase, borderless, plain))

        registry.defaultPrinting("Lightning Bolt") shouldBe plain
    }

    test("defaultPrinting still picks the newest set even when only its variant is alternate-frame") {
        val registry = PrintingRegistry()
        val oldPlain = bolt("M10", "146", releaseDate = "2009-07-17")
        val newShowcase = bolt("ECL", "300", releaseDate = "2026-01-23")
            .copy(frameEffects = listOf("showcase"))
        registry.register(listOf(oldPlain, newShowcase))

        // Release date dominates the alternate-frame tiebreaker: the newer set still wins.
        registry.defaultPrinting("Lightning Bolt") shouldBe newShowcase
    }

    test("defaultPrinting falls back to first-registered when dates are missing") {
        val registry = PrintingRegistry()
        val first = bolt("M10", "146")
        val second = bolt("M11", "149")
        registry.register(listOf(first, second))

        registry.defaultPrinting("Lightning Bolt") shouldBe first
    }

    test("defaultPrinting returns null for unknown name") {
        PrintingRegistry().defaultPrinting("Lightning Bolt").shouldBeNull()
    }

    test("re-registering same ref overwrites and dedupes secondary indexes") {
        val registry = PrintingRegistry()
        registry.register(bolt("M10", "146", releaseDate = "2009-07-17"))
        val updated = bolt("M10", "146", releaseDate = "2009-07-18").copy(artist = "Updated")
        registry.register(updated)

        registry.size shouldBe 1
        registry.printingsOf("Lightning Bolt") shouldBe listOf(updated)
        registry.printingsOfOracle("bolt-oracle") shouldBe listOf(updated)
    }

    test("registerSynthesizedDefault builds a printing from CardDefinition.metadata") {
        val card = CardDefinition.creature(
            name = "Grizzly Bears",
            manaCost = ManaCost.parse("{1}{G}"),
            subtypes = emptySet(),
            power = 2,
            toughness = 2,
            metadata = ScryfallMetadata(
                collectorNumber = "146",
                rarity = Rarity.COMMON,
                artist = "Test Artist",
                imageUri = "https://example.test/grizzly.jpg",
                releaseDate = "1993-08-05",
                scryfallId = "scry-grizzly",
            ),
        ).copy(setCode = "LEA", oracleId = "grizzly-oracle")

        val registry = PrintingRegistry()
        val synth = registry.registerSynthesizedDefault(card)

        synth.shouldBe(
            Printing(
                oracleId = "grizzly-oracle",
                name = "Grizzly Bears",
                setCode = "LEA",
                collectorNumber = "146",
                scryfallId = "scry-grizzly",
                artist = "Test Artist",
                imageUri = "https://example.test/grizzly.jpg",
                releaseDate = "1993-08-05",
                rarity = Rarity.COMMON,
            )
        )
        registry.getPrinting(PrintingRef("LEA", "146")) shouldBe synth
    }

    test("registerSynthesizedDefault is null when setCode or collector number missing") {
        val noSet = CardDefinition.creature(
            name = "Bear",
            manaCost = ManaCost.parse("{1}{G}"),
            subtypes = emptySet(),
            power = 2,
            toughness = 2,
            metadata = ScryfallMetadata(collectorNumber = "146"),
        )
        PrintingRegistry().registerSynthesizedDefault(noSet).shouldBeNull()

        val noCn = CardDefinition.creature(
            name = "Bear",
            manaCost = ManaCost.parse("{1}{G}"),
            subtypes = emptySet(),
            power = 2,
            toughness = 2,
        ).copy(setCode = "LEA")
        PrintingRegistry().registerSynthesizedDefault(noCn).shouldBeNull()
    }

    test("registerSynthesizedDefault is idempotent — pre-existing printing wins") {
        val card = CardDefinition.creature(
            name = "Grizzly Bears",
            manaCost = ManaCost.parse("{1}{G}"),
            subtypes = emptySet(),
            power = 2,
            toughness = 2,
            metadata = ScryfallMetadata(collectorNumber = "146", artist = "Synth Artist"),
        ).copy(setCode = "LEA", oracleId = "grizzly-oracle")

        val registry = PrintingRegistry()
        val explicit = Printing(
            oracleId = "grizzly-oracle",
            name = "Grizzly Bears",
            setCode = "LEA",
            collectorNumber = "146",
            artist = "Explicit Artist",
        )
        registry.register(explicit)

        val result = registry.registerSynthesizedDefault(card)
        result shouldBe explicit
        registry.size shouldBe 1
        registry.getPrinting(PrintingRef("LEA", "146")) shouldBe explicit
    }

    test("clear empties all indexes") {
        val registry = PrintingRegistry()
        registry.register(bolt("M10", "146"))
        registry.clear()

        registry.size shouldBe 0
        registry.getPrinting(PrintingRef("M10", "146")).shouldBeNull()
        registry.printingsOf("Lightning Bolt") shouldBe emptyList()
        registry.printingsOfOracle("bolt-oracle") shouldBe emptyList()
    }
})
