package com.wingedsheep.ai.engine

import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.ai.llm.BottomCardsInfo
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

/**
 * Seedless Gate 2 diagnostics for deterministic land access in opening-hand decisions.
 *
 * These are exact synthetic hands backed by the canonical registered card definitions. They do
 * not shuffle a deck, generate a seed, execute a game, or inspect hidden library order.
 */
class PestControlMonoRedMadnessMulliganPolicyTest : ScenarioTestBase() {

    private fun summaries(game: TestGame, ids: List<com.wingedsheep.sdk.model.EntityId>) =
        ids.associateWith { id ->
            val card = requireNotNull(game.state.getEntity(id)?.get<CardComponent>())
            CardSummary(
                name = card.name,
                manaCost = card.manaCost.toString(),
                typeLine = card.typeLine.toString(),
                oracleText = card.oracleText,
            )
        }

    private fun controller(game: TestGame) = EngineAiPlayerController(
        cardRegistry,
        game.player1Id,
        gameStateProvider = { game.state },
    )

    private fun openingHand(vararg cardNames: String): TestGame {
        require(cardNames.size == 7)
        val builder = scenario().withPlayers("Opener", "Opponent")
        cardNames.forEach { builder.withCardInHand(1, it) }
        return builder.build()
    }

    private fun mulliganDecision(
        game: TestGame,
        hand: List<com.wingedsheep.sdk.model.EntityId> = game.state.getHand(game.player1Id),
        mulliganCount: Int = 0,
    ): Boolean {
        val cardSummaries = summaries(game, hand)
        return EngineAiPlayerController(
            cardRegistry,
            game.player1Id,
            gameStateProvider = { game.state },
        ).decideMulligan(
            MulliganInfo(
                hand = hand,
                mulliganCount = mulliganCount,
                cardsToPutOnBottom = mulliganCount,
                cards = cardSummaries,
                isOnThePlay = true,
            )
        )
    }

    private fun generousEntHand(
        openingLands: List<String>,
        libraryCards: List<String>,
        seventhCard: String = "Fierce Witchstalker",
    ): TestGame {
        val builder = scenario().withPlayers("Landcycler", "Opponent")
        openingLands.forEach { builder.withCardInHand(1, it) }
        listOf(
            "Generous Ent",
            "Essence Warden",
            "Carrier Thrall",
            "Blood Researcher",
            "Pest Mascot",
            seventhCard,
        ).take(7 - openingLands.size).forEach { builder.withCardInHand(1, it) }
        libraryCards.forEach { builder.withCardInLibrary(1, it) }
        return builder.build()
    }

    init {
        test("keeps madness only when the opening hand has a usable discard path") {
            val withOutlet = scenario()
                .withPlayers("Mono Red", "Opponent")
                .withCardInHand(1, "Mountain")
                .withCardInHand(1, "Mountain")
                .withCardInHand(1, "Grab the Prize")
                .withCardInHand(1, "Fiery Temper")
                .withCardInHand(1, "Lightning Bolt")
                .withCardInHand(1, "Sneaky Snacker")
                .withCardInHand(1, "Kessig Flamebreather")
                .build()
            val withoutOutlet = scenario()
                .withPlayers("Mono Red", "Opponent")
                .withCardInHand(1, "Mountain")
                .withCardInHand(1, "Mountain")
                .withCardInHand(1, "Fiery Temper")
                .withCardInHand(1, "Fiery Temper")
                .withCardInHand(1, "Fiery Temper")
                .withCardInHand(1, "Guttersnipe")
                .withCardInHand(1, "Guttersnipe")
                .build()

            withClue("two Mountains cast Grab the Prize, which enables the represented madness line") {
                mulliganDecision(withOutlet) shouldBe true
            }
            withClue(
                "the outlet-free control has no spell it can cast from the opening resources and " +
                    "must not retain Fiery Temper on the premise that madness is available"
            ) {
                mulliganDecision(withoutOutlet) shouldBe false
            }
        }

        test("canonical Highway Robbery is random draw with Plot, not deterministic land access") {
            val highway = cardRegistry.requireCard("Highway Robbery")
            highway.manaCost.toString() shouldBe "{1}{R}"
            highway.oracleText shouldContain "Plot {1}{R}"
            highway.oracleText shouldNotContain "landcycling"

            val game = scenario()
                .withPlayers("Mono Red", "Opponent")
                .withCardInHand(1, "Mountain")
                .withCardInHand(1, "Highway Robbery")
                .withCardInHand(1, "Lightning Bolt")
                .withCardInHand(1, "Lava Dart")
                .withCardInHand(1, "Guttersnipe")
                .withCardInHand(1, "Grab the Prize")
                .withCardInHand(1, "Fiery Temper")
                .withCardInLibrary(1, "Mountain")
                .build()

            mulliganDecision(game) shouldBe false
        }

        test("requires nonempty executable evidence within the established early horizon") {
            val payableTwoDrop = openingHand(
                "Mountain", "Mountain", "Lightning Bolt", "Guttersnipe", "Guttersnipe",
                "Fiery Temper", "Fiery Temper",
            )
            val threeLandDevelopment = openingHand(
                "Mountain", "Mountain", "Mountain", "Guttersnipe", "Fiery Temper",
                "Fiery Temper", "Fiery Temper",
            )
            val emptyEarlySet = openingHand(
                "Mountain", "Mountain", "Guttersnipe", "Guttersnipe", "Guttersnipe",
                "Guttersnipe", "Fierce Witchstalker",
            )

            withClue("two deterministic lands make the one-mana development spell executable") {
                mulliganDecision(payableTwoDrop) shouldBe true
            }
            withClue("three deterministic Mountains make normal three-mana development executable") {
                mulliganDecision(threeLandDevelopment) shouldBe true
            }
            withClue("an empty early-action set cannot pass through zero color mismatches") {
                mulliganDecision(emptyEarlySet) shouldBe false
            }
        }

        test("applies curve sufficiency equally to madness and non-madness slow hands") {
            val normallyCastableMadness = openingHand(
                "Mountain", "Mountain", "Mountain", "Fiery Temper", "Guttersnipe",
                "Guttersnipe", "Guttersnipe",
            )
            val nonMadnessSlow = openingHand(
                "Mountain", "Mountain", "Guttersnipe", "Guttersnipe", "Guttersnipe",
                "Guttersnipe", "Fierce Witchstalker",
            )

            withClue("Fiery Temper retains its legitimate normal-cast value at three mana") {
                mulliganDecision(normallyCastableMadness) shouldBe true
            }
            withClue("two lands do not imply an unproven third land for a non-madness curve") {
                mulliganDecision(nonMadnessSlow) shouldBe false
            }
        }

        test("evaluates colorless and colored early mana semantically") {
            val colorlessAction = openingHand(
                "Mountain", "Mountain", "Campfire", "Guttersnipe", "Guttersnipe",
                "Fiery Temper", "Fiery Temper",
            )
            val wrongColor = openingHand(
                "Forest", "Forest", "Lightning Bolt", "Lightning Bolt", "Lightning Bolt",
                "Lightning Bolt", "Lightning Bolt",
            )

            withClue("a payable colorless action needs no colored source") {
                mulliganDecision(colorlessAction) shouldBe true
            }
            withClue("green lands cannot pay a red early spell") {
                mulliganDecision(wrongColor) shouldBe false
            }
        }

        test("counts target-dependent interaction but enforces mandatory controller costs") {
            val reactiveInteraction = openingHand(
                "Swamp", "Swamp", "Cast Down", "Pest Mascot", "Pest Mascot",
                "Blood Researcher", "Fierce Witchstalker",
            )
            val missingSacrifice = openingHand(
                "Mountain", "Mountain", "Shrapnel Blast", "Guttersnipe", "Guttersnipe",
                "Fiery Temper", "Fiery Temper",
            )

            withClue("Cast Down is mana-reachable without inventing a pregame target") {
                mulliganDecision(reactiveInteraction) shouldBe true
            }
            withClue("Shrapnel Blast cannot qualify without an artifact to sacrifice") {
                mulliganDecision(missingSacrifice) shouldBe false
            }
        }

        test("does not spend a newly played tapped land before a legal untap") {
            val game = openingHand(
                "Jungle Hollow", "Jungle Hollow", "Carrier Thrall", "Pest Mascot",
                "Blood Researcher", "Fierce Witchstalker", "Fierce Witchstalker",
            )

            mulliganDecision(game) shouldBe false
        }

        test("London bottoming preserves the only early development line") {
            val game = openingHand(
                "Mountain", "Mountain", "Lightning Bolt", "Guttersnipe", "Guttersnipe",
                "Fiery Temper", "Fiery Temper",
            )
            val hand = game.state.getHand(game.player1Id)
            val cardSummaries = summaries(game, hand)
            val bolt = hand.single { cardSummaries.getValue(it).name == "Lightning Bolt" }

            val bottomed = controller(game).chooseBottomCards(
                BottomCardsInfo(
                    hand = hand,
                    cardsToPutOnBottom = 2,
                    cards = cardSummaries,
                )
            )

            bottomed.size shouldBe 2
            bottomed shouldNotContain bolt
        }

        test("keeps a functional one-Forest hand with payable Generous Ent and a Forest target") {
            val oneLand = generousEntHand(
                openingLands = listOf("Forest"),
                libraryCards = listOf("Forest"),
            )
            val twoLandControl = generousEntHand(
                openingLands = listOf("Forest", "Forest"),
                libraryCards = listOf("Forest"),
            )

            withClue("the representative two-land hand passes every unchanged non-land-count gate") {
                mulliganDecision(twoLandControl) shouldBe true
            }
            withClue(
                "one Forest pays Forestcycling {1}, a legal Forest remains, and only the physical " +
                    "land-count gate differs from the passing control"
            ) {
                mulliganDecision(oneLand) shouldBe true
            }
        }

        test("keeps a second genuine one-land typecycler with a different land type") {
            val game = scenario()
                .withPlayers("Landcycler", "Opponent")
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Troll of Khazad-dûm")
                .withCardInHand(1, "Shambling Ghast")
                .withCardInHand(1, "Carrier Thrall")
                .withCardInHand(1, "Cast Down")
                .withCardInHand(1, "Chainer's Edict")
                .withCardInHand(1, "Bone Shards")
                .withCardInLibrary(1, "Swamp")
                .build()

            mulliganDecision(game) shouldBe true
        }

        test("does not treat Generous Ent as deterministic access when no Forest target remains") {
            val game = generousEntHand(
                openingLands = listOf("Forest"),
                libraryCards = listOf("Swamp"),
            )

            mulliganDecision(game) shouldBe false
        }

        test("does not credit a Forestcycling line whose sole land enters tapped") {
            val game = generousEntHand(
                openingLands = listOf("Jungle Hollow"),
                libraryCards = listOf("Forest"),
            )

            mulliganDecision(game) shouldBe false
        }

        test("does not credit a typecycler whose cost has an unavailable colored requirement") {
            val game = scenario()
                .withPlayers("Landcycler", "Opponent")
                .withCardInHand(1, "Ancient Tomb")
                .withCardInHand(1, "Fiery Fall")
                .withCardInHand(1, "Campfire")
                .withCardInHand(1, "Campfire")
                .withCardInHand(1, "Lightning Bolt")
                .withCardInHand(1, "Guttersnipe")
                .withCardInHand(1, "Fireblast")
                .withCardInLibrary(1, "Mountain")
                .build()

            mulliganDecision(game) shouldBe false
        }

        test("does not credit an ordinary cantrip as guaranteed land access") {
            val game = scenario()
                .withPlayers("Cantrip", "Opponent")
                .withCardInHand(1, "Island")
                .withCardInHand(1, "Ponder")
                .withCardInHand(1, "Counterspell")
                .withCardInHand(1, "Brainstorm")
                .withCardInHand(1, "Mulldrifter")
                .withCardInHand(1, "Sea Gate Oracle")
                .withCardInHand(1, "Aether Adept")
                .withCardInLibrary(1, "Island")
                .build()

            mulliganDecision(game) shouldBe false
        }

        test("does not credit typecycling when the acquisition card is not in the actual hand") {
            val game = scenario()
                .withPlayers("Landcycler", "Opponent")
                .withCardInHand(1, "Forest")
                .withCardInHand(1, "Essence Warden")
                .withCardInHand(1, "Carrier Thrall")
                .withCardInHand(1, "Blood Researcher")
                .withCardInHand(1, "Pest Mascot")
                .withCardInHand(1, "Fierce Witchstalker")
                .withCardInHand(1, "Weather the Storm")
                .withCardInGraveyard(1, "Generous Ent")
                .withCardInLibrary(1, "Forest")
                .build()
            val actualHand = game.state.getHand(game.player1Id)
            val entInGraveyard = game.state.getGraveyard(game.player1Id).single()
            val claimedHand = actualHand.dropLast(1) + entInGraveyard

            mulliganDecision(game, hand = claimedHand) shouldBe false
        }

        test("preserves zero-land excessive-land and representative ordinary land-count outcomes") {
            val zeroLand = scenario()
                .withPlayers("Zero", "Opponent")
                .withCardInHand(1, "Essence Warden")
                .withCardInHand(1, "Carrier Thrall")
                .withCardInHand(1, "Blood Researcher")
                .withCardInHand(1, "Pest Mascot")
                .withCardInHand(1, "Fierce Witchstalker")
                .withCardInHand(1, "Weather the Storm")
                .withCardInHand(1, "Cast Down")
                .build()
            val sixLand = scenario()
                .withPlayers("Flood", "Opponent")
                .withCardInHand(1, "Forest")
                .withCardInHand(1, "Forest")
                .withCardInHand(1, "Forest")
                .withCardInHand(1, "Forest")
                .withCardInHand(1, "Forest")
                .withCardInHand(1, "Forest")
                .withCardInHand(1, "Essence Warden")
                .build()
            val twoLand = generousEntHand(
                openingLands = listOf("Forest", "Forest"),
                libraryCards = listOf("Forest"),
            )
            val fiveLand = scenario()
                .withPlayers("Five", "Opponent")
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Swamp")
                .withCardInHand(1, "Shambling Ghast")
                .withCardInHand(1, "Bone Shards")
                .build()

            mulliganDecision(zeroLand) shouldBe false
            mulliganDecision(sixLand) shouldBe false
            mulliganDecision(twoLand) shouldBe true
            mulliganDecision(fiveLand) shouldBe true
        }

        test("London bottoming preserves the sole land and required landcycler") {
            val game = generousEntHand(
                openingLands = listOf("Forest"),
                libraryCards = listOf("Forest"),
            )
            val hand = game.state.getHand(game.player1Id)
            val cardSummaries = summaries(game, hand)
            val forest = hand.single { cardSummaries.getValue(it).name == "Forest" }
            val ent = hand.single { cardSummaries.getValue(it).name == "Generous Ent" }

            val bottomed = controller(game).chooseBottomCards(
                BottomCardsInfo(
                    hand = hand,
                    cardsToPutOnBottom = 2,
                    cards = cardSummaries,
                )
            )

            bottomed.size shouldBe 2
            bottomed shouldNotContain forest
            bottomed shouldNotContain ent
        }
    }
}
