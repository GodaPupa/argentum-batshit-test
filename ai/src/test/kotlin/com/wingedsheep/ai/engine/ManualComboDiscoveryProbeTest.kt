package com.wingedsheep.ai.engine

import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/** Honest constructed puzzle states, not sampled pod wins. CURRENT supplies every subsequent action. */
class ManualComboDiscoveryProbeTest : ScenarioTestBase() {
    init {
        listOf("statue-purphoros", "ballista").forEachIndexed { index, puzzle ->
            test("CURRENT bounded discovery probe $puzzle") {
                val builder = scenario().withPlayers("Manual", "Opponent")
                    .withRngSeed(202610190820L + index)
                    .withCardOnBattlefield(1, "Animar, Soul of Elements")
                    .withCardInHand(1, if (index == 0) "Ancestral Statue" else "Walking Ballista")
                    .withLifeTotal(1, 40).withLifeTotal(2, if (index == 0) 40 else 3)
                    .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                if (index == 0) builder.withCardOnBattlefield(1, "Purphoros, God of the Forge")
                // Avoid an empty-library loss obscuring the puzzle if the AI passes its first main.
                repeat(20) { builder.withCardInLibrary(1, "Forest"); builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()
                val animar = game.findPermanent("Animar, Soul of Elements")!!
                game.state = game.state.updateEntity(animar) {
                    it.with(CountersComponent().withAdded(CounterType.PLUS_ONE_PLUS_ONE, if (index == 0) 4 else 6))
                }
                val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
                val records = mutableListOf<String>()
                val insights = mutableListOf<String>()
                val sink = AiInsightSink { _, insight -> insights += json.encodeToString(insight) }
                val pilots = listOf(game.player1Id, game.player2Id).associateWith {
                    AIPlayer.create(cardRegistry, it, AiProfile.CURRENT, insightSink = sink)
                }
                val initial = json.encodeToString(game.state)
                var submitted = 0
                var casts = 0
                var rejection: String? = null
                var status = "RESOURCE_CAP"
                val started = System.nanoTime()
                while (submitted < 200 && (System.nanoTime() - started) / 1_000_000 < 60_000) {
                    val state = game.state
                    if (state.gameOver) { status = "GAME_OVER"; break }
                    val pending = state.pendingDecision
                    val actor = pending?.playerId ?: requireNotNull(state.priorityPlayerId)
                    val action: GameAction = if (pending != null)
                        SubmitDecision(actor, pilots.getValue(actor).respondToDecision(state, pending))
                    else pilots.getValue(actor).chooseAction(state)
                    val name = (action as? CastSpell)?.cardId?.let { state.getEntity(it)?.get<CardComponent>()?.name }
                    val result = game.execute(action)
                    submitted++
                    casts += result.events.filterIsInstance<SpellCastEvent>().count {
                        it.cardName == "Ancestral Statue" || it.cardName == "Walking Ballista"
                    }
                    records += buildJsonObject {
                        put("index", submitted); put("turn", state.turnNumber); put("actor", actor.value)
                        put("sourceName", name); put("action", json.encodeToString<GameAction>(action))
                        put("events", JsonArray(result.events.map { JsonPrimitive(it.toString()) }))
                        put("error", result.error?.toString()); put("life1", game.getLifeTotal(1)); put("life2", game.getLifeTotal(2))
                        put("pendingBefore", pending?.toString()); put("gameOver", game.state.gameOver)
                    }.toString()
                    if (result.error != null) { rejection = result.error.toString(); status = "ENGINE_REJECTION"; break }
                }
                if (game.state.gameOver) status = "GAME_OVER"
                val summary = buildJsonObject {
                    put("puzzle", puzzle); put("profile", AiProfile.CURRENT.id); put("status", status)
                    put("submitted", submitted); put("comboCardCasts", casts); put("error", rejection)
                    put("life1", game.getLifeTotal(1)); put("life2", game.getLifeTotal(2))
                    put("gameOver", game.state.gameOver); put("winner", game.state.winnerId?.value)
                    put("interpretation", "Constructed two-player discovery probe; no actions or outcomes forced after initialization")
                }
                System.getenv("MANUAL_MECHANICS_OUTPUT")?.let { directory ->
                    val output = Path.of(directory).resolve(puzzle)
                    Files.createDirectories(output)
                    Files.writeString(output.resolve("initial-state.json"), initial)
                    Files.writeString(output.resolve("actions.jsonl"), records.joinToString("\n", postfix = "\n"))
                    Files.writeString(output.resolve("decisions.jsonl"), insights.joinToString("\n", postfix = "\n"))
                    Files.writeString(output.resolve("summary.json"), summary.toString())
                    Files.writeString(output.resolve("final-state.json"), json.encodeToString(game.state))
                }
                println("MANUAL_COMBO_DISCOVERY=$summary")
                check(submitted > 0)
                check(rejection == null) { "Actual CURRENT action rejected: $rejection" }
            }
        }
    }
}
