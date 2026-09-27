package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.TurnManager
import com.wingedsheep.engine.core.TypecycleCard
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** Prospective P03-05/P04-01 Ent typecycling offer receiving, not actor policy parity. */
class PestMonsterEntTypecycleReceivingTest : ScenarioTestBase() {
    private val projection = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator(cardRegistry, ManaSolver(cardRegistry),
        CostCalculator(cardRegistry), PredicateEvaluator(), ConditionEvaluator(), TurnManager(cardRegistry))

    init {
        for (seat in listOf(1, 2)) {
            test("P03-05 Monster seat $seat Ent pays discards and selects offered Forest") {
                val otherSeat = if (seat == 1) 2 else 1
                val game = scenario().withRngSeed(0xFE000E01L + seat)
                    .withPlayers(if (seat == 1) "Monster seat" else "Pest seat",
                        if (seat == 2) "Monster seat" else "Pest seat")
                    .withActivePlayer(seat).withPriorityPlayer(seat)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .withCardInHand(seat, "Generous Ent")
                    .withLandsOnBattlefield(seat, "Forest", 1)
                    .withCardInLibrary(seat, "Forest")
                    .withCardInLibrary(seat, "Urza's Tower")
                    .withCardInLibrary(otherSeat, "Swamp")
                    .build()
                val monster = if (seat == 1) game.player1Id else game.player2Id
                val ent = game.state.getHand(monster).single()
                val forest = game.state.getLibrary(monster).first { id ->
                    game.state.getEntity(id)!!.get<com.wingedsheep.engine.state.components.identity.CardComponent>()!!.name == "Forest"
                }
                val menu = completeActorLegalActions(game.state, monster, enumerator)
                val input = projection.build(game.state, monster, menu,
                    ActorEpoch("pest-monster-p03-05-v1", "monster-seat-$seat-action", 0),
                    0xFE000E01L + seat)
                val cycle = input.legalActions.map { it.action }.filterIsInstance<TypecycleCard>()
                    .single { it.cardId == ent }
                cycle.playerId shouldBe monster
                game.execute(cycle).error shouldBe null
                (ent in game.state.getGraveyard(monster)) shouldBe true
                val question = game.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                question.playerId shouldBe monster
                question.options shouldBe listOf(forest)
                val projected = projection.build(game.state, monster, emptyList(),
                    ActorEpoch("pest-monster-p03-05-v1", "monster-seat-$seat-search", 1),
                    0xFE000E01L + seat)
                projected.decision.shouldBeInstanceOf<SelectCardsDecision>().options shouldBe listOf(forest)
                game.execute(SubmitDecision(monster, CardsSelectedResponse(question.id, listOf(forest)))).error shouldBe null
                (forest in game.state.getHand(monster)) shouldBe true
                (forest in game.state.getLibrary(monster)) shouldBe false
            }
        }
    }
}
