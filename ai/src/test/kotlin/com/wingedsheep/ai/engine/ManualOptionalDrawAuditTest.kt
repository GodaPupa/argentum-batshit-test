package com.wingedsheep.ai.engine

import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Exact archived state plus clearly labelled counterfactual legal choices, not sampled pod results. */
class ManualOptionalDrawAuditTest : FunSpec({
    test("replay exact Soul choice and compare bounded actual AI continuations") {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val out = Path.of(System.getenv("MANUAL_R5_DRAW_OUTPUT") ?: root.resolve("ai/build/manual-r5-draw").toString())
        Files.createDirectories(out)
        val archive = root.resolve("gameplay/manual-r5/input/r4-integration-39270737788.zip")
        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive)).joinToString("") { "%02x".format(it) } shouldBe
            "bd541f2a72aa7ec229c115a83c4d59ef44d35131ee11322ac52a10f9e9d3c6d0"
        val registry = CardRegistry().apply { MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) } }
        val processor = ActionProcessor(registry)
        val sim = GameSimulator(registry)
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val bindings = mutableListOf<JsonElement>()
        var checkpoint: GameState? = null
        var expectedWinner: EntityId? = null
        var lastReplayState: GameState? = null
        var lastReplayIndex = 0
        try {
        ZipFile(archive.toFile()).use { zip ->
            val prefix = "r4-output/smoke/seed-202610191401/"
            fun read(name: String) = zip.getInputStream(zip.getEntry(prefix + name)).bufferedReader().readText()
            var state = json.decodeFromString<GameState>(read("initial-state.json"))
            for (line in read("actions.jsonl").lineSequence().filter(String::isNotBlank)) {
                val row = json.parseToJsonElement(line).jsonObject
                if (row["record"]?.jsonPrimitive?.content != "submit") continue
                val index = row.getValue("index").jsonPrimitive.int
                lastReplayState = state
                lastReplayIndex = index
                val archived = json.decodeFromString<GameAction>(row.getValue("action").jsonPrimitive.content)
                val action = if (archived is ActivateAbility) {
                    val ids = sim.getLegalActions(state, archived.playerId).filter { it.affordable && it.requiresTargets == archived.targets.isNotEmpty() }.mapNotNull { it.action as? ActivateAbility }
                        .filter { it.sourceId == archived.sourceId }.map { it.abilityId }.distinct()
                    if (archived.abilityId in ids) archived else {
                        check(ids.size == 1) { "Ambiguous ability at $index: $ids" }
                        bindings += buildJsonObject { put("index", index); put("source", archived.sourceId.value); put("old", archived.abilityId.value); put("runtime", ids.single().value) }
                        archived.copy(abilityId = ids.single())
                    }
                } else archived
                if (index == 1863) {
                    checkpoint = state
                    state.turnNumber shouldBe 58
                    state.getZone(ZoneKey(action.playerId, Zone.LIBRARY)).size shouldBe 2
                    val pending = state.pendingDecision as YesNoDecision
                    pending.context.sourceName shouldBe "Soul of the Harvest"
                    val actual = AIPlayer.create(registry, action.playerId, ManualTransmissionR4Candidate.profile).respondToDecision(state, pending)
                    actual shouldBe YesNoResponse(pending.id, true)
                    Files.writeString(out.resolve("before-1863.json"), json.encodeToString(state))
                    val yes = sim.simulateDecision(state, YesNoResponse(pending.id, true)) as SimulationResult.Terminal
                    val no = sim.simulateDecision(state, YesNoResponse(pending.id, false)) as SimulationResult.Terminal
                    yes.state.getZone(ZoneKey(action.playerId, Zone.LIBRARY)).size shouldBe 1
                    no.state.getZone(ZoneKey(action.playerId, Zone.LIBRARY)).size shouldBe 2
                    val evaluator = AIPlayer.defaultEvaluator()
                    val evidence = buildJsonObject {
                        put("originalIndex", index); put("globalTurn", state.turnNumber); put("libraryBefore", 2)
                        put("yesLibrary", 1); put("noLibrary", 2); put("actualChoice", json.encodeToString(actual))
                        put("yesScore", evaluator.evaluate(yes.state, yes.state.projectedState, action.playerId))
                        put("noScore", evaluator.evaluate(no.state, no.state.projectedState, action.playerId))
                        put("yesEvents", JsonArray(yes.events.map { JsonPrimitive(it.toString()) }))
                        put("noEvents", JsonArray(no.events.map { JsonPrimitive(it.toString()) }))
                        put("interpretation", "Optional draw preserves one card; it does not immediately empty the library. Analysis may inspect outcomes; no production policy peeks at library identities.")
                    }
                    Files.writeString(out.resolve("decision-1863.json"), evidence.toString())
                    println("R5_OPTIONAL_DRAW_DECISION=$evidence")
                }
                val result = processor.process(state, action).result
                check(result.error == null) { "Archive rejected at $index: ${result.error}" }
                state = result.state
            }
            state.gameOver shouldBe true
            state.winnerId shouldBe EntityId("manual-mirror-3")
            state.turnNumber shouldBe 62
            expectedWinner = state.winnerId
            Files.writeString(out.resolve("replay-final.json"), json.encodeToString(state))
        }
        } finally {
            Files.writeString(out.resolve("ability-bindings.json"), JsonArray(bindings).toString())
            lastReplayState?.let { Files.writeString(out.resolve("last-replay-before.json"), json.encodeToString(it)) }
            Files.writeString(out.resolve("last-replay-index.json"), buildJsonObject { put("index", lastReplayIndex) }.toString())
        }
        val initial = requireNotNull(checkpoint)
        val decision = initial.pendingDecision as YesNoDecision
        val actor = decision.playerId
        for (choice in listOf(true, false)) {
            val output = out.resolve(if (choice) "accept" else "decline")
            Files.createDirectories(output)
            var state = initial
            val records = mutableListOf<String>()
            val insights = mutableListOf<String>()
            val pilots = initial.turnOrder.associateWith { id -> AIPlayer.create(registry, id, ManualTransmissionR4Candidate.profile,
                insightSink = AiInsightSink { _, insight -> insights += json.encodeToString(insight) }) }
            var actions = 0
            var error: String? = null
            var status = "RESOURCE_CAP"
            val start = System.nanoTime()
            while (actions < 500 && state.turnNumber <= 72 && (System.nanoTime() - start) / 1_000_000 < 120_000) {
                if (state.gameOver) { status = "GAME_OVER"; break }
                val pending = state.pendingDecision
                val player = pending?.playerId ?: requireNotNull(state.priorityPlayerId)
                val action = if (actions == 0) SubmitDecision(actor, YesNoResponse(decision.id, choice))
                    else if (pending != null) SubmitDecision(player, pilots.getValue(player).respondToDecision(state, pending))
                    else pilots.getValue(player).chooseAction(state)
                val result = processor.process(state, action).result
                actions++
                records += buildJsonObject {
                    put("index", actions); put("globalTurn", state.turnNumber); put("actor", player.value)
                    put("action", json.encodeToString<GameAction>(action)); put("intervention", actions == 1)
                    put("events", JsonArray(result.events.map { JsonPrimitive(it.toString()) }))
                    put("error", result.error); put("library", result.state.getZone(ZoneKey(actor, Zone.LIBRARY)).size)
                    put("life", JsonArray(initial.turnOrder.map { JsonPrimitive(result.state.lifeTotal(it)) }))
                }.toString()
                state = result.state
                if (result.error != null) { error = result.error; status = "ENGINE_REJECTION"; break }
            }
            if (state.gameOver) status = "GAME_OVER"
            val summary = buildJsonObject {
                put("branch", if (choice) "accept" else "decline"); put("status", status); put("actions", actions)
                put("winner", state.winnerId?.value); put("globalTurn", state.turnNumber); put("error", error)
                put("archiveWinner", expectedWinner?.value); put("library", state.getZone(ZoneKey(actor, Zone.LIBRARY)).size)
                put("forcedDecisionInterventions", 1); put("subsequentActions", "Actual R4 composed AI pilots for every player")
                put("controllerHistory", "Both forks create fresh controllers at the same archived state; prior Strategist memory is not restored")
                put("interpretation", "Counterfactual branch probe, not a new sampled pod or evidence that hidden library identities were known to the pilot")
            }
            Files.writeString(output.resolve("actions.jsonl"), records.joinToString("\n", postfix = "\n"))
            Files.writeString(output.resolve("decisions.jsonl"), insights.joinToString("\n", postfix = "\n"))
            Files.writeString(output.resolve("summary.json"), summary.toString())
            Files.writeString(output.resolve("final-state.json"), json.encodeToString(state))
            println("R5_OPTIONAL_DRAW_FORK=$summary")
            check(error == null)
        }
        check(AiProfile.CURRENT.advisorModules.isEmpty())
    }
})
