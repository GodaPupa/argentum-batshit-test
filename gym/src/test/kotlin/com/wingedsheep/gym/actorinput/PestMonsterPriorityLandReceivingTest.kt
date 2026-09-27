package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.TurnManager
import com.wingedsheep.engine.handlers.ConditionEvaluator
import com.wingedsheep.engine.handlers.PredicateEvaluator
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.mechanics.mana.CostCalculator
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/** Prospective P03-01 land-menu receiving only; no policy or matchup outcome is inferred. */
class PestMonsterPriorityLandReceivingTest : ScenarioTestBase() {
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator(cardRegistry, ManaSolver(cardRegistry),
        CostCalculator(cardRegistry), PredicateEvaluator(), ConditionEvaluator(), TurnManager(cardRegistry))

    init {
        listOf(
            Triple(1, "Jungle Hollow", "Forest"),
            Triple(2, "Urza's Mine", "Bojuka Bog"),
        ).forEachIndexed { index, (seat, firstLand, secondLand) ->
            test("P03-01 seat $seat physical land menu and exhausted drop") {
                val game = scenario().withRngSeed(0xFE000201L + index)
                    .withPlayers("Pest seat", "Monster seat")
                    .withActivePlayer(seat).withPriorityPlayer(seat)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .withCardInHand(seat, firstLand).withCardInHand(seat, secondLand)
                    .withCardInLibrary(1, "Forest").withCardInLibrary(2, "Forest")
                    .build()
                val actor = if (seat == 1) game.player1Id else game.player2Id
                val hand = game.state.getHand(actor)
                val menu = completeActorLegalActions(game.state, actor, enumerator)
                val input = adapter.build(game.state, actor, menu,
                    ActorEpoch("pest-monster-p03-01-v1", "seat-$seat", 0), 0xFE000201L + index)
                val landActions = input.legalActions.map { it.action }.filterIsInstance<PlayLand>()
                landActions.map { it.cardId }.toSet() shouldBe hand.toSet()
                landActions.all { it.playerId == actor } shouldBe true
                val chosen = landActions.single { action ->
                    game.state.getEntity(action.cardId)!!.get<com.wingedsheep.engine.state.components.identity.CardComponent>()!!.name == firstLand
                }
                game.execute(chosen).error shouldBe null
                (chosen.cardId in game.state.getBattlefield()) shouldBe true
                (chosen.cardId in game.state.getHand(actor)) shouldBe false
                val after = completeActorLegalActions(game.state, actor, enumerator)
                after.none { it.action is PlayLand } shouldBe true
            }
        }
    }
}
