package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.modules.ManualStatueContinuationAdvisorModule
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.*
import io.kotest.matchers.shouldBe

/** Real engine actions establish payoff and exit; no supplied damage or terminal outcomes. */
class ManualStatuePayoffRegressionTest : ScenarioTestBase() {
    init {
        MtgSetCatalog.all.forEach { cardRegistry.register(it.cards); cardRegistry.register(it.basicLands) }
        val profile = AiProfile.CURRENT.copy(id = "manual-statue-continuation-r3", advisorModules = listOf(ManualStatueContinuationAdvisorModule))
        fun board(purphoros: Boolean, protection: Boolean = false): TestGame {
            val builder = scenario().withPlayers("Manual", "Opponent").withLifeTotal(2, 4)
                .withCardOnBattlefield(1, "Animar, Soul of Elements")
                .withCardInHand(1, "Ancestral Statue")
                .withLandsOnBattlefield(1, "Forest", 4)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            if (purphoros) builder.withCardOnBattlefield(1, "Purphoros, God of the Forge")
            if (protection) builder.withCardOnBattlefield(2, "Platinum Emperion")
            val game = builder.build()
            val animar = game.state.getZone(ZoneKey(game.player1Id, Zone.BATTLEFIELD)).single {
                game.state.getEntity(it)?.get<CardComponent>()?.name == "Animar, Soul of Elements"
            }
            game.state = game.state.updateEntity(animar) { it.with(CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 4))) }
            return game
        }
        fun sim(game: TestGame): GameSimulator = GameSimulator(cardRegistry).apply {
            decisionResolver = { state, decision ->
                (AIPlayer.create(cardRegistry, decision.playerId, profile).chooseAction(state) as SubmitDecision).response
            }
        }
        test("real Purphoros damage preserves repeatable Statue line and reaches actual win") {
            val game = board(true)
            val simulator = sim(game)
            val statue = game.state.getZone(ZoneKey(game.player1Id, Zone.HAND)).single()
            var state = game.state
            repeat(2) {
                val legal = simulator.getLegalActions(state, game.player1Id).single { (it.action as? CastSpell)?.cardId == statue }
                legal.affordable shouldBe true
                val result = simulator.simulate(state, legal.action) as SimulationResult.Terminal
                state = result.state
                println("STATUE_PURPHOROS iteration=$it life=${state.getEntity(game.player2Id)?.get<LifeTotalComponent>()?.life} events=${result.events}")
                if (!state.gameOver) check(statue in state.getZone(ZoneKey(game.player1Id, Zone.HAND)))
            }
            state.gameOver shouldBe true
            state.winnerId shouldBe game.player1Id
        }
        test("counter only Statue cast uses legal finite exit instead of self return") {
            val game = board(false)
            val simulator = sim(game)
            val statue = game.state.getZone(ZoneKey(game.player1Id, Zone.HAND)).single()
            val result = simulator.simulate(game.state, CastSpell(game.player1Id, statue)) as SimulationResult.Terminal
            check(statue in result.state.getZone(ZoneKey(game.player1Id, Zone.BATTLEFIELD)))
            result.state.gameOver shouldBe false
            result.state.getEntity(game.player2Id)?.get<LifeTotalComponent>()?.life shouldBe 4
        }
        test("available real Ballista exit is chosen over another Statue counter iteration") {
            // Build the same real board with Ballista supplied as a genuine hand card.
            val builder = scenario().withPlayers("Manual", "Opponent").withLifeTotal(2, 2)
                .withCardOnBattlefield(1, "Animar, Soul of Elements")
                .withCardInHand(1, "Ancestral Statue").withCardInHand(1, "Walking Ballista")
                .withLandsOnBattlefield(1, "Forest", 4).withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val animar = builder.state.getZone(ZoneKey(builder.player1Id, Zone.BATTLEFIELD)).single()
            builder.state = builder.state.updateEntity(animar) { it.with(CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 4))) }
            val ai = AIPlayer.create(cardRegistry, builder.player1Id, profile)
            val chosen = ai.chooseAction(builder.state) as CastSpell
            builder.state.getEntity(chosen.cardId)?.get<CardComponent>()?.name shouldBe "Walking Ballista"
            val result = sim(builder).simulate(builder.state, chosen) as SimulationResult.Terminal
            val counters = result.state.getEntity(chosen.cardId)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
            check(counters > 0) { "Actual selected Ballista must enter with counters, not fictitious X" }
            println("STATUE_BALLISTA actualCast=$chosen actualCounters=$counters events=${result.events}")
            // The unchanged rules may still under-enumerate cost-reduced X; no nonexistent free X is credited.
            result.state.gameOver shouldBe false
        }
        test("unrealizable Purphoros damage does not authorize endless self bounce") {
            val game = board(true, true)
            val simulator = sim(game)
            val statue = game.state.getZone(ZoneKey(game.player1Id, Zone.HAND)).single()
            val result = simulator.simulate(game.state, CastSpell(game.player1Id, statue)) as SimulationResult.Terminal
            check(statue in result.state.getZone(ZoneKey(game.player1Id, Zone.BATTLEFIELD)))
            result.state.getEntity(game.player2Id)?.get<LifeTotalComponent>()?.life shouldBe 4
        }
    }
}
