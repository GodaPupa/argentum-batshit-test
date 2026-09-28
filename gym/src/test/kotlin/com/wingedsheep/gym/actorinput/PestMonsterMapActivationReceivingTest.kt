package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.TurnManager
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Prospective P03-04/P04-01 Map activation and current search offer; no policy parity. */
class PestMonsterMapActivationReceivingTest : ScenarioTestBase() {
    private val projection = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator(cardRegistry, ManaSolver(cardRegistry),
        CostCalculator(cardRegistry), PredicateEvaluator(), ConditionEvaluator(), TurnManager(cardRegistry))

    init {
        for (seat in listOf(1, 2)) {
            test("P03-04 Monster seat $seat Map pays sacrifices and selects offered Tower") {
                val otherSeat = if (seat == 1) 2 else 1
                val game = scenario().withRngSeed(0xFE000F01L + seat)
                    .withPlayers(if (seat == 1) "Monster seat" else "Pest seat",
                        if (seat == 2) "Monster seat" else "Pest seat")
                    .withActivePlayer(seat).withPriorityPlayer(seat)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .withCardOnBattlefield(seat, "Expedition Map")
                    .withLandsOnBattlefield(seat, "Forest", 2)
                    .withCardInLibrary(seat, "Urza's Tower")
                    .withCardInLibrary(seat, "Forest")
                    .withCardInLibrary(otherSeat, "Swamp")
                    .build()
                val monster = if (seat == 1) game.player1Id else game.player2Id
                val map = game.state.getBattlefield(monster).single { id ->
                    game.state.getEntity(id)!!.get<CardComponent>()!!.name == "Expedition Map"
                }
                val tower = game.state.getLibrary(monster).single { id ->
                    game.state.getEntity(id)!!.get<CardComponent>()!!.name == "Urza's Tower"
                }
                val forest = game.state.getLibrary(monster).single { id ->
                    game.state.getEntity(id)!!.get<CardComponent>()!!.name == "Forest"
                }
                val menu = completeActorLegalActions(game.state, monster, enumerator)
                val input = projection.build(game.state, monster, menu,
                    ActorEpoch("pest-monster-p03-04-v1", "monster-seat-$seat-action", 0),
                    0xFE000F01L + seat)
                val activation = input.legalActions.map { it.action }.filterIsInstance<ActivateAbility>()
                    .single { it.sourceId == map }
                activation.playerId shouldBe monster
                game.execute(activation).error shouldBe null
                (map in game.state.getGraveyard(monster)) shouldBe true
                game.resolveStack().forEach { it.error shouldBe null }
                val question = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                question.playerId shouldBe monster
                question.options.toSet() shouldBe setOf(tower, forest)
                (question.authorizedLibrarySearch != null) shouldBe true
                val projected = projection.build(game.state, monster, emptyList(),
                    ActorEpoch("pest-monster-p04-01-v1", "monster-seat-$seat-search", 1),
                    0xFE000F01L + seat)
                projected.decision.shouldBeInstanceOf<SelectCardsDecision>().options.toSet() shouldBe
                    setOf(tower, forest)
                game.execute(SubmitDecision(monster,
                    CardsSelectedResponse(question.id, listOf(tower)))).error shouldBe null
                (tower in game.state.getHand(monster)) shouldBe true
                (tower in game.state.getLibrary(monster)) shouldBe false
            }
        }
    }
}
