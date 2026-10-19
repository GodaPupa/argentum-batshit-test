package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.modules.ManualAnimarDevelopmentAdvisorModule
import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.engine.core.*
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files

/** One bounded continuation of the archived failed game, never a new A/B batch. */
class ManualEncoreSmokeTest : FunSpec({
    test("bounded real gameplay continues after archived rejected Encore attack") {
        if (System.getenv("MANUAL_R3_ENCORE_SMOKE") != "1") return@test
        val output = EncoreArchive.output().resolve("smoke").also { Files.createDirectories(it) }
        val registry = EncoreArchive.registry()
        val processor = ActionProcessor(registry)
        var state = EncoreArchive.savedState()
        var accepted = 0
        var status = "CAP"
        var error: String? = null
        val start = System.nanoTime()
        val insights = Files.newBufferedWriter(output.resolve("decisions.jsonl"))
        val sink = AiInsightSink { _, insight -> insights.write(EncoreArchive.json.encodeToString(insight)); insights.newLine(); insights.flush() }
        // Repair must also cover the unchanged reference pilot that generated the rejected attack.
        // Preserve r2 only on its original seat; no combo policy is present in this lane.
        val pilots = state.turnOrder.associateWith { player ->
            val original = if (player.value == "manual-mirror-3") AiProfile.CURRENT.copy(
                advisorModules = listOf(ManualAnimarDevelopmentAdvisorModule)) else AiProfile.CURRENT
            AIPlayer.create(registry, player, original.copy(id = "manual-encore-defenders-r3",
                respectRequiredAttackDefenders = true), insightSink = sink)
        }
        Files.writeString(output.resolve("initial-state.json"), EncoreArchive.json.encodeToString(state))
        try {
            Files.newBufferedWriter(output.resolve("actions.jsonl")).use { log ->
                while (!state.gameOver && accepted < 5000 && state.turnNumber <= 130 &&
                    System.nanoTime() - start < 180_000_000_000L) {
                    val pending = state.pendingDecision
                    val actor = pending?.playerId ?: requireNotNull(state.priorityPlayerId)
                    val action = if (pending != null) SubmitDecision(actor, pilots.getValue(actor).respondToDecision(state, pending))
                        else pilots.getValue(actor).chooseAction(state)
                    val result = processor.process(state, action).result
                    log.write(buildJsonObject {
                        put("index", 3562 + accepted); put("turn", state.turnNumber)
                        put("actor", actor.value); put("action", EncoreArchive.json.encodeToString<GameAction>(action))
                        put("error", result.error); put("events", JsonArray(result.events.map { JsonPrimitive(it.toString()) }))
                    }.toString()); log.newLine(); log.flush()
                    if (result.error != null) { status = "ENGINE_REJECTION"; error = result.error; break }
                    state = result.state; accepted++
                }
                if (state.gameOver) status = "COMPLETE"
            }
        } catch (failure: Throwable) {
            status = "EXCEPTION"; error = failure.toString()
            Files.writeString(output.resolve("exception.txt"), failure.stackTraceToString())
        } finally {
            insights.close()
            Files.writeString(output.resolve("final-state.json"), EncoreArchive.json.encodeToString(state))
            Files.writeString(output.resolve("summary.json"), buildJsonObject {
                put("status", status); put("seed", 202610190801L); put("archivedAcceptedPrefix", 3561)
                put("continuationAccepted", accepted); put("turn", state.turnNumber); put("winner", state.winnerId?.value)
                put("error", error); put("life", buildJsonObject { state.turnOrder.forEach { put(it.value, state.lifeTotal(it)) } })
            }.toString())
        }
        check(error == null) { error!! }
        check(accepted >= 64 || state.gameOver) { "Insufficient smoke: $accepted actions" }
    }
})
