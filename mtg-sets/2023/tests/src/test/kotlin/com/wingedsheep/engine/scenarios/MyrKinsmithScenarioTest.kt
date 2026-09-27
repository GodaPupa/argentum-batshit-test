package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CardsRevealedEvent
import com.wingedsheep.engine.core.LibrarySearchedEvent
import com.wingedsheep.engine.core.LibraryShuffledEvent
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.support.ScenarioTestBase
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Fixed, excluded tutor fixtures; no admitted candidate list or official allocation is used. */
class MyrKinsmithScenarioTest : ScenarioTestBase() {
    private fun kinsmithGame(includeMyr: Boolean = true): TestGame {
        val builder = scenario().withPlayers("Controller", "Opponent").withRngSeed(0x4b494e534d495448L)
            .withCardInHand(1, "Myr Kinsmith")
            .withLandsOnBattlefield(1, "Forest", 4)
            .withCardInLibrary(1, "Chromatic Star")
            .withCardInLibrary(1, "Grizzly Bears")
            .withCardInLibrary(1, "Island")
            .withCardInLibrary(1, "Mountain")
            .withCardInGraveyard(1, "Myr Retriever")
            .withCardInLibrary(2, "Myr Retriever")
        if (includeMyr) {
            builder.withCardInLibrary(1, "Myr Retriever")
                .withCardInLibrary(1, "Myr Enforcer")
        }
        return builder.build()
    }

    private fun beginSearch(game: TestGame) {
        game.castSpell(1, "Myr Kinsmith").error shouldBe null
        game.hasPendingDecision() shouldBe false
        game.resolveStack()
        game.isOnBattlefield("Myr Kinsmith") shouldBe true
        game.getPendingDecision().shouldBeInstanceOf<YesNoDecision>().playerId shouldBe game.player1Id
    }

    init {
        test("accepting the optional search offers only own library Myr cards and reveals the chosen card") {
            val game = kinsmithGame()
            val library = game.state.getLibrary(game.player1Id).toList()
            val opponentLibrary = game.state.getLibrary(game.player2Id).toList()
            val retriever = game.findCardsInLibrary(1, "Myr Retriever").single()
            val enforcer = game.findCardsInLibrary(1, "Myr Enforcer").single()
            beginSearch(game)

            val accept = game.answerYesNo(true)
            accept.error shouldBe null
            accept.events.filterIsInstance<CardsRevealedEvent>() shouldBe emptyList()
            val choice = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            choice.playerId shouldBe game.player1Id
            choice.options shouldContainExactlyInAnyOrder listOf(retriever, enforcer)
            choice.minSelections shouldBe 0
            choice.maxSelections shouldBe 1

            val selected = game.selectCards(listOf(retriever))
            selected.error shouldBe null
            val reveal = selected.events.filterIsInstance<CardsRevealedEvent>().single()
            reveal.revealingPlayerId shouldBe game.player1Id
            reveal.cardIds shouldBe listOf(retriever)
            reveal.cardNames shouldBe listOf("Myr Retriever")
            selected.events.filterIsInstance<LibraryShuffledEvent>().single().playerId shouldBe game.player1Id
            selected.events.filterIsInstance<LibrarySearchedEvent>().single().playerId shouldBe game.player1Id
            game.resolveStack()

            game.state.getHand(game.player1Id) shouldBe listOf(retriever)
            game.state.getLibrary(game.player1Id) shouldContainExactlyInAnyOrder (library - retriever)
            game.state.getLibrary(game.player2Id) shouldBe opponentLibrary
            game.findCardsInGraveyard(1, "Myr Retriever").size shouldBe 1
            game.hasPendingDecision() shouldBe false
        }

        test("declining the optional search preserves library order and emits no search reveal or shuffle") {
            val game = kinsmithGame()
            val library = game.state.getLibrary(game.player1Id).toList()
            beginSearch(game)
            val decline = game.answerYesNo(false)
            decline.error shouldBe null
            decline.events.filterIsInstance<LibrarySearchedEvent>() shouldBe emptyList()
            decline.events.filterIsInstance<CardsRevealedEvent>() shouldBe emptyList()
            decline.events.filterIsInstance<LibraryShuffledEvent>() shouldBe emptyList()
            game.resolveStack()

            game.state.getLibrary(game.player1Id) shouldBe library
            game.handSize(1) shouldBe 0
            game.isOnBattlefield("Myr Kinsmith") shouldBe true
            game.hasPendingDecision() shouldBe false
        }

        test("accepting then finding no card still searches and shuffles even when a Myr is available") {
            val game = kinsmithGame()
            val library = game.state.getLibrary(game.player1Id).toList()
            beginSearch(game)
            game.answerYesNo(true).error shouldBe null
            game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>().options.size shouldBe 2
            val selected = game.skipSelection()
            selected.error shouldBe null
            selected.events.filterIsInstance<CardsRevealedEvent>() shouldBe emptyList()
            selected.events.filterIsInstance<LibraryShuffledEvent>().single().playerId shouldBe game.player1Id
            selected.events.filterIsInstance<LibrarySearchedEvent>().single().playerId shouldBe game.player1Id
            game.resolveStack()

            game.state.getLibrary(game.player1Id) shouldContainExactlyInAnyOrder library
            game.handSize(1) shouldBe 0
            game.hasPendingDecision() shouldBe false
        }

        test("a library with no Myr still completes an accepted search and shuffle") {
            val game = kinsmithGame(includeMyr = false)
            val library = game.state.getLibrary(game.player1Id).toList()
            beginSearch(game)
            val accept = game.answerYesNo(true)
            accept.error shouldBe null
            accept.events.filterIsInstance<CardsRevealedEvent>() shouldBe emptyList()
            accept.events.filterIsInstance<LibraryShuffledEvent>().single().playerId shouldBe game.player1Id
            accept.events.filterIsInstance<LibrarySearchedEvent>().single().playerId shouldBe game.player1Id
            game.resolveStack()

            game.state.getLibrary(game.player1Id) shouldContainExactlyInAnyOrder library
            game.handSize(1) shouldBe 0
            game.hasPendingDecision() shouldBe false
        }

        test("non-Myr foreign-library and graveyard selections are rejected without consuming the choice") {
            val game = kinsmithGame()
            val legal = game.findCardsInLibrary(1, "Myr Retriever").single()
            val illegal = listOf(
                game.findCardsInLibrary(1, "Chromatic Star").single(),
                game.findCardsInLibrary(1, "Grizzly Bears").single(),
                game.findCardsInLibrary(2, "Myr Retriever").single(),
                game.findCardsInGraveyard(1, "Myr Retriever").single()
            )
            beginSearch(game)
            game.answerYesNo(true).error shouldBe null
            val before = game.state
            for (card in illegal) {
                game.selectCards(listOf(card)).isSuccess shouldBe false
                game.state shouldBe before
            }
            game.selectCards(listOf(legal)).error shouldBe null
            game.resolveStack()

            game.state.getHand(game.player1Id) shouldBe listOf(legal)
            game.hasPendingDecision() shouldBe false
        }
    }
}
