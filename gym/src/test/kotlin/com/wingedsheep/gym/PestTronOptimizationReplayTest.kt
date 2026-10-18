package com.wingedsheep.gym

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.PestMonsterTronPolicy
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/** Decision reconstruction only; never entered into the gameplay win ledger. */
class PestTronOptimizationReplayTest : FunSpec({
    test("reconstruct the recorded empty combat and inspect the timing choice") {
        val json = Json { serializersModule = engineSerializersModule; encodeDefaults = true; allowStructuredMapKeys = true }
        val trace = Files.readAllLines(Path.of(System.getenv("MTG_REPLAY_TRACE"))).map { json.parseToJsonElement(it).jsonObject }
        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val env = GameEnvironment.create(registry)
        env.reset(GameConfig(players = listOf(
            PlayerConfig("mehanske Monster Tron", PestControlTierOneMonsterTronReadiness.mainDeck(), startingLife = 20),
            PlayerConfig("Pest Control v1.0", PestControlPreboardDecks.pestMain(), startingLife = 20),
        ), skipMulligans = false, useHandSmoother = false, startingPlayerIndex = 0, seed = 2026101806L))
        for (record in trace) {
            val n = record["sequence"]?.jsonPrimitive?.int ?: continue
            if (n >= 280) break
            if (record["recordType"]?.jsonPrimitive?.content == "ACTION_INTENT") {
                val action = json.decodeFromJsonElement(GameAction.serializer(), record.getValue("selectedAction"))
                check(env.stepExactlyOne(action) is ExactlyOneSubmissionResult.Applied)
            } else if (record["recordType"]?.jsonPrimitive?.content == "ACTION_RESULT") {
                check(JsonArray(env.lastStepEvents.map { json.encodeToJsonElement(GameEvent.serializer(), it) }) == record["emittedEvents"]) { "Prefix event mismatch at $n" }
                check(env.turnNumber == record.getValue("turnAfter").jsonPrimitive.int)
                check(JsonArray(env.playerIds.map { JsonPrimitive(env.state.lifeTotal(it)) }) == record["lifeAfter"])
            }
        }
        val log = mutableListOf<JsonObject>()
        val agents = env.playerIds.mapIndexed { i, id -> AIPlayer.create(registry, id,
            if (i == 1) AiProfile.PRODUCTION_CANDIDATE_EXPIRING else PestMonsterTronPolicy.profile) }
        repeat(40) { index ->
            if (env.isTerminal) return@repeat
            val player = env.state.pendingDecision?.playerId ?: env.state.priorityPlayerId ?: error("No priority")
            val agent = agents[env.playerIds.indexOf(player)]
            val action = env.state.pendingDecision?.let { SubmitDecision(player, agent.respondToDecision(env.state, it)) }
                ?: agent.chooseAction(env.state)
            if (index == 0) {
                if (System.getenv("MTG_REPLAY_EXPECT") == "candidate") check(action is PassPriority) { "Candidate did not defer: $action" }
                else check(action is CastSpell) { "Control did not reproduce Weather: $action" }
            }
            val before = env.state
            check(env.stepExactlyOne(action) is ExactlyOneSubmissionResult.Applied)
            log += buildJsonObject {
                put("offset", index); put("turn", before.turnNumber); put("step", before.step.name)
                put("action", json.encodeToJsonElement(GameAction.serializer(), action))
                put("events", JsonArray(env.lastStepEvents.map { json.encodeToJsonElement(GameEvent.serializer(), it) }))
            }
        }
        Files.writeString(Path.of(System.getenv("MTG_REPLAY_REPORT")), buildJsonObject {
            put("diagnosticOnly", true); put("prefixActionsVerified", 279)
            put("variant", System.getenv("MTG_REPLAY_EXPECT")); put("decisions", JsonArray(log))
        }.toString())
    }
})
