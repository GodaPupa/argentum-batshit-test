package com.wingedsheep.gym.actorinput

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PassPriority
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
import com.wingedsheep.sdk.scripting.AdditionalCostPayment
import io.kotest.matchers.shouldBe

/** Prospective P03-02/P03-08 timing fixture; policy selection remains unqualified. */
class PestMonsterPriorityResponseReceivingTest : ScenarioTestBase() {
    private val projection = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator(cardRegistry, ManaSolver(cardRegistry),
        CostCalculator(cardRegistry), PredicateEvaluator(), ConditionEvaluator(), TurnManager(cardRegistry))

    init {
        listOf(1, 2).forEach { monsterSeat ->
            test("P03-02 Monster seat $monsterSeat passes Crop stack to Pest Weather response") {
                val pestSeat = if (monsterSeat == 1) 2 else 1
                val game = scenario().withRngSeed(0xFE000501L + monsterSeat)
                    .withPlayers(if (monsterSeat == 1) "Monster seat" else "Pest seat",
                        if (monsterSeat == 2) "Monster seat" else "Pest seat")
                    .withActivePlayer(monsterSeat).withPriorityPlayer(monsterSeat)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                    .withCardInHand(monsterSeat, "Crop Rotation")
                    .withCardInHand(pestSeat, "Weather the Storm")
                    .withLandsOnBattlefield(monsterSeat, "Forest", 2)
                    .withLandsOnBattlefield(pestSeat, "Forest", 2)
                    .withCardInLibrary(monsterSeat, "Urza's Tower")
                    .withCardInLibrary(pestSeat, "Swamp")
                    .build()
                val monster = if (monsterSeat == 1) game.player1Id else game.player2Id
                val pest = if (monsterSeat == 1) game.player2Id else game.player1Id
                val crop = game.state.getHand(monster).single()
                val land = game.state.getBattlefield(monster).first()
                game.execute(CastSpell(monster, crop,
                    additionalCostPayment = AdditionalCostPayment(sacrificedPermanents = listOf(land))))
                    .error shouldBe null
                game.execute(PassPriority(monster)).error shouldBe null
                game.state.priorityPlayerId shouldBe pest
                val menu = completeActorLegalActions(game.state, pest, enumerator)
                val input = projection.build(game.state, pest, menu,
                    ActorEpoch("pest-monster-p03-02-v1", "monster-seat-$monsterSeat", 0), 0xFE000501L + monsterSeat)
                val weather = game.state.getHand(pest).single()
                val response = input.legalActions.map { it.action }.filterIsInstance<CastSpell>()
                    .single { it.cardId == weather }
                game.state.getEntity(response.cardId)!!.get<CardComponent>()!!.name shouldBe "Weather the Storm"
                game.execute(response).error shouldBe null
                game.state.stack.size shouldBe 2
                game.state.stack.last() shouldBe response.cardId
            }
        }
    }
}
