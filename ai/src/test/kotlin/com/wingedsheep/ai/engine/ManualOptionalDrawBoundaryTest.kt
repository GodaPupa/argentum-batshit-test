package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.*
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/** Existing legal draw behavior must survive any future optional-draw candidate. */
class ManualOptionalDrawBoundaryTest : ScenarioTestBase() {
    init {
        MtgSetCatalog.all.forEach { cardRegistry.register(it.cards); cardRegistry.register(it.basicLands) }
        for (winningLastCard in listOf(false, true)) {
            test(if (winningLastCard) "R4 pilot accepts a last-card draw and executes actual Ballista win" else "R4 pilot already declines an optional immediate empty-library loss") {
                val builder = scenario().withPlayers("Manual", "Reference").withRngSeed(202610200502L)
                    .withLifeTotal(1, 40).withLifeTotal(2, 3)
                    .withCardOnBattlefield(1, "Animar, Soul of Elements")
                    .withCardOnBattlefield(1, "Soul of the Harvest")
                    .withCardInHand(1, "Ornithopter").withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                if (winningLastCard) builder.withCardInLibrary(1, "Walking Ballista")
                repeat(20) { builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()
                val animar = game.findPermanent("Animar, Soul of Elements")!!
                game.state = game.state.updateEntity(animar) { it.with(CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to 6))) }
                val ornithopter = game.state.getZone(ZoneKey(game.player1Id, Zone.HAND)).single()
                // Initialize a genuine trigger decision by casting and resolving an actual creature.
                val trigger = GameSimulator(cardRegistry).simulate(game.state, CastSpell(game.player1Id, ornithopter)) as SimulationResult.NeedsDecision
                game.state = trigger.state
                val decision = trigger.decision as YesNoDecision
                decision.context.sourceName shouldBe "Soul of the Harvest"
                val pilots = game.state.turnOrder.associateWith { AIPlayer.create(cardRegistry, it, ManualTransmissionR4Candidate.profile) }
                val response = pilots.getValue(game.player1Id).respondToDecision(game.state, decision) as YesNoResponse
                response.choice shouldBe winningLastCard
                val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
                val initial = json.encodeToString(game.state)
                val records = mutableListOf<String>()
                var count = 0
                val limit = if (winningLastCard) 100 else 1
                while (count < limit) {
                    val state = game.state
                    if (state.gameOver) break
                    val pending = state.pendingDecision
                    val actor = pending?.playerId ?: requireNotNull(state.priorityPlayerId)
                    val action: GameAction = if (pending != null) SubmitDecision(actor, pilots.getValue(actor).respondToDecision(state, pending))
                        else pilots.getValue(actor).chooseAction(state)
                    check(action !is DeclareAttackers) { "Combat cannot replace the asserted Ballista draw payoff" }
                    val result = game.execute(action)
                    check(result.error == null)
                    count++
                    records += buildJsonObject {
                        put("index", count); put("action", json.encodeToString<GameAction>(action))
                        put("events", JsonArray(result.events.map { JsonPrimitive(it.toString()) }))
                        put("library", game.state.getZone(ZoneKey(game.player1Id, Zone.LIBRARY)).size)
                        put("gameOver", game.state.gameOver); put("winner", game.state.winnerId?.value)
                    }.toString()
                }
                game.state.gameOver shouldBe winningLastCard
                if (winningLastCard) game.state.winnerId shouldBe game.player1Id
                val output = Path.of(System.getenv("MANUAL_R5_DRAW_OUTPUT") ?: "build/manual-r5-draw").resolve(if (winningLastCard) "last-card-win" else "empty-library-decline")
                Files.createDirectories(output)
                Files.writeString(output.resolve("initial-state.json"), initial)
                Files.writeString(output.resolve("actions.jsonl"), records.joinToString("\n", postfix = "\n"))
                val summary = buildJsonObject { put("actions", count); put("acceptedDraw", response.choice); put("winner", game.state.winnerId?.value); put("gameOver", game.state.gameOver); put("forcedActionsAfterDecision", 0) }
                Files.writeString(output.resolve("summary.json"), summary.toString())
                println("R5_OPTIONAL_DRAW_BOUNDARY=$summary")
            }
        }
    }
}
