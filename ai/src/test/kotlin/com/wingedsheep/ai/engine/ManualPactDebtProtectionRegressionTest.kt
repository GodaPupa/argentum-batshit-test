package com.wingedsheep.ai.engine

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

/** Diagnostic successor draft: tests actual choice versus a separately labelled legal protection fork. */
class ManualPactDebtProtectionRegressionTest : ScenarioTestBase() {
    init {
        MtgSetCatalog.all.forEach { cardRegistry.register(it.cards); cardRegistry.register(it.basicLands) }
        test("unfunded debt guard does not yet recognize protecting a lethal Ballista continuation") {
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
                put("legalProtectionFork", json.encodeToString<GameAction>(expectedProtection))
            }.toString())
            originalChoice shouldBe expectedProtection
            guardedChoice shouldBe PassPriority(game.player1Id)
            // Explicitly forced fork below proves the legal continuation, not that guarded AI chose it.
            game.execute(expectedProtection).error shouldBe null
            game.resolveStack().forEach { it.error shouldBe null }
            game.state.getEntity(cast.cardId)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE) shouldBe 3
            val pilots = game.state.turnOrder.associateWith { AIPlayer.create(cardRegistry, it, original) }
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
                    Files.writeString(output.resolve("legal-protection-fork-actions.jsonl"), actions.joinToString("\n", postfix = "\n"))
                    Files.writeString(output.resolve("legal-protection-fork-final.json"), json.encodeToString(game.state))
                    check(result.error == null)
                }
            }
            Files.writeString(output.resolve("legal-protection-fork-actions.jsonl"), actions.joinToString("\n", postfix = "\n"))
            Files.writeString(output.resolve("legal-protection-fork-final.json"), json.encodeToString(game.state))
            game.state.gameOver shouldBe true
            game.state.winnerId shouldBe game.player1Id
            check(actions.none { it.contains("DeclareAttackers") })
        }
    }
}
