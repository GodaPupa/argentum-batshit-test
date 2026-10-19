package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
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

/**
 * Diagnostic-only alternative opening actions in two exact R5 states. This is NOT a live gameplay
 * trial, policy deployment, forced winner or forced opponent pass. Once the labelled first move
 * is taken, the four real AI pilots freely choose EVERY remaining legal action and decision.
 */
class ManualR6AnimarForkAnalysisTest : FunSpec({
    test("compare bounded AI continuations at R5 action 1813 and action 2368") {
        val archive = Path.of(requireNotNull(System.getenv("MANUAL_R6_AUDIT_ARCHIVE")))
        val out = Path.of(requireNotNull(System.getenv("MANUAL_R6_FORK_OUTPUT")))
        Files.createDirectories(out)
        val inputHash = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(archive)).joinToString("") { "%02x".format(it) }
        inputHash shouldBe "985f86cc30893b6243b429b033794d790653fd5bae6a42d2c4a5a1c00a49d7eb"
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val processor = ActionProcessor(registry)
        val simulator = GameSimulator(registry)
        val evaluator = AIPlayer.defaultEvaluator()
        val seat = EntityId("manual-mirror-0")
        val results = mutableListOf<JsonElement>()
        fun name(state: GameState, id: EntityId): String =
            state.getEntity(id)?.get<CardComponent>()?.name ?: id.value
        fun finite(score: Double): JsonElement =
            if (score.isFinite()) JsonPrimitive(score) else JsonPrimitive(score.toString())
        fun run(label: String, stateBefore: GameState, first: CastSpell, original: Boolean, index: Int) {
            val dir = out.resolve("$index-$label")
            Files.createDirectories(dir)
            val initialTurn = stateBefore.turnNumber
            val firstResult = processor.process(stateBefore, first).result
            check(firstResult.error == null) { "Initial controlled fork move rejected: ${firstResult.error}" }
            val pilot = stateBefore.turnOrder.associateWith {
                AIPlayer.create(registry, it, ManualTransmissionR5Candidate.profile)
            }
            val start = System.nanoTime()
            var state = firstResult.state
            var accepted = 1
            var rejected: String? = null
            var status = "RESOURCE_BOUND"
            val actionLog = Files.newBufferedWriter(dir.resolve("actions.jsonl"))
            try {
                actionLog.write(buildJsonObject {
                    put("index", 1); put("label", label); put("controlledFirstChoice", true)
                    put("originalR5Choice", original)
                    put("action", json.encodeToString<GameAction>(first))
                    put("events", JsonArray(firstResult.events.map { JsonPrimitive(it.toString()) }))
                    put("error", JsonNull)
                }.toString()); actionLog.newLine(); actionLog.flush()
                while (accepted < 160 && state.turnNumber <= initialTurn + 7 &&
                    (System.nanoTime() - start) / 1_000_000 < 80_000) {
                    if (state.gameOver) { status = "GAME_OVER"; break }
                    val pending = state.pendingDecision
                    val actor = pending?.playerId ?: requireNotNull(state.priorityPlayerId)
                    val next = if (pending != null) SubmitDecision(actor,
                        pilot.getValue(actor).respondToDecision(state, pending))
                    else pilot.getValue(actor).chooseAction(state)
                    val outcome = processor.process(state, next).result
                    accepted++
                    actionLog.write(buildJsonObject {
                        put("index", accepted); put("turn", state.turnNumber)
                        put("actor", actor.value); put("controlledFirstChoice", false)
                        put("action", json.encodeToString<GameAction>(next))
                        put("events", JsonArray(outcome.events.map { JsonPrimitive(it.toString()) }))
                        put("error", outcome.error)
                    }.toString()); actionLog.newLine(); actionLog.flush()
                    if (outcome.error != null) { rejected = outcome.error.toString(); status = "ENGINE_REJECTION"; break }
                    state = outcome.state
                }
                if (state.gameOver) status = "GAME_OVER"
            } finally {
                actionLog.close()
            }
            Files.writeString(dir.resolve("initial.json"), json.encodeToString(stateBefore))
            Files.writeString(dir.resolve("final.json"), json.encodeToString(state))
            val finalSummary = buildJsonObject {
                put("originatingR5ActionIndex", index); put("branch", label)
                put("counterfactual", !original); put("initialAction", json.encodeToString<GameAction>(first))
                put("continuationPolicy", "Four independent unmodified R5 AI controllers; no forced priority, pass, or later cast")
                put("originalControllerMemoryRestored", false)
                put("status", status); put("accepted", accepted); put("error", rejected)
                put("startTurn", initialTurn); put("endTurn", state.turnNumber)
                put("scoreBefore", finite(evaluator.evaluate(stateBefore, stateBefore.projectedState, seat)))
                put("scoreAfter", finite(evaluator.evaluate(state, state.projectedState, seat)))
                put("gameOver", state.gameOver); put("winner", state.winnerId?.value)
                put("activePlayers", JsonArray(state.activePlayers.map { JsonPrimitive(it.value) }))
                put("life", buildJsonObject { state.turnOrder.forEach { player ->
                    put(player.value, state.lifeTotal(player)) } })
                put("commanderInCommandZone", state.getZone(ZoneKey(seat, Zone.COMMAND)).size)
                put("commanderOnBattlefield", state.projectedState.getBattlefieldControlledBy(seat)
                    .any { name(state, it) == "Animar, Soul of Elements" })
            }
            Files.writeString(dir.resolve("summary.json"), finalSummary.toString())
            results += finalSummary
            Files.writeString(out.resolve("summary.json"), JsonArray(results).toString())
            println("R6_FORK_RESULT=$finalSummary")
            rejected shouldBe null
            check(accepted >= 30 || state.gameOver) {
                "Not enough real AI continuation actions to assess: $index/$label accepted=$accepted"
            }
        }

        ZipFile(archive.toFile()).use { z ->
            val expectedSource = z.getInputStream(
                requireNotNull(z.getEntry("r6-output/source-sha.txt"))
            ).bufferedReader().readText().trim()
            expectedSource shouldBe "30b3f3332b1306125cd4d655c79db66c0f209252"
            for ((index, originalName) in listOf(1813 to "Fyndhorn Elves", 2368 to "Vizier of the Menagerie")) {
                val state = json.decodeFromString<GameState>(z.getInputStream(
                    requireNotNull(z.getEntry("r6-output/state-before-$index.json"))
                ).bufferedReader().readText())
                state.turnNumber shouldBe (if (index == 1813) 40 else 52)
                val command = state.getZone(ZoneKey(seat, Zone.COMMAND)).single()
                val legal = simulator.getLegalActions(state, seat).filter { it.affordable && it.action is CastSpell }
                val original = legal.single { name(state, (it.action as CastSpell).cardId) == originalName }
                val alternative = legal.single { (it.action as CastSpell).cardId == command }
                // The controlled first choices are both engine-legal at the original priority.
                run("chosen-$index", state, original.action as CastSpell, true, index)
                run("hypothetical-animar-$index", state, alternative.action as CastSpell, false, index)
            }
        }
        results.size shouldBe 4
        AiProfile.CURRENT.advisorModules.isEmpty() shouldBe true
    }
})
