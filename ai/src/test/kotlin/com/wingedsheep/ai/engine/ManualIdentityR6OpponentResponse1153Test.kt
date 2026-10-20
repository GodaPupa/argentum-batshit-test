package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Bounded original-1153 fork with opt-in R5 AI taking actual stack priority.
 * NO new game, new seed, full future continuation, or change to production.
 * One R5 four-player policy sample cannot establish human-optimal counterplay.
 */
class ManualIdentityR6OpponentResponse1153Test : FunSpec({
    test("exact R4 state, two selected cast orders, real R5 opponent priority responses") {
        check(System.getenv("MANUAL_IDENTITY_R6_MODE") == "EXPLICIT_ORIGINAL_1153_R5_RESPONSES")
        val archive = Path.of(requireNotNull(System.getenv("MANUAL_IDENTITY_R6_R4_ARCHIVE")))
        val evidence = Path.of(requireNotNull(System.getenv("MANUAL_IDENTITY_R6_R5_ARCHIVE")))
        val output = Path.of(requireNotNull(System.getenv("MANUAL_IDENTITY_R6_OUTPUT")))
        Files.createDirectories(output)
        fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        check(sha(Files.readAllBytes(archive)) ==
            "d27a0b8bedc4377c1852cfde23f217e6b92c62a193bdf67972da7320be7ac492")
        check(sha(Files.readAllBytes(evidence)) ==
            "f223adc20c6a8713b745a3b786699e4876a036f1b5e253292b85d5b072604d51")
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        fun JsonObject.str(field: String) = this[field]?.jsonPrimitive?.contentOrNull
        fun JsonObject.intVal(field: String) = this[field]?.jsonPrimitive?.intOrNull
        val actor = EntityId("manual-mirror-3")
        val profile = ManualTransmissionR5Candidate.profile
        check(profile.id == "manual-transmission-r5-survival-opt-in")
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val processor = ActionProcessor(registry)
        val simulator = GameSimulator(registry)
        val evaluator = AIPlayer.defaultEvaluator()
        val state: GameState
        val original: CastSpell
        val animar: CastSpell
        ZipFile(archive.toFile()).use { z ->
            fun read(path: String): String = z.getInputStream(
                requireNotNull(z.getEntry("r4-receipt/" + path))
            ).bufferedReader().readText()
            check(read("source-sha.txt").trim() == "68a8e331c84ebf53575deab9951ec80ca75312a3")
            val report = json.parseToJsonElement(read("result.json")).jsonObject
            check(report.str("status") == "EXACT_ORIGINAL_STATE_REPLAY_VERIFIED")
            check(report.intVal("archivedSubmissionsReplayed") == 1513 &&
                report.intVal("matchingEngineEventRows") == 1513)
            check(report["prefix64FullSerializedStateEqual"]?.jsonPrimitive?.boolean == true)
            val window = report.getValue("observedWindows").jsonArray
                .map { it.jsonObject }.single { it.intVal("index") == 1153 }
            val text = read("exact-state-before-1153.json")
            check(sha(text.toByteArray()) == window.str("stateBeforeSha256"))
            state = json.decodeFromString<GameState>(text)
            check(state.turnNumber == 28 && state.step.name == "PRECOMBAT_MAIN")
            check(state.priorityPlayerId == actor && state.activePlayerId == actor)
            val decision = json.parseToJsonElement(read("decision-before-1153.json")).jsonObject
            val options = decision.getValue("options").jsonArray.map { it.jsonObject }
            val a = options.single { it["chosen"]?.jsonPrimitive?.booleanOrNull == true }
            val b = options.single { it.str("cardName") == "Animar, Soul of Elements" }
            check(a.str("cardName") == "Paradise Druid")
            original = json.decodeFromString<GameAction>(a.getValue("action").toString()) as CastSpell
            animar = json.decodeFromString<GameAction>(b.getValue("action").toString()) as CastSpell
            check(animar.cardId in state.getZone(ZoneKey(actor, Zone.COMMAND)))
        }
        ZipFile(evidence.toFile()).use { z ->
            fun read(path: String) = z.getInputStream(
                requireNotNull(z.getEntry("r5-receipt/" + path))
            ).bufferedReader().readText()
            check(read("source-sha.txt").trim() == "7f5d5abd97f6fcd8ecd2983b60f82f09ad329a30")
            val s = json.parseToJsonElement(read("summary.json")).jsonObject
            check(s.str("status") == "BOUNDED_ORIGINAL_STATE_MICRO_FORKS_COMPLETE")
            check(s.intVal("defectsProven") == 0 && s.intVal("newFullGameRuns") == 0)
            val w = json.parseToJsonElement(read("forks.json")).jsonArray
                .map { it.jsonObject }.single { it.intVal("actionIndex") == 1153 }
            for (key in listOf("chosenThenAnimar", "animarThenChosen")) {
                check(w.getValue(key).jsonObject["secondCastAffordableLegal"]?.jsonPrimitive?.boolean == true)
            }
        }
        check(processor.process(state, original).result.error == null)
        check(processor.process(state, animar).result.error == null)

        fun counterCount(s: GameState): Int? {
            if (animar.cardId !in s.getZone(ZoneKey(actor, Zone.BATTLEFIELD))) return null
            return s.getEntity(animar.cardId)?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
        }
        fun playPriorityUntilResolved(
            start: GameState, players: Map<EntityId, AIPlayer>, label: String
        ): Pair<GameState, JsonObject> {
            var current = start
            var accepted = 0
            var censored = false
            var opponentNonpass = 0
            val log = mutableListOf<JsonElement>()
            while (!current.gameOver && (current.stack.isNotEmpty() || current.pendingDecision != null)) {
                if (accepted >= 48) { censored = true; break }
                val decision = current.pendingDecision
                val who = decision?.playerId ?: requireNotNull(current.priorityPlayerId)
                val ai = players.getValue(who)
                val action: GameAction = if (decision == null) ai.chooseAction(current)
                    else SubmitDecision(who, ai.respondToDecision(current, decision))
                val sourceId = when (action) {
                    is CastSpell -> action.cardId
                    is ActivateAbility -> action.sourceId
                    else -> null
                }
                val sourceName = sourceId?.let { current.getEntity(it)?.get<CardComponent>()?.name }
                val result = processor.process(current, action).result
                check(result.error == null) {
                    "R5 opponent policy produced rejected action in " + label + ": " + result.error
                }
                if (who != actor && action !is PassPriority) opponentNonpass++
                log += buildJsonObject {
                    put("seat", who.value)
                    put("action", action::class.simpleName)
                    put("sourceName", sourceName)
                    put("opponent", who != actor)
                    put("stackBefore", current.stack.size)
                    put("stackAfter", result.state.stack.size)
                }
                current = result.state
                accepted++
            }
            return current to buildJsonObject {
                put("label", label)
                put("actionsAccepted", accepted)
                put("opponentNonpasses", opponentNonpass)
                put("responses", JsonArray(log))
                put("stackEmpty", current.stack.isEmpty())
                put("pendingCleared", current.pendingDecision == null)
                put("censored", censored)
                put("noForcedOpponentPasses", true)
            }
        }
        fun branch(first: CastSpell, second: CastSpell, label: String): JsonObject {
            // Fresh R5 strategy state for each independently executed branch.
            val players = state.turnOrder.associateWith {
                AIPlayer.create(registry, it, profile)
            }
            val result = processor.process(state, first).result
            check(result.error == null)
            val resolved = playPriorityUntilResolved(result.state, players, label + "-first")
            var current = resolved.first
            val firstSettled = current.stack.isEmpty() && current.pendingDecision == null &&
                resolved.second["censored"]?.jsonPrimitive?.boolean == false && !current.gameOver
            var secondLegal = false
            var secondSettled = false
            var secondTrace: JsonElement = JsonNull
            if (firstSettled && current.turnNumber == 28) {
                val offers = simulator.getLegalActions(current, actor).filter {
                    it.affordable && (it.action as? CastSpell)?.let { cast ->
                        cast.cardId == second.cardId &&
                            cast.useAlternativeCost == second.useAlternativeCost &&
                            cast.alternativeCostType == second.alternativeCostType
                    } == true
                }
                secondLegal = offers.isNotEmpty()
                if (secondLegal) {
                    val cast = offers.first().action as CastSpell
                    val applied = processor.process(current, cast).result
                    check(applied.error == null)
                    val resolved2 = playPriorityUntilResolved(applied.state, players, label + "-second")
                    current = resolved2.first
                    secondTrace = resolved2.second
                    secondSettled = current.stack.isEmpty() && current.pendingDecision == null &&
                        secondTrace.jsonObject["censored"]?.jsonPrimitive?.boolean == false && !current.gameOver
                }
            }
            val full = firstSettled && secondSettled
            return buildJsonObject {
                put("lane", label)
                put("firstCast", state.getEntity(first.cardId)?.get<CardComponent>()?.name)
                put("secondCast", state.getEntity(second.cardId)?.get<CardComponent>()?.name)
                put("firstSettled", firstSettled)
                put("secondAffordableLegalAfterOpponentResponses", secondLegal)
                put("secondSettled", secondSettled)
                put("firstResponseTranscript", resolved.second)
                put("secondResponseTranscript", secondTrace)
                put("finalTurn", current.turnNumber)
                put("finalAnimarCounters", counterCount(current)?.let(::JsonPrimitive) ?: JsonNull)
                put("finalStackSize", current.stack.size)
                put("finalLifeTotals", buildJsonObject {
                    state.turnOrder.forEach { put(it.value, current.lifeTotal(it)) }
                })
                put("finalBoardScoreIfBothSettled",
                    if (full) JsonPrimitive(evaluator.evaluate(current, current.projectedState, actor))
                    else JsonNull
                )
                put("onlyOriginalStateAndStackResponses", true)
                put("futureTurnsPlayed", 0)
                put("strategicDefectProven", false)
                put("R5AIIsNotOptimalHumanCounterplay", true)
            }
        }
        val druidFirst = branch(original, animar, "DRUID_THEN_ANIMAR")
        val animarFirst = branch(animar, original, "ANIMAR_THEN_DRUID")
        val report = buildJsonObject {
            put("status", "R6_1153_R5_POLICY_RESPONSE_MICRO_FORKS_COMPLETE")
            put("originalSource", "R22 control 202610191622 action 1153")
            put("r4SourceRun", 39454771346L)
            put("r5SourceRun", 39456950083L)
            put("profile", profile.id)
            put("druidFirst", druidFirst)
            put("animarFirst", animarFirst)
            put("fullGamesInitialized", 0)
            put("newSeeds", 0)
            put("productionChanges", 0)
            put("hardwareChanges", 0)
            put("gearChanges", 0)
            put("defectsProven", 0)
            put("automaticPromotion", false)
            put("conclusion", "NO_PROVEN_DEFECT")
        }
        Files.writeString(output.resolve("result.json"), report.toString())
        println("IDENTITY_R6_REAL_PRIORITY_RESULT=" + report)
        println("IDENTITY_R6_ORIGINAL_1153_PASS source_state=1 new_games=0 promotions=0")
    }
})
