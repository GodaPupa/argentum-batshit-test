package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CardsDrawnEvent
import com.wingedsheep.engine.core.CardsRevealedEvent
import com.wingedsheep.engine.core.DrawFailedEvent
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import io.kotest.matchers.shouldBe

/** Exact-card regression fixtures, excluded from the official Phase 2 seed registry and sample. */
class CoilingOracleScenarioTest : ScenarioTestBase() {
    private fun fixture(): ScenarioBuilder = scenario().withPlayers().withRngSeed(9250925014L)
        .withCardInHand(1, "Coiling Oracle")
        .withCardOnBattlefield(1, "Forest")
        .withCardOnBattlefield(1, "Island")

    private fun TestGame.resolveEvents(): List<GameEvent> {
        val results = resolveStack()
        results.forEach { it.error shouldBe null }
        state.stack.isEmpty() shouldBe true
        hasPendingDecision() shouldBe false
        return results.flatMap { it.events }
    }

    init {
        test("a revealed land enters without being drawn or consuming the turn's land play") {
            val game = fixture().withCardInLibrary(1, "Plains")
                .withCardInLibrary(1, "Grizzly Bears")
                .withCardInHand(1, "Mountain").build()
            val plains = game.findCardsInLibrary(1, "Plains").single()
            game.state.getLibrary(game.player1Id).first() shouldBe plains
            game.castSpell(1, "Coiling Oracle").error shouldBe null
            val events = game.resolveEvents()
            val reveal = events.filterIsInstance<CardsRevealedEvent>().single()
            reveal.revealingPlayerId shouldBe game.player1Id
            reveal.cardIds shouldBe listOf(plains)
            reveal.cardNames shouldBe listOf("Plains")
            events.filterIsInstance<CardsDrawnEvent>().isEmpty() shouldBe true
            game.findPermanent("Plains") shouldBe plains
            game.state.getEntity(plains)!!.has<TappedComponent>() shouldBe false
            game.findCardsInLibrary(1, "Grizzly Bears").size shouldBe 1
            game.isInHand(1, "Grizzly Bears") shouldBe false
            game.execute(PlayLand(game.player1Id, game.findCardsInHand(1, "Mountain").single())).error shouldBe null
            game.isOnBattlefield("Mountain") shouldBe true
        }

        test("a revealed nonland goes into hand without a draw event") {
            val game = fixture().withCardInLibrary(1, "Grizzly Bears").build()
            val bears = game.findCardsInLibrary(1, "Grizzly Bears").single()
            game.castSpell(1, "Coiling Oracle").error shouldBe null
            val events = game.resolveEvents()
            events.filterIsInstance<CardsRevealedEvent>().single().cardIds shouldBe listOf(bears)
            events.filterIsInstance<CardsDrawnEvent>().isEmpty() shouldBe true
            game.findCardsInHand(1, "Grizzly Bears") shouldBe listOf(bears)
            game.isOnBattlefield("Grizzly Bears") shouldBe false
            game.state.getLibrary(game.player1Id).isEmpty() shouldBe true
        }

        test("a revealed Simic Guildgate retains its enters-tapped replacement") {
            val game = fixture().withCardInLibrary(1, "Simic Guildgate").build()
            game.castSpell(1, "Coiling Oracle").error shouldBe null
            game.resolveEvents()
            val gate = game.findPermanent("Simic Guildgate")!!
            game.state.getEntity(gate)!!.has<TappedComponent>() shouldBe true
            game.state.projectedState.getController(gate) shouldBe game.player1Id
        }

        test("countering Oracle prevents its entry trigger and leaves the library untouched") {
            val game = fixture().withCardInLibrary(1, "Grizzly Bears")
                .withCardInHand(2, "Counterspell").withLandsOnBattlefield(2, "Island", 2).build()
            val library = game.state.getLibrary(game.player1Id)
            game.castSpell(1, "Coiling Oracle").error shouldBe null
            game.passPriority().error shouldBe null
            game.castSpellTargetingStackSpell(2, "Counterspell", "Coiling Oracle").error shouldBe null
            val events = game.resolveEvents()
            game.isInGraveyard(1, "Coiling Oracle") shouldBe true
            game.state.getLibrary(game.player1Id) shouldBe library
            events.filterIsInstance<CardsRevealedEvent>().isEmpty() shouldBe true
        }

        test("the entry trigger still reveals and moves a card after Oracle dies in response") {
            val game = fixture().withCardInLibrary(1, "Grizzly Bears")
                .withCardInHand(2, "Lightning Bolt").withCardOnBattlefield(2, "Mountain").build()
            game.castSpell(1, "Coiling Oracle").error shouldBe null
            game.passPriority().error shouldBe null
            game.passPriority().error shouldBe null
            val oracle = game.findPermanent("Coiling Oracle")!!
            game.state.stack.size shouldBe 1
            game.passPriority().error shouldBe null
            game.castSpell(2, "Lightning Bolt", oracle).error shouldBe null
            val events = game.resolveEvents()
            game.isInGraveyard(1, "Coiling Oracle") shouldBe true
            game.isInHand(1, "Grizzly Bears") shouldBe true
            events.filterIsInstance<CardsRevealedEvent>().single().revealingPlayerId shouldBe game.player1Id
        }

        test("returning Oracle with Momentary Blink triggers its entry without casting it") {
            val game = scenario().withPlayers().withRngSeed(9250925014L)
                .withCardOnBattlefield(1, "Coiling Oracle")
                .withCardInLibrary(1, "Grizzly Bears")
                .withCardInHand(1, "Momentary Blink")
                .withLandsOnBattlefield(1, "Plains", 2).build()
            game.castSpell(1, "Momentary Blink", game.findPermanent("Coiling Oracle")!!).error shouldBe null
            val events = game.resolveEvents()
            game.isOnBattlefield("Coiling Oracle") shouldBe true
            game.isInHand(1, "Grizzly Bears") shouldBe true
            events.filterIsInstance<CardsRevealedEvent>().size shouldBe 1
        }

        test("an empty library causes no failed draw and no game loss") {
            val game = fixture().build()
            game.state.getLibrary(game.player1Id).isEmpty() shouldBe true
            game.castSpell(1, "Coiling Oracle").error shouldBe null
            val events = game.resolveEvents()
            events.filterIsInstance<CardsDrawnEvent>().isEmpty() shouldBe true
            events.filterIsInstance<DrawFailedEvent>().isEmpty() shouldBe true
            game.state.gameOver shouldBe false
            game.isOnBattlefield("Coiling Oracle") shouldBe true
        }

        test("the second player's Oracle uses that player's library and reveals it publicly") {
            val game = scenario().withPlayers().withRngSeed(9250925014L)
                .withCardInHand(2, "Coiling Oracle")
                .withCardOnBattlefield(2, "Forest").withCardOnBattlefield(2, "Island")
                .withCardInLibrary(2, "Plains").withCardInLibrary(1, "Mountain")
                .withActivePlayer(2).build()
            game.castSpell(2, "Coiling Oracle").error shouldBe null
            val events = game.resolveEvents()
            events.filterIsInstance<CardsRevealedEvent>().single().revealingPlayerId shouldBe game.player2Id
            val plains = game.findPermanent("Plains")!!
            game.state.projectedState.getController(plains) shouldBe game.player2Id
            game.findCardsInLibrary(1, "Mountain").size shouldBe 1
        }
    }
}
