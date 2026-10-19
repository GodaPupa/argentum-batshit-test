package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.ai.engine.advisor.modules.ManualPactDebtAdvisor
import com.wingedsheep.ai.engine.advisor.modules.ManualPactDebtAdvisorModule
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import io.kotest.matchers.shouldBe

/** Real processor casts, priority, counters, delayed debt and terminal outcomes. */
class ManualPactDebtRegressionTest : ScenarioTestBase() {
    init {
        MtgSetCatalog.all.forEach { cardRegistry.register(it.cards); cardRegistry.register(it.basicLands) }
        val profile = ManualTransmissionR4Candidate.profile.copy(id = "manual-pact-debt-r5",
            advisorModules = ManualTransmissionR4Candidate.profile.advisorModules + ManualPactDebtAdvisorModule)
        fun candidate(game: TestGame) = AIPlayer.create(cardRegistry, game.player1Id, profile)
        fun context(game: TestGame, action: CastSpell): CastContext {
            val simulator = GameSimulator(cardRegistry)
            val evaluator = AIPlayer.defaultEvaluator()
            val offered = simulator.getLegalActions(game.state, action.playerId).single { (it.action as? CastSpell)?.cardId == action.cardId }.copy(action = action)
            val result = simulator.simulate(game.state, action) as SimulationResult.Terminal
            val pass = simulator.simulate(game.state, PassPriority(action.playerId)) as SimulationResult.Terminal
            return CastContext(game.state, game.state.projectedState, action.playerId, offered,
                evaluator.evaluate(pass.state, pass.state.projectedState, action.playerId),
                evaluator.evaluate(result.state, result.state.projectedState, action.playerId), evaluator, simulator)
        }
        fun ordinary(land: String?, tapped: Boolean = false): Pair<TestGame, CastSpell> {
            val builder = scenario().withPlayers("Manual", "Reference")
                .withActivePlayer(2).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                .withCardInHand(2, "Grizzly Bears").withLandsOnBattlefield(2, "Forest", 2)
                .withCardInHand(1, "Pact of Negation")
            if (land != null) builder.withLandsOnBattlefield(1, land, 5)
            val game = builder.build()
            if (tapped) game.state.projectedState.getBattlefieldControlledBy(game.player1Id).forEach { id ->
                game.state = game.state.updateEntity(id) { it.with(TappedComponent) }
            }
            game.castSpell(2, "Grizzly Bears").error shouldBe null
            val target = game.state.stack.last()
            game.passPriority().error shouldBe null
            return game to CastSpell(game.player1Id, game.findCardsInHand(1, "Pact of Negation").single(), listOf(ChosenTarget.Spell(target)))
        }
        test("ordinary early counter without debt resources is declined") {
            val (game, pact) = ordinary(null)
            val ctx = context(game, pact)
            val after = ctx.simulator.simulate(game.state, pact) as SimulationResult.Terminal
            ManualPactDebtAdvisor.nextUpkeepDebt(ctx, after.state) shouldBe ManualPactDebtAdvisor.Debt.UNFUNDED
            check(ManualPactDebtAdvisor.evaluateCast(ctx)!! < ctx.passScore)
            val chosen = candidate(game).chooseAction(game.state)
            chosen shouldBe PassPriority(game.player1Id)
            game.execute(chosen).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.passUntilPhase(Phase.ENDING, Step.END)
            game.passUntilPhase(Phase.BEGINNING, Step.UPKEEP)
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.activePlayerId shouldBe game.player1Id
            game.state.gameOver shouldBe false
            game.state.delayedTriggers.isEmpty() shouldBe true
            game.findCardsInHand(1, "Pact of Negation").size shouldBe 1
        }
        test("five untapped Islands preserve payable Pact") {
            val (game, pact) = ordinary("Island")
            val ctx = context(game, pact)
            val after = ctx.simulator.simulate(game.state, pact) as SimulationResult.Terminal
            ManualPactDebtAdvisor.nextUpkeepDebt(ctx, after.state) shouldBe ManualPactDebtAdvisor.Debt.FUNDED
            ManualPactDebtAdvisor.evaluateCast(ctx) shouldBe null
        }
        test("real next turn untap makes tapped Islands a valid debt witness") {
            val (game, pact) = ordinary("Island", tapped = true)
            val ctx = context(game, pact)
            val after = ctx.simulator.simulate(game.state, pact) as SimulationResult.Terminal
            ManualPactDebtAdvisor.nextUpkeepDebt(ctx, after.state) shouldBe ManualPactDebtAdvisor.Debt.FUNDED
            ManualPactDebtAdvisor.evaluateCast(ctx) shouldBe null
        }
        test("five green sources cannot fund two required blue mana") {
            val (game, pact) = ordinary("Forest")
            val ctx = context(game, pact)
            val after = ctx.simulator.simulate(game.state, pact) as SimulationResult.Terminal
            ManualPactDebtAdvisor.nextUpkeepDebt(ctx, after.state) shouldBe ManualPactDebtAdvisor.Debt.UNFUNDED
            check(ManualPactDebtAdvisor.evaluateCast(ctx)!! < ctx.passScore)
        }
        test("Pact remains an actual immediate lethal survival response without debt mana") {
            val game = scenario().withPlayers("Manual", "Reference").withActivePlayer(2)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).withLifeTotal(1, 2)
                .withCardInHand(1, "Pact of Negation").withCardInHand(2, "Lightning Bolt")
                .withLandsOnBattlefield(2, "Mountain", 1).build()
            val bolt = game.findCardsInHand(2, "Lightning Bolt").single()
            game.execute(CastSpell(game.player2Id, bolt, listOf(ChosenTarget.Player(game.player1Id)))).error shouldBe null
            game.passPriority().error shouldBe null
            val pact = CastSpell(game.player1Id, game.findCardsInHand(1, "Pact of Negation").single(), listOf(ChosenTarget.Spell(bolt)))
            val ctx = context(game, pact)
            val pass = ctx.simulator.simulate(game.state, PassPriority(game.player1Id)) as SimulationResult.Terminal
            pass.state.gameOver shouldBe true
            pass.state.winnerId shouldBe game.player2Id
            ManualPactDebtAdvisor.evaluateCast(ctx) shouldBe null
            val chosen = candidate(game).chooseAction(game.state)
            chosen shouldBe pact
            game.execute(chosen).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.gameOver shouldBe false
            game.getLifeTotal(1) shouldBe 2
        }
        test("Pact protecting actual winning Bolt counterwar is chosen and resolves terminal win") {
            val game = scenario().withPlayers("Manual", "Reference").withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).withLifeTotal(2, 3)
                .withCardInHand(1, "Lightning Bolt").withCardInHand(1, "Pact of Negation")
                .withCardInHand(2, "Counterspell").withLandsOnBattlefield(1, "Mountain", 1)
                .withLandsOnBattlefield(2, "Island", 2).build()
            val bolt = game.findCardsInHand(1, "Lightning Bolt").single()
            game.execute(CastSpell(game.player1Id, bolt, listOf(ChosenTarget.Player(game.player2Id)))).error shouldBe null
            game.passPriority().error shouldBe null
            val counter = game.findCardsInHand(2, "Counterspell").single()
            game.execute(CastSpell(game.player2Id, counter, listOf(ChosenTarget.Spell(bolt)))).error shouldBe null
            game.passPriority().error shouldBe null
            val pact = CastSpell(game.player1Id, game.findCardsInHand(1, "Pact of Negation").single(), listOf(ChosenTarget.Spell(counter)))
            val ctx = context(game, pact)
            val after = ctx.simulator.simulate(game.state, pact) as SimulationResult.Terminal
            after.state.gameOver shouldBe true
            after.state.winnerId shouldBe game.player1Id
            ManualPactDebtAdvisor.evaluateCast(ctx) shouldBe null
            val chosen = candidate(game).chooseAction(game.state)
            chosen shouldBe pact
            game.execute(chosen).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.gameOver shouldBe true
            game.state.winnerId shouldBe game.player1Id
        }
    }
}
