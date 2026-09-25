package com.wingedsheep.engine.scenarios

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CardsRevealedEvent
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Land Grant's cast-time reveal cost and Forest search, through the real action processor. */
class LandGrantScenarioTest : ScenarioTestBase() {
    init {
        test("the free alternative reveals the entire remaining hand and excludes the spell being cast") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Land Grant")
                .withCardInHand(1, "Elvish Mystic")
                .withCardInHand(1, "Fyndhorn Elves")
                .withCardInLibrary(1, "Forest")
                .withCardInLibrary(1, "Llanowar Elves")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val handBefore = game.state.getHand(game.player1Id)
            val landGrant = handBefore.single { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Land Grant"
            }
            val alternative = game.getLegalActions(1).single { legal ->
                val action = legal.action as? CastSpell
                action?.cardId == landGrant && action.useAlternativeCost
            }

            val cast = game.execute(alternative.action)
            withClue("The landless-hand alternative must be payable without mana: ${cast.error}") {
                cast.error shouldBe null
            }
            val reveal = cast.events.filterIsInstance<CardsRevealedEvent>().single()
            reveal.revealingPlayerId shouldBe game.player1Id
            reveal.source shouldBe "Land Grant"
            reveal.cardIds shouldContainExactlyInAnyOrder handBefore.filter { it != landGrant }
            reveal.cardNames shouldContainExactlyInAnyOrder listOf("Elvish Mystic", "Fyndhorn Elves")
            game.state.getHand(game.player1Id) shouldContainExactlyInAnyOrder reveal.cardIds
            game.state.stack.size shouldBe 1

            game.resolveStack().forEach { it.error shouldBe null }
            val search = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            search.minSelections shouldBe 0
            search.maxSelections shouldBe 1
            search.options.map { game.state.getEntity(it)?.get<CardComponent>()?.name } shouldBe listOf("Forest")
            game.selectCards(search.options).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }

            game.isInHand(1, "Forest") shouldBe true
            game.isInHand(1, "Elvish Mystic") shouldBe true
            game.isInHand(1, "Fyndhorn Elves") shouldBe true
            game.isInGraveyard(1, "Land Grant") shouldBe true
        }

        test("a land in hand prevents enumeration of the alternative cost") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Land Grant")
                .withCardInHand(1, "Bojuka Bog")
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val landGrant = game.state.getHand(game.player1Id).single { id ->
                game.state.getEntity(id)?.get<CardComponent>()?.name == "Land Grant"
            }

            game.getLegalActions(1).count { legal ->
                val action = legal.action as? CastSpell
                action?.cardId == landGrant && action.useAlternativeCost
            } shouldBe 0
        }

        test("a forged free cast with a land in hand fails before revealing or moving any card") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Land Grant")
                .withCardInHand(1, "Forest")
                .withCardInHand(1, "Elvish Mystic")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInLibrary(1, "Snow-Covered Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val before = game.state
            val landGrant = before.getHand(game.player1Id).single { id ->
                before.getEntity(id)?.get<CardComponent>()?.name == "Land Grant"
            }
            val result = game.execute(CastSpell(
                playerId = game.player1Id,
                cardId = landGrant,
                useAlternativeCost = true,
                alternativeCostType = AlternativeCostType.SELF_ALTERNATIVE,
            ))

            result.error.shouldNotBeNull()
            result.state shouldBe before
            game.state shouldBe before
            result.events shouldBe emptyList()
            game.getPendingDecision() shouldBe null
        }

        test("normal mana payment works with a land in hand and does not pay a reveal-hand cost") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Land Grant")
                .withCardInHand(1, "Forest")
                .withCardInHand(1, "Elvish Mystic")
                .withLandsOnBattlefield(1, "Forest", 2)
                .withCardInLibrary(1, "Snow-Covered Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()

            val cast = game.castSpell(1, "Land Grant")
            cast.error shouldBe null
            cast.events.filterIsInstance<CardsRevealedEvent>() shouldBe emptyList()
            game.resolveStack().forEach { it.error shouldBe null }
            val search = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            val select = game.selectCards(search.options)
            select.error shouldBe null
            val completed = game.resolveStack()
            completed.forEach { it.error shouldBe null }
            val revealedNames = (select.events + completed.flatMap { it.events })
                .filterIsInstance<CardsRevealedEvent>().flatMap { it.cardNames }
            revealedNames shouldBe listOf("Snow-Covered Forest")
            game.isInHand(1, "Forest") shouldBe true
            game.isInHand(1, "Elvish Mystic") shouldBe true
            game.isInHand(1, "Snow-Covered Forest") shouldBe true
        }

        test("the alternative cost is payable when the spell is the only card in hand") {
            val game = scenario()
                .withPlayers("Elves", "Opponent")
                .withCardInHand(1, "Land Grant")
                .withCardInLibrary(1, "Forest")
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .build()
            val landGrant = game.state.getHand(game.player1Id).single()
            val alternative = game.getLegalActions(1).single { legal ->
                val action = legal.action as? CastSpell
                action?.cardId == landGrant && action.useAlternativeCost
            }
            val cast = game.execute(alternative.action)
            cast.error shouldBe null
            val reveal = cast.events.filterIsInstance<CardsRevealedEvent>().single()
            reveal.cardIds shouldBe emptyList()
            reveal.cardNames shouldBe emptyList()
            game.state.getHand(game.player1Id) shouldBe emptyList()

            game.resolveStack().forEach { it.error shouldBe null }
            val search = game.getPendingDecision().shouldBeInstanceOf<SelectCardsDecision>()
            game.selectCards(search.options).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.isInHand(1, "Forest") shouldBe true
            game.isInGraveyard(1, "Land Grant") shouldBe true
        }
    }
}
