package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.CardsDrawnEvent
import com.wingedsheep.engine.core.CardsRevealedEvent
import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.DrawFailedEvent
import com.wingedsheep.engine.core.OptionChosenResponse
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Both fixed choices and the reveal/filter/remainder pipeline use the real processor. */
class WindingWayScenarioTest : ScenarioTestBase() {
    init {
        listOf("Creature", "Land").forEach { choice ->
            test("choosing $choice takes exactly the matching revealed cards and leaves the fifth card untouched") {
                val game = scenario()
                    .withPlayers("Elves", "Opponent")
                    .withCardInHand(1, "Winding Way")
                    .withLandsOnBattlefield(1, "Forest", 2)
                    .withCardInLibrary(1, "Elvish Mystic")
                    .withCardInLibrary(1, "Forest")
                    .withCardInLibrary(1, "Fyndhorn Elves")
                    .withCardInLibrary(1, "Snow-Covered Forest")
                    .withCardInLibrary(1, "Expedition Map")
                    .withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .build()
                val libraryBefore = game.state.getLibrary(game.player1Id)
                val topFour = libraryBefore.take(4)
                val cast = game.castSpell(1, "Winding Way")
                cast.error shouldBe null
                val beforeChoice = game.resolveStack()
                beforeChoice.forEach { it.error shouldBe null }
                val decision = game.getPendingDecision().shouldBeInstanceOf<ChooseOptionDecision>()
                decision.options shouldBe listOf("Creature", "Land")
                game.state.getLibrary(game.player1Id) shouldBe libraryBefore
                beforeChoice.flatMap { it.events }.filterIsInstance<CardsRevealedEvent>() shouldBe emptyList()

                val selected = game.submitDecision(OptionChosenResponse(decision.id, decision.options.indexOf(choice)))
                selected.error shouldBe null
                val completed = game.resolveStack()
                completed.forEach { it.error shouldBe null }
                val events = cast.events + beforeChoice.flatMap { it.events } + selected.events + completed.flatMap { it.events }
                val reveals = events.filterIsInstance<CardsRevealedEvent>()
                reveals.any { it.cardIds.toSet() == topFour.toSet() } shouldBe true
                reveals.any { libraryBefore.last() in it.cardIds } shouldBe false
                events.filterIsInstance<CardsDrawnEvent>() shouldBe emptyList()
                events.filterIsInstance<DrawFailedEvent>() shouldBe emptyList()

                val creatures = listOf("Elvish Mystic", "Fyndhorn Elves")
                val lands = listOf("Forest", "Snow-Covered Forest")
                val selectedNames = if (choice == "Creature") creatures else lands
                val restNames = if (choice == "Creature") lands else creatures
                game.state.getHand(game.player1Id).map { game.state.getEntity(it)?.get<CardComponent>()?.name }
                    .shouldContainExactlyInAnyOrder(selectedNames)
                game.state.getGraveyard(game.player1Id).map { game.state.getEntity(it)?.get<CardComponent>()?.name }
                    .shouldContainExactlyInAnyOrder(restNames + "Winding Way")
                game.state.getLibrary(game.player1Id) shouldBe libraryBefore.drop(4)
                game.state.stack shouldBe emptyList()
                game.getPendingDecision() shouldBe null
            }
        }

        test("a short library reveals only available cards and does not draw beyond them") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Winding Way")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInLibrary(1, "Elvish Mystic")
                .withCardInLibrary(1, "Expedition Map")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val libraryBefore = game.state.getLibrary(game.player1Id)
            val cast = game.castSpell(1, "Winding Way")
            cast.error shouldBe null
            val beforeChoice = game.resolveStack()
            beforeChoice.forEach { it.error shouldBe null }
            val decision = game.getPendingDecision().shouldBeInstanceOf<ChooseOptionDecision>()
            decision.options shouldBe listOf("Creature", "Land")
            val selected = game.submitDecision(OptionChosenResponse(decision.id, 0))
            selected.error shouldBe null
            val completed = game.resolveStack()
            completed.forEach { it.error shouldBe null }
            val events = cast.events + beforeChoice.flatMap { it.events } + selected.events + completed.flatMap { it.events }

            events.filterIsInstance<CardsRevealedEvent>().any { it.cardIds.toSet() == libraryBefore.toSet() } shouldBe true
            events.filterIsInstance<CardsDrawnEvent>() shouldBe emptyList()
            events.filterIsInstance<DrawFailedEvent>() shouldBe emptyList()
            game.state.getLibrary(game.player1Id) shouldBe emptyList()
            game.isInHand(1, "Elvish Mystic") shouldBe true
            game.isInGraveyard(1, "Expedition Map") shouldBe true
            game.isInGraveyard(1, "Winding Way") shouldBe true
            game.state.gameOver shouldBe false
        }

        test("invalid option indexes fail without exposing or moving the library and the original choice remains available") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Winding Way")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Elvish Mystic")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            game.castSpell(1, "Winding Way").error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            val decision = game.getPendingDecision().shouldBeInstanceOf<ChooseOptionDecision>()
            decision.options shouldBe listOf("Creature", "Land")
            val before = game.state

            listOf(-1, decision.options.size).forEach { index ->
                val rejected = game.submitDecision(OptionChosenResponse(decision.id, index))
                rejected.error.shouldNotBeNull()
                rejected.state shouldBe before
                game.state shouldBe before
                rejected.events shouldBe emptyList()
                game.getPendingDecision() shouldBe decision
            }

            game.submitDecision(OptionChosenResponse(decision.id, 1)).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.isInHand(1, "Forest") shouldBe true
            game.isInGraveyard(1, "Elvish Mystic") shouldBe true
            game.isInGraveyard(1, "Winding Way") shouldBe true
        }
    }
}
