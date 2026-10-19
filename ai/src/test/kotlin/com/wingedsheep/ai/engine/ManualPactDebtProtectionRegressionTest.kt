package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.ai.engine.advisor.modules.ManualPactDebtAdvisor
import com.wingedsheep.ai.engine.advisor.modules.ManualPactDebtAdvisorModule
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.*
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/** Preservation contract: the candidate must actually protect and execute a lethal Ballista line. */
class ManualPactDebtProtectionRegressionTest : ScenarioTestBase() {
    init {
        MtgSetCatalog.all.forEach { cardRegistry.register(it.cards); cardRegistry.register(it.basicLands) }
        test("debt guard preserves actual lethal Ballista protection and AI damage continuation") {
            val builder = scenario().withPlayers("Manual", "Reference").withLifeTotal(2, 3)
                .withCardOnBattlefield(1, "Animar, Soul of Elements")
                .withCardInHand(1, "Walking Ballista").withCardInHand(1, "Pact of Negation")
                .withCardInHand(2, "Counterspell").withLandsOnBattlefield(2, "Island", 2)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            repeat(20) { builder.withCardInLibrary(1, "Forest"); builder.withCardInLibrary(2, "Forest") }
            val game = builder.build()
            val animar = game.findPermanent("Animar, Soul of Elements")!!
            game.state = game.state.updateEntity(animar) { it.with(CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 6))) }
            val original = ManualTransmissionR4Candidate.profile
            val guarded = original.copy(id = "manual-pact-debt-r5",
                advisorModules = original.advisorModules + ManualPactDebtAdvisorModule)
            val cast = AIPlayer.create(cardRegistry, game.player1Id, original).chooseAction(game.state) as CastSpell
            game.state.getEntity(cast.cardId)?.get<CardComponent>()?.name shouldBe "Walking Ballista"
            cast.xValue shouldBe 3
            game.execute(cast).error shouldBe null
            game.passPriority().error shouldBe null
            val counter = game.findCardsInHand(2, "Counterspell").single()
            game.execute(CastSpell(game.player2Id, counter, listOf(ChosenTarget.Spell(cast.cardId)))).error shouldBe null
            game.passPriority().error shouldBe null
            val before = game.state
            val originalChoice = AIPlayer.create(cardRegistry, game.player1Id, original).chooseAction(before)
            val guardedChoice = AIPlayer.create(cardRegistry, game.player1Id, guarded).chooseAction(before)
            val expectedProtection = CastSpell(game.player1Id, game.findCardsInHand(1, "Pact of Negation").single(), listOf(ChosenTarget.Spell(counter)))
            val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
            val output = Path.of(System.getenv("MANUAL_R5_PACT_OUTPUT") ?: "build/manual-r5-pact").resolve("ballista-protection")
            Files.createDirectories(output)
            Files.writeString(output.resolve("before.json"), json.encodeToString(before))
            Files.writeString(output.resolve("choices.json"), buildJsonObject {
                put("original", json.encodeToString<GameAction>(originalChoice))
                put("guarded", json.encodeToString<GameAction>(guardedChoice))
                put("expectedProtectionContract", json.encodeToString<GameAction>(expectedProtection))
            }.toString())
            val simulator = GameSimulator(cardRegistry)
            val evaluator = AIPlayer.defaultEvaluator()
            val offer = simulator.getLegalActions(before, game.player1Id).single {
                (it.action as? CastSpell)?.cardId == expectedProtection.cardId
            }.copy(action = expectedProtection)
            val resolved = simulator.simulate(before, expectedProtection) as SimulationResult.Terminal
            val passed = simulator.simulate(before, PassPriority(game.player1Id)) as SimulationResult.Terminal
            val ctx = CastContext(before, before.projectedState, game.player1Id, offer,
                evaluator.evaluate(passed.state, passed.state.projectedState, game.player1Id),
                evaluator.evaluate(resolved.state, resolved.state.projectedState, game.player1Id), evaluator, simulator)
            val debt = Files.newBufferedWriter(output.resolve("protection-debt-walk.jsonl")).use { walk ->
                ManualPactDebtAdvisor.nextUpkeepDebt(ctx, resolved.state) { state, action, reason ->
                    walk.write(buildJsonObject { put("turn", state.turnNumber); put("step", state.step.name)
                        put("priority", state.priorityPlayerId?.value); put("action", action?.let { json.encodeToString<GameAction>(it) })
                        put("reason", reason) }.toString()); walk.newLine(); walk.flush()
                }
            }
            Files.writeString(output.resolve("debt.json"), buildJsonObject { put("assessment", debt.name)
                put("quietStateIsTerminalWin", resolved.state.gameOver && resolved.state.winnerId == game.player1Id) }.toString())
            originalChoice shouldBe expectedProtection
            guardedChoice shouldBe expectedProtection
            game.execute(guardedChoice).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.getEntity(cast.cardId)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 3
            val pilots = game.state.turnOrder.associateWith { AIPlayer.create(cardRegistry, it, if (it == game.player1Id) guarded else AiProfile.CURRENT) }
            val actions = mutableListOf<String>()
            repeat(40) {
                if (!game.state.gameOver) {
                    val pending = game.state.pendingDecision
                    val actor = pending?.playerId ?: requireNotNull(game.state.priorityPlayerId)
                    val action = if (pending != null) SubmitDecision(actor, pilots.getValue(actor).respondToDecision(game.state, pending))
                        else pilots.getValue(actor).chooseAction(game.state)
                    val result = game.execute(action)
                    actions += buildJsonObject { put("action", json.encodeToString<GameAction>(action)); put("error", result.error)
                        put("events", JsonArray(result.events.map { JsonPrimitive(it.toString()) })) }.toString()
                    Files.writeString(output.resolve("actual-candidate-actions.jsonl"), actions.joinToString("\n", postfix = "\n"))
                    Files.writeString(output.resolve("actual-candidate-final.json"), json.encodeToString(game.state))
                    check(result.error == null)
                }
            }
            Files.writeString(output.resolve("actual-candidate-actions.jsonl"), actions.joinToString("\n", postfix = "\n"))
            Files.writeString(output.resolve("actual-candidate-final.json"), json.encodeToString(game.state))
            game.state.gameOver shouldBe true
            game.state.winnerId shouldBe game.player1Id
            check(actions.none { it.contains("DeclareAttackers") })
        }
    }
}
