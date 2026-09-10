package com.wingedsheep.gameserver.controller

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.registry.PrintingRegistry
import com.wingedsheep.mtg.sets.definitions.por.PortalSet
import com.wingedsheep.sdk.model.Printing
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.http.HttpStatus

class PrintingsControllerTest : FunSpec({

    val registry = CardRegistry().apply {
        // Portal carries `setCode = "POR"` and Scryfall metadata for every card it ships,
        // which is what the controller needs to synthesise a fallback printing when the
        // PrintingRegistry has no explicit row.
        register(PortalSet.cards.map { it.copy(setCode = "POR") })
        register(PortalSet.basicLands)
    }

    test("GET /api/cards/{name}/printings returns every registered printing newest-first") {
        val printings = PrintingRegistry().apply {
            register(Printing(
                oracleId = "bolt-oracle",
                name = "Lightning Bolt",
                setCode = "M10",
                collectorNumber = "146",
                imageUri = "https://img/m10.jpg",
                releaseDate = "2009-07-17",
            ))
            register(Printing(
                oracleId = "bolt-oracle",
                name = "Lightning Bolt",
                setCode = "2X2",
                collectorNumber = "117",
                imageUri = "https://img/2x2.jpg",
                releaseDate = "2022-07-08",
            ))
            register(Printing(
                oracleId = "bolt-oracle",
                name = "Lightning Bolt",
                setCode = "M11",
                collectorNumber = "149",
                imageUri = "https://img/m11.jpg",
                releaseDate = "2010-07-16",
            ))
        }
        val controller = PrintingsController(registry, printings)

        val response = controller.getPrintingsForCard("Lightning Bolt")

        response.statusCode shouldBe HttpStatus.OK
        val body = response.body.shouldNotBeNull()
        body.map { it.setCode } shouldContainExactly listOf("2X2", "M11", "M10")
        body.first().imageUri shouldBe "https://img/2x2.jpg"
    }

    test("GET /api/cards/{name}/printings orders the plain frame ahead of same-set variants") {
        // A set ships a normal printing plus showcase/borderless variants on the same release
        // date. The plain printing must lead so the deckbuilder's set-filter art override picks
        // it (it takes the first match for the set) and the picker lists the standard frame first.
        val printings = PrintingRegistry().apply {
            register(Printing(
                oracleId = "bolt-oracle",
                name = "Lightning Bolt",
                setCode = "ECL",
                collectorNumber = "300",
                imageUri = "https://img/ecl-showcase.jpg",
                releaseDate = "2026-01-23",
                frameEffects = listOf("showcase"),
            ))
            register(Printing(
                oracleId = "bolt-oracle",
                name = "Lightning Bolt",
                setCode = "ECL",
                collectorNumber = "350",
                imageUri = "https://img/ecl-borderless.jpg",
                releaseDate = "2026-01-23",
                borderColor = "borderless",
            ))
            register(Printing(
                oracleId = "bolt-oracle",
                name = "Lightning Bolt",
                setCode = "ECL",
                collectorNumber = "120",
                imageUri = "https://img/ecl-plain.jpg",
                releaseDate = "2026-01-23",
            ))
        }
        val controller = PrintingsController(registry, printings)

        val body = controller.getPrintingsForCard("Lightning Bolt").body.shouldNotBeNull()

        body.first().collectorNumber shouldBe "120"
        body.first().imageUri shouldBe "https://img/ecl-plain.jpg"
    }

    test("GET /api/cards/{name}/printings falls back to a synthesised default when registry is empty") {
        // No printing rows wired — the controller has to derive one from the CardDefinition's
        // own setCode + metadata so the picker still surfaces a usable option.
        val printings = PrintingRegistry()
        val controller = PrintingsController(registry, printings)
        val knownPortalCard = registry.allCardNames().first { name ->
            registry.getCard(name)?.defaultPrintingRef != null
        }

        val response = controller.getPrintingsForCard(knownPortalCard)

        response.statusCode shouldBe HttpStatus.OK
        val body = response.body.shouldNotBeNull()
        body.size shouldBe 1
        body.first().setCode shouldBe "POR"
    }

    test("GET /api/cards/{name}/printings returns 404 when neither registry knows the card") {
        val controller = PrintingsController(registry, PrintingRegistry())
        val response = controller.getPrintingsForCard("Definitely Not A Real Card")
        response.statusCode shouldBe HttpStatus.NOT_FOUND
        response.body shouldBe null
    }

    test("GET /api/printings batches multiple names into one map") {
        val printings = PrintingRegistry().apply {
            register(Printing(
                oracleId = "bolt-oracle",
                name = "Lightning Bolt",
                setCode = "M10",
                collectorNumber = "146",
            ))
            register(Printing(
                oracleId = "counter-oracle",
                name = "Counterspell",
                setCode = "MMQ",
                collectorNumber = "67",
            ))
        }
        val controller = PrintingsController(registry, printings)

        val result = controller.getPrintingsBatch(listOf("Lightning Bolt", "Counterspell"))

        result.keys shouldBe setOf("Lightning Bolt", "Counterspell")
        result.getValue("Lightning Bolt").single().setCode shouldBe "M10"
        result.getValue("Counterspell").single().setCode shouldBe "MMQ"
    }

    test("GET /api/printings dedupes repeated names") {
        val printings = PrintingRegistry().apply {
            register(Printing(
                oracleId = "bolt-oracle",
                name = "Lightning Bolt",
                setCode = "M10",
                collectorNumber = "146",
            ))
        }
        val controller = PrintingsController(registry, printings)

        val result = controller.getPrintingsBatch(
            listOf("Lightning Bolt", "Lightning Bolt", "Lightning Bolt")
        )

        result.size shouldBe 1
        result.getValue("Lightning Bolt").size shouldBe 1
    }

    test("GET /api/printings omits unknown names from the response map") {
        val controller = PrintingsController(registry, PrintingRegistry())

        val result = controller.getPrintingsBatch(listOf("Definitely Not A Real Card"))

        result.shouldBeEmpty()
    }

    test("GET /api/printings with empty names returns empty map") {
        val controller = PrintingsController(registry, PrintingRegistry())
        controller.getPrintingsBatch(emptyList()).shouldBeEmpty()
    }

    test("PrintingDTO carries setName resolved via MtgSetCatalog") {
        val printings = PrintingRegistry().apply {
            register(Printing(
                oracleId = "bolt-oracle",
                name = "Lightning Bolt",
                // Edge of Eternities is a real catalog set — its displayName must surface
                // on the DTO so the deckbuilder can label rows without a second lookup.
                setCode = "EOE",
                collectorNumber = "1",
            ))
        }
        val controller = PrintingsController(registry, printings)

        val response = controller.getPrintingsForCard("Lightning Bolt")
        val dto = response.body.shouldNotBeNull().single()
        dto.setName shouldBe "Edge of Eternities"
    }
})
