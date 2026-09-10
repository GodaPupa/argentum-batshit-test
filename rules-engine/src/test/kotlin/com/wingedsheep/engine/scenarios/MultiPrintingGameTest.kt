package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.registry.PrintingRegistry
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.CardEntry
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.Printing
import com.wingedsheep.sdk.model.PrintingRef
import com.wingedsheep.sdk.model.Rarity
import com.wingedsheep.sdk.model.ScryfallMetadata
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

/**
 * End-to-end seam test for multi-printing support: deck entries pin a [PrintingRef], the
 * [PrintingRegistry] resolves it, and [GameInitializer] stamps the chosen printing's image
 * URL onto the per-entity [CardComponent.imageUri]. Together with the precedence flip in
 * `ClientStateTransformer` (Phase 3), this guarantees that a player's pick of M10 vs. 2X2
 * Lightning Bolt shows up at every projection layer.
 *
 * Phase 4 of the multi-printing plan in `backlog/multi-printing-system.md`.
 */
class MultiPrintingGameTest : FunSpec({

    fun bear(setCode: String, collectorNumber: String, imageUri: String) = Printing(
        oracleId = "grizzly-oracle",
        name = "Grizzly Bears",
        setCode = setCode,
        collectorNumber = collectorNumber,
        imageUri = imageUri,
    )

    test("PrintingRef in deck overrides CardComponent.imageUri at game-init") {
        val grizzly = CardDefinition.creature(
            name = "Grizzly Bears",
            manaCost = ManaCost.parse("{1}{G}"),
            subtypes = setOf(Subtype.BEAR),
            power = 2,
            toughness = 2,
            metadata = ScryfallMetadata(
                collectorNumber = "146",
                rarity = Rarity.COMMON,
                imageUri = "https://default-printing.test/grizzly.jpg",
            ),
        ).copy(setCode = "LEA", oracleId = "grizzly-oracle")

        val cards = CardRegistry().apply { register(grizzly) }
        val printings = PrintingRegistry().apply {
            register(bear("LEA", "146", "https://default-printing.test/grizzly.jpg"))
            register(bear("M10", "175", "https://m10.test/grizzly.jpg"))
            register(bear("2X2", "117", "https://2x2.test/grizzly.jpg"))
        }
        val initializer = GameInitializer(cards, printings)

        // Two copies pinned to LEA, one to M10, one to 2X2.
        val deck = Deck.fromEntries(
            entries = listOf(
                CardEntry("Grizzly Bears", PrintingRef("LEA", "146")),
                CardEntry("Grizzly Bears", PrintingRef("LEA", "146")),
                CardEntry("Grizzly Bears", PrintingRef("M10", "175")),
                CardEntry("Grizzly Bears", PrintingRef("2X2", "117")),
            ),
        )

        val result = initializer.initializeGame(
            GameConfig(
                players = listOf(
                    PlayerConfig("P1", deck, startingLife = 20),
                    PlayerConfig("P2", deck, startingLife = 20),
                ),
                skipMulligans = true,
                startingHandSize = 0,  // keep all four bears in the library
                useHandSmoother = false,
            )
        )

        val state = result.state
        val playerId = result.playerIds[0]
        val library = state.getZone(com.wingedsheep.engine.state.ZoneKey(playerId, Zone.LIBRARY))
        library.size shouldBe 4

        val imagesByEntity: Map<EntityId, String?> = library.associateWith { id ->
            state.getEntity(id)?.get<CardComponent>()?.imageUri
        }
        imagesByEntity.values.toList() shouldContainExactlyInAnyOrder listOf(
            "https://default-printing.test/grizzly.jpg",
            "https://default-printing.test/grizzly.jpg",
            "https://m10.test/grizzly.jpg",
            "https://2x2.test/grizzly.jpg",
        )

        // Owner is preserved across all entries — Deck.fromEntries doesn't mangle ownership.
        for (id in library) {
            state.getEntity(id)?.get<OwnerComponent>()?.playerId shouldBe playerId
        }
    }

    test("legacy Deck.cards path still works without a PrintingRegistry") {
        val grizzly = CardDefinition.creature(
            name = "Grizzly Bears",
            manaCost = ManaCost.parse("{1}{G}"),
            subtypes = setOf(Subtype.BEAR),
            power = 2,
            toughness = 2,
            metadata = ScryfallMetadata(
                collectorNumber = "146",
                imageUri = "https://default-printing.test/grizzly.jpg",
            ),
        ).copy(setCode = "LEA")

        val cards = CardRegistry().apply { register(grizzly) }
        val initializer = GameInitializer(cards)  // no printing registry

        val deck = Deck.of("Grizzly Bears" to 3)
        val result = initializer.initializeGame(
            GameConfig(
                players = listOf(
                    PlayerConfig("P1", deck),
                    PlayerConfig("P2", deck),
                ),
                skipMulligans = true,
                startingHandSize = 0,
                useHandSmoother = false,
            )
        )
        val playerId = result.playerIds[0]
        val library = result.state.getZone(com.wingedsheep.engine.state.ZoneKey(playerId, Zone.LIBRARY))
        for (id in library) {
            result.state.getEntity(id)?.get<CardComponent>()?.imageUri shouldBe
                "https://default-printing.test/grizzly.jpg"
        }
    }

    test("unresolved PrintingRef falls back to canonical CardDefinition.metadata") {
        val grizzly = CardDefinition.creature(
            name = "Grizzly Bears",
            manaCost = ManaCost.parse("{1}{G}"),
            subtypes = setOf(Subtype.BEAR),
            power = 2,
            toughness = 2,
            metadata = ScryfallMetadata(
                collectorNumber = "146",
                imageUri = "https://default-printing.test/grizzly.jpg",
            ),
        ).copy(setCode = "LEA")

        val cards = CardRegistry().apply { register(grizzly) }
        val printings = PrintingRegistry()  // intentionally empty
        val initializer = GameInitializer(cards, printings)

        val deck = Deck.fromEntries(
            entries = listOf(
                CardEntry("Grizzly Bears", PrintingRef("UNKNOWN", "999"))
            )
        )
        val result = initializer.initializeGame(
            GameConfig(
                players = listOf(
                    PlayerConfig("P1", deck),
                    PlayerConfig("P2", deck),
                ),
                skipMulligans = true,
                startingHandSize = 0,
                useHandSmoother = false,
            )
        )
        val playerId = result.playerIds[0]
        val library = result.state.getZone(com.wingedsheep.engine.state.ZoneKey(playerId, Zone.LIBRARY))
        library.size shouldBe 1
        val image = result.state.getEntity(library.first())?.get<CardComponent>()?.imageUri
        image shouldBe "https://default-printing.test/grizzly.jpg"
        image shouldNotBe null
    }
})
