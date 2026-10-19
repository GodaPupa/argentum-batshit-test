package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.CounterType
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

/**
 * Read-only, source-bound decision replay and two labelled tactical continuations.
 * No policy changes, invented outcomes, forced opponent passes, or hidden-information use.
 * Both arms create fresh R5 controllers; original controller memory cannot be restored.
 * Holding Animar-targeted shots on the original turn is explicitly an intervention.
 */
class ManualR7BallistaCounterForkTest : FunSpec({
    test("replay nonlethal Animar shots versus bounded hold and preserve real lethal control") {
        val out = Path.of(requireNotNull(System.getenv("MANUAL_R7_BALLISTA_OUTPUT")))
        Files.createDirectories(out)
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val processor = ActionProcessor(registry)
        val simulator = GameSimulator(registry)
        val evaluator = AIPlayer.defaultEvaluator()
        val animar = EntityId("e0")
        val defender = EntityId("manual-mirror-0")
        val summary = mutableListOf<JsonElement>()
        val bindings = mutableListOf<JsonElement>()
        data class Checkpoint(val state: GameState, val rows: List<JsonObject>, val original: ActivateAbility, val index: Int)

        fun finite(score: Double): JsonElement =
            if (score.isFinite()) JsonPrimitive(score) else JsonPrimitive(score.toString())
        fun counters(state: GameState, entity: EntityId): Int =
            state.getEntity(entity)?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
        fun name(state: GameState, entity: EntityId): String =
            state.getEntity(entity)?.get<CardComponent>()?.name ?: entity.value

        fun bindAction(state: GameState, original: GameAction, index: Int): GameAction {
            if (original !is ActivateAbility) return original
            val offers = simulator.getLegalActions(state, original.playerId)
                .filter { it.affordable && (original.targets.isEmpty() || it.requiresTargets) }
                .mapNotNull { it.action as? ActivateAbility }
                .filter { it.sourceId == original.sourceId }.map { it.abilityId }.distinct()
            if (original.abilityId in offers) return original
            check(offers.size == 1) {
                "Archived ability cannot be uniquely rebound at " + index + ": " + offers
            }
            bindings += buildJsonObject {
                put("index", index)
                put("source", original.sourceId.value)
                put("archivedAbility", original.abilityId.value)
                put("runtimeAbility", offers.single().value)
            }
            Files.writeString(out.resolve("ability-bindings.json"), JsonArray(bindings).toString())
            return original.copy(abilityId = offers.single())
        }

        fun checkpoint(seed: Long, sha: String, beforeIndex: Int): Checkpoint {
            val input = Path.of(requireNotNull(System.getenv(
                if (seed == 202610191609L) "MANUAL_R7_ARTIFACT_1609" else "MANUAL_R7_ARTIFACT_1610"
            )))
            val actualHash = MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(input)).joinToString("") { "%02x".format(it) }
            actualHash shouldBe sha
            ZipFile(input.toFile()).use { zip ->
                val prefix = "r7-output/seed-" + seed + "/"
                fun entry(file: String): String = zip.getInputStream(requireNotNull(
                    zip.getEntry(prefix + file)
                ) { "Missing accepted R7 original: " + prefix + file }).bufferedReader().readText()
                val originalSummary = json.parseToJsonElement(entry("summary.json")).jsonObject
                originalSummary.getValue("seed").jsonPrimitive.long shouldBe seed
                originalSummary.getValue("status").jsonPrimitive.content shouldBe "COMPLETE"
                val rows = entry("actions.jsonl").lineSequence().filter(String::isNotBlank)
                    .map { json.parseToJsonElement(it).jsonObject }
                    .filter { it["record"]?.jsonPrimitive?.content == "submit" }.toList()
                var state = json.decodeFromString<GameState>(entry("initial-state.json"))
                var replayed = 0
                for (row in rows) {
                    val index = row.getValue("index").jsonPrimitive.int
                    val archived = json.decodeFromString<GameAction>(row.getValue("action").jsonPrimitive.content)
                    if (index == beforeIndex) {
                        val shot = archived as? ActivateAbility
                            ?: error("Expected archived Ballista activation at " + index)
                        val dest = out.resolve("seed-" + seed + "-before-" + beforeIndex + ".json")
                        Files.writeString(dest, json.encodeToString(state))
                        Files.writeString(out.resolve("replay-" + seed + "-progress.json"), buildJsonObject {
                            put("verifiedOriginalActions", replayed)
                            put("capturedBeforeAction", beforeIndex)
                            put("gameTurn", state.turnNumber)
                            put("actor", shot.playerId.value)
                            put("ballista", name(state, shot.sourceId))
                            put("ballistaCounters", counters(state, shot.sourceId))
                            put("stackSize", state.stack.size)
                            put("originalAction", json.encodeToString<GameAction>(shot))
                        }.toString())
                        return Checkpoint(state, rows, shot, beforeIndex)
                    }
                    val bound = bindAction(state, archived, index)
                    val processed = processor.process(state, bound).result
                    check(processed.error == null) {
                        "Original R7 action " + index + " rejected: " + processed.error
                    }
                    state = processed.state
                    replayed++
                    if (replayed % 200 == 0) Files.writeString(
                        out.resolve("replay-" + seed + "-progress.json"),
                        buildJsonObject { put("verifiedOriginalActions", replayed) }.toString()
                    )
                }
            }
            error("The original archived R7 action was not found for " + seed + "/" + beforeIndex)
        }

        val lead = checkpoint(
            202610191609L,
            "1367da258c0ebb9f216101adf3440cf748e251be64c7789fdaff80a25ab5c8eb",
            2659
        )
        val shooter = lead.original.playerId
        val ballista = lead.original.sourceId
        shooter shouldBe EntityId("manual-mirror-2")
        lead.state.turnNumber shouldBe 46
        name(lead.state, ballista) shouldBe "Walking Ballista"
        counters(lead.state, ballista) shouldBe 8
        check(lead.state.stack.isNotEmpty()) { "Expected seven queued damage abilities before action 2659" }
        val legal = simulator.getLegalActions(lead.state, shooter)
        check(legal.any { it.action is PassPriority }) { "Hold-first action is not legal" }
        check(legal.any {
            it.affordable && (it.action as? ActivateAbility)?.sourceId == ballista
        }) { "Original Ballista activation is not affordable/available" }
        val beforeEval = evaluator.evaluate(lead.state, lead.state.projectedState, shooter)
        val baselineSource = bindAction(lead.state, lead.original, 2659) as ActivateAbility

        fun runArm(label: String, hold: Boolean): JsonObject {
            val dir = out.resolve("seed-1609-" + label)
            Files.createDirectories(dir)
            val pilots = lead.state.turnOrder.associateWith {
                AIPlayer.create(registry, it, ManualTransmissionR5Candidate.profile)
            }
            val writer = Files.newBufferedWriter(dir.resolve("actions.jsonl"))
            var state = lead.state
            var accepted = 0
            var alteredChoices = 0
            var ballistaActivations = 0
            var animarDamageEvents = 0
            var error: String? = null
            var status = "RESOURCE_BOUND"
            val began = System.nanoTime()
            try {
                while (accepted < 180 && state.turnNumber <= 50 &&
                    (System.nanoTime() - began) / 1_000_000 < 80_000L) {
                    if (state.gameOver) { status = "GAME_OVER"; break }
                    val pending = state.pendingDecision
                    val actor = pending?.playerId ?: requireNotNull(state.priorityPlayerId)
                    val requested: GameAction = if (accepted == 0) baselineSource
                    else if (pending != null) SubmitDecision(actor,
                        pilots.getValue(actor).respondToDecision(state, pending))
                    else pilots.getValue(actor).chooseAction(state)
                    val selected: GameAction =
                        if (accepted == 0 && hold) {
                            alteredChoices++; PassPriority(shooter)
                        } else if (hold && state.turnNumber == 46 && pending == null &&
                            actor == shooter && requested is ActivateAbility &&
                            requested.sourceId == ballista &&
                            requested.targets.any {
                                it is ChosenTarget.Permanent && it.entityId == animar
                            }) {
                            // This is a *labelled*, controlled tactical hold, not an AI policy.
                            // Only our shooter is altered; all other players retain actual R5 pilots.
                            alteredChoices++; PassPriority(shooter)
                        } else requested
                    val outcome = processor.process(state, selected).result
                    accepted++
                    if (selected is ActivateAbility && selected.sourceId == ballista) ballistaActivations++
                    animarDamageEvents += outcome.events.count { event ->
                        event is DamageDealtEvent && event.sourceName == "Walking Ballista" &&
                            event.targetId == animar
                    }
                    writer.write(buildJsonObject {
                        put("index", accepted)
                        put("turn", state.turnNumber)
                        put("actor", actor.value)
                        put("controlledOverride", selected != requested)
                        put("requested", json.encodeToString<GameAction>(requested))
                        put("submitted", json.encodeToString<GameAction>(selected))
                        put("events", JsonArray(outcome.events.map { JsonPrimitive(it.toString()) }))
                        put("error", outcome.error?.toString())
                    }.toString()); writer.newLine(); writer.flush()
                    if (outcome.error != null) {
                        error = outcome.error.toString(); status = "ENGINE_REJECTION"; break
                    }
                    state = outcome.state
                }
                if (state.gameOver) status = "GAME_OVER"
            } finally {
                writer.close()
                Files.writeString(dir.resolve("final-state.json"), json.encodeToString(state))
            }
            val result = buildJsonObject {
                put("label", label)
                put("controlledCounterfactual", hold)
                put("replayedFromAction", 2659)
                put("originalR7ControllerMemoryRestored", false)
                put("status", status)
                put("accepted", accepted)
                put("error", error)
                put("controlledHoldChoices", alteredChoices)
                put("actualBallistaActivationsInFork", ballistaActivations)
                put("ballistaDamageEventsAgainstAnimar", animarDamageEvents)
                put("initialBallistaCounters", 8)
                put("remainingBallistaCounters", counters(state, ballista))
                put("ballistaOnBattlefield", ballista in state.getZone(ZoneKey(shooter, Zone.BATTLEFIELD)))
                put("opposingAnimarOnBattlefield", animar in state.getZone(ZoneKey(defender, Zone.BATTLEFIELD)))
                put("endTurn", state.turnNumber)
                put("gameOver", state.gameOver)
                put("winner", state.winnerId?.value)
                put("scoreBefore", finite(beforeEval))
                put("scoreAfter", finite(evaluator.evaluate(state, state.projectedState, shooter)))
                put("life", buildJsonObject {
                    state.turnOrder.forEach { id -> put(id.value, state.lifeTotal(id)) }
                })
                put("interpretation", "Fresh R5 controllers; initial action controlled, hold arm additionally overrides own Animar-targeted shots on turn 46 only; no opponent pass forced; not a win-rate comparison")
            }
            Files.writeString(dir.resolve("summary.json"), result.toString())
            summary += result
            Files.writeString(out.resolve("summary.json"), JsonArray(summary).toString())
            println("R7_BALLISTA_FORK=" + result)
            check(error == null) { "Fork engine rejection: " + result }
            check(accepted >= 40 || state.gameOver) { "Fork too short for meaningful observation: " + result }
            return result
        }

        runArm("original-first-shot-unmodified-AI", false)
        runArm("pass-and-retain-animar-target-counters", true)

        // Mandatory negative control: actual nine-shot kill was legal and resolved.
        val negative = checkpoint(
            202610191610L,
            "111687fab8c483c227ec22478822aa4824ccb736d031b716b9c4855f0fa58ba9",
            2764
        )
        negative.state.turnNumber shouldBe 53
        name(negative.state, negative.original.sourceId) shouldBe "Walking Ballista"
        counters(negative.state, negative.original.sourceId) shouldBe 9
        var negativeState = negative.state
        var negativeShots = 0
        val controlEvents = mutableListOf<JsonElement>()
        for (row in negative.rows) {
            val index = row.getValue("index").jsonPrimitive.int
            if (index < 2764) continue
            if (index > 2800) break
            val archived = json.decodeFromString<GameAction>(row.getValue("action").jsonPrimitive.content)
            val action = bindAction(negativeState, archived, index)
            if (action is ActivateAbility && action.sourceId == negative.original.sourceId &&
                action.targets.any { it is ChosenTarget.Permanent && it.entityId == animar }) negativeShots++
            val processed = processor.process(negativeState, action).result
            check(processed.error == null) { "Negative-control action " + index + ": " + processed.error }
            if (index == 2799 || index == 2800) {
                controlEvents += buildJsonObject {
                    put("index", index)
                    put("events", JsonArray(processed.events.map { JsonPrimitive(it.toString()) }))
                }
            }
            negativeState = processed.state
        }
        val animarRemoved = animar !in negativeState.getZone(ZoneKey(defender, Zone.BATTLEFIELD)) &&
            animar in negativeState.getZone(ZoneKey(defender, Zone.COMMAND))
        val control = buildJsonObject {
            put("seed", 202610191610L)
            put("originalAction", 2764)
            put("originalShotsAtAnimar", negativeShots)
            put("initialRemainingBallistaCounters", 9)
            put("animarRemovedToCommandZone", animarRemoved)
            put("realEngineReplayThroughAction", 2800)
            put("events", JsonArray(controlEvents))
            put("disposition", "Legitimate terminal creature-removal line; do not suppress by blanket penalty")
        }
        Files.writeString(out.resolve("negative-control.json"), control.toString())
        println("R7_BALLISTA_NEGATIVE_CONTROL=" + control)
        negativeShots shouldBe 9
        animarRemoved shouldBe true
        summary.size shouldBe 2
        AiProfile.CURRENT.advisorModules.isEmpty() shouldBe true
    }
})
