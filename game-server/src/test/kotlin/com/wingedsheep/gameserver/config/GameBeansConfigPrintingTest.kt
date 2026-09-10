package com.wingedsheep.gameserver.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

/**
 * Guards the deckbuilder default-printing wiring against the regression where a set that ships
 * showcase/borderless variant printings (e.g. Lorwyn Eclipsed) had its catalog default to a
 * variant art instead of the standard frame.
 *
 * The canonical printing of a card is *synthesised* from its `CardDefinition` metadata, while
 * the variants are explicit `Printing` rows carrying their set's release date. `GameBeansConfig`
 * must stamp the same release date onto the canonical printing so the two tie on date and the
 * `isAlternateFrame` tiebreaker can pick the plain one — otherwise the canonical printing dates
 * to `null`, sorts last, and a showcase/borderless variant wins the default slot.
 */
class GameBeansConfigPrintingTest : FunSpec({

    val config = GameBeansConfig(GameProperties())
    val cardRegistry = config.cardRegistry()
    val printingRegistry = config.printingRegistry(cardRegistry)

    test("ECL canonical printing is stamped with the set release date") {
        // Dawnhand Dissident ships a plain printing (#98) plus a showcase/borderless variant.
        // The synthesised canonical printing must inherit ECL's release date from the stamp.
        val plain = printingRegistry.getPrinting("ECL", "98").shouldNotBeNull()
        plain.releaseDate shouldBe "2026-01-23"
        plain.isAlternateFrame shouldBe false
    }

    test("ECL deckbuilder default is the plain frame, not the showcase/borderless variant") {
        val default = printingRegistry.defaultPrinting("Dawnhand Dissident").shouldNotBeNull()
        default.collectorNumber shouldBe "98"
        default.isAlternateFrame shouldBe false
    }
})
