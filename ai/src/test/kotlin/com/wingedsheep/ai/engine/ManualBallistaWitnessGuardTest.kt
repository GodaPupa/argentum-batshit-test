package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.ai.engine.advisor.modules.ManualBallistaContinuationAdvisor
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.*
import io.kotest.matchers.shouldBe

/** Terminal witnesses must honor actual targeting and actual loss prevention, not summed life. */
class ManualBallistaWitnessGuardTest : ScenarioTestBase() {
    init {
        MtgSetCatalog.all.forEach { cardRegistry.register(it.cards); cardRegistry.register(it.basicLands) }
        for (protection in listOf("True Believer", "Platinum Angel")) {
            test("no invented terminal witness through $protection") {
                val game = scenario().withPlayers("Manual", "Protected").withLifeTotal(1, 40).withLifeTotal(2, 3)
                    .withCardOnBattlefield(1, "Animar, Soul of Elements").withCardOnBattlefield(2, protection)
                    .withCardInHand(1, "Walking Ballista").withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
                val animar = game.state.getZone(ZoneKey(game.player1Id, Zone.BATTLEFIELD)).single {
                    game.state.getEntity(it)?.get<CardComponent>()?.name == "Animar, Soul of Elements"
                }
                game.state = game.state.updateEntity(animar) { it.with(CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 6))) }
                val sim = GameSimulator(cardRegistry)
                val legal = sim.getLegalActions(game.state, game.player1Id).single {
                    it.affordable && (it.action as? CastSpell)?.xValue == 3
                }
                val after = sim.simulate(game.state, legal.action) as SimulationResult.Terminal
                val evaluator = AIPlayer.defaultEvaluator()
                val context = CastContext(game.state, game.state.projectedState, game.player1Id, legal,
                    evaluator.evaluate(game.state, game.state.projectedState, game.player1Id),
                    evaluator.evaluate(after.state, after.state.projectedState, game.player1Id), evaluator, sim)
                ManualBallistaContinuationAdvisor.evaluateCast(context) shouldBe null
                println("R4_WITNESS_GUARD=$protection override=null actualCast=${legal.action}")
            }
        }
    }
}
