package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.modules.ManualStatueContinuationAdvisorModule
import com.wingedsheep.ai.engine.advisor.modules.ManualBallistaContinuationAdvisorModule
import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/** Constructed discovery states. Every action after initialization belongs to a real AI pilot. */
class ManualBallistaAiDiscoveryTest : ScenarioTestBase() {
    init {
        MtgSetCatalog.all.forEach { cardRegistry.register(it.cards); cardRegistry.register(it.basicLands) }
        val statueProfile = AiProfile.CURRENT.copy(id = "manual-statue-continuation-r3", advisorModules = listOf(ManualStatueContinuationAdvisorModule))
        val candidate = AiProfile.CURRENT.copy(id = "manual-ballista-continuation-r4", advisorModules = listOf(ManualBallistaContinuationAdvisorModule))
        for (profile in listOf(AiProfile.CURRENT, statueProfile, candidate)) for (puzzle in listOf("ready", "counter-build", "pod-counter-build", "pod-ready")) {
            test("actual AI ${profile.id} $puzzle") {
                val buildCounters = puzzle.endsWith("counter-build")
                val pod = puzzle.startsWith("pod")
                val initialCounters = if (pod) { if (buildCounters) 8 else 12 } else if (buildCounters) 4 else 6
                val label = "${profile.id}-$puzzle"
                val builder = scenario().withPlayers("Manual", "Reference").withLifeTotal(1, 40).withLifeTotal(2, if (pod) 2 else 3)
                    .withRngSeed(202610200901L).withCardOnBattlefield(1, "Animar, Soul of Elements")
                    .withCardInHand(1, "Walking Ballista").withActivePlayer(1)
                    .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
                if (buildCounters) builder.withCardInHand(1, "Ancestral Statue")
                repeat(30) { builder.withCardInLibrary(1, "Forest"); builder.withCardInLibrary(2, "Forest") }
                val game = builder.build()
                if (pod) {
                    // Extra fixture players have normal player components; the probe stops before any next turn.
                    val reference = requireNotNull(game.state.getEntity(game.player2Id))
                    val extras = listOf(EntityId("player-3"), EntityId("player-4"))
                    extras.forEachIndexed { index, id ->
                        game.state = game.state.withEntity(id, reference.with(PlayerComponent("Reference-${index + 3}")).with(LifeTotalComponent(2)))
                    }
                    game.state = game.state.copy(turnOrder = game.state.turnOrder + extras)
                }
                val players = game.state.turnOrder
                val initialTurn = game.state.turnNumber
                val animar = game.findPermanent("Animar, Soul of Elements")!!
                game.state = game.state.updateEntity(animar) { it.with(CountersComponent(mapOf(CounterType.PLUS_ONE_PLUS_ONE to initialCounters))) }
                val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
                val records = mutableListOf<String>()
                val choices = mutableListOf<String>()
                val sink = AiInsightSink { _, insight -> choices += json.encodeToString(insight) }
                val pilots = players.associateWith { id ->
                    AIPlayer.create(cardRegistry, id, if (id == game.player1Id) profile else AiProfile.CURRENT, insightSink = sink)
                }
                val initial = json.encodeToString(game.state)
                val simulator = GameSimulator(cardRegistry)
                var error: String? = null
                var status = "RESOURCE_CAP"
                var index = 0
                val start = System.nanoTime()
                while (index < 300 && game.state.turnNumber <= 8 && (System.nanoTime() - start) / 1_000_000 < 60_000) {
                    val state = game.state
                    if (state.gameOver) { status = "GAME_OVER"; break }
                    if (pod && state.turnNumber != initialTurn) { status = "LEFT_COMBO_TURN"; break }
                    val pending = state.pendingDecision
                    val actor = pending?.playerId ?: requireNotNull(state.priorityPlayerId)
                    val legal = if (pending == null) simulator.getLegalActions(state, actor) else emptyList()
                    val action = if (pending != null) SubmitDecision(actor, pilots.getValue(actor).respondToDecision(state, pending))
                        else pilots.getValue(actor).chooseAction(state)
                    val cardId = (action as? CastSpell)?.cardId ?: (action as? ActivateAbility)?.sourceId
                    val result = game.execute(action)
                    index++
                    records += buildJsonObject {
                        put("index", index); put("turn", state.turnNumber); put("step", state.step.name)
                        put("actor", actor.value); put("priorityBefore", state.priorityPlayerId?.value)
                        put("sourceName", cardId?.let { state.getEntity(it)?.get<CardComponent>()?.name })
                        put("action", json.encodeToString<GameAction>(action))
                        put("animarCountersBefore", state.getEntity(animar)?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE))
                        put("availableBallistaCasts", JsonArray(legal.filter { it.affordable && (it.action as? CastSpell)?.cardId?.let { id -> state.getEntity(id)?.get<CardComponent>()?.name == "Walking Ballista" } == true }.map { offer -> buildJsonObject {
                            put("action", json.encodeToString<GameAction>(offer.action)); put("hasXCost", offer.hasXCost)
                            put("maxAffordableX", offer.maxAffordableX); put("minX", offer.minX)
                        } }))
                        put("events", JsonArray(result.events.map { JsonPrimitive(it.toString()) }))
                        put("life", JsonArray(players.map { JsonPrimitive(game.state.lifeTotal(it)) }))
                        put("error", result.error?.toString()); put("pendingBefore", pending?.toString())
                    }.toString()
                    if (result.error != null) { error = result.error.toString(); status = "ENGINE_REJECTION"; break }
                }
                if (game.state.gameOver) status = "GAME_OVER"
                val summary = buildJsonObject {
                    put("profile", profile.id); put("scenario", label); put("status", status); put("actions", index)
                    put("winner", game.state.winnerId?.value); put("gameOver", game.state.gameOver); put("error", error)
                    put("life", JsonArray(players.map { JsonPrimitive(game.state.lifeTotal(it)) }))
                    put("initialCounters", initialCounters); put("players", players.size)
                    put("forcedActionsAfterInitialization", 0); put("interpretation", "Constructed AI discovery, not a sampled Commander win; four-player case stops before any next-turn draw")
                }
                val output = Path.of(System.getenv("MANUAL_R4_OUTPUT") ?: "build/manual-r4").resolve("discovery").resolve(label)
                Files.createDirectories(output)
                Files.writeString(output.resolve("initial-state.json"), initial)
                Files.writeString(output.resolve("actions.jsonl"), records.joinToString("\n", postfix = "\n"))
                Files.writeString(output.resolve("decisions.jsonl"), choices.joinToString("\n", postfix = "\n"))
                Files.writeString(output.resolve("summary.json"), summary.toString())
                Files.writeString(output.resolve("final-state.json"), json.encodeToString(game.state))
                println("R4_BALLISTA_DISCOVERY=$summary")
                check(error == null) { "Actual AI action failed: $error" }
                check(index > 0)
                if (profile.id == candidate.id) {
                    check(game.state.gameOver && game.state.winnerId == game.player1Id) { "Actual candidate did not execute terminal damage: $summary" }
                    val rows = records.map { json.parseToJsonElement(it).jsonObject }
                    check(rows.none { it.getValue("action").jsonPrimitive.content.contains("DeclareAttackers") }) { "Combat cannot masquerade as Ballista payoff" }
                    if (pod && buildCounters) {
                        val statueCasts = rows.count { it["sourceName"]?.jsonPrimitive?.contentOrNull == "Ancestral Statue" && it.getValue("action").jsonPrimitive.content.contains("CastSpell") }
                        check(statueCasts == 4) { "Expected actual four-cast bounded counter build, observed $statueCasts" }
                    }
                }
            }
        }
    }
}
