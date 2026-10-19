package com.wingedsheep.gym

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

/** Read-only forced trace replay on the original engine/policy; never a new gameplay sample. */
class MonoRedPolicyReplayAuditTest : FunSpec({
    test("audit original Mono-Red policy against recorded candidate states").config(timeout=55.minutes) {
        val json = Json { serializersModule=engineSerializersModule; encodeDefaults=true; allowStructuredMapKeys=true }
        val registry=CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val files=Files.walk(Path.of(System.getenv("MTG_AUDIT_ROOT"))).use { stream ->
            stream.filter { it.fileName.toString().matches(Regex("game-00[12]\\.jsonl")) }.sorted().toList()
        }
        check(files.size==20) { "Expected exactly 20 preserved candidate traces, found ${files.size}" }
        val reports=mutableListOf<JsonElement>()
        for (file in files) {
            val trace=Files.readAllLines(file).map { json.parseToJsonElement(it).jsonObject }
            val meta=trace.first(); val seed=meta.getValue("seed").jsonPrimitive.content.toLong()
            val pest=meta.getValue("pestSeat").jsonPrimitive.int; val red=1-pest
            val env=GameEnvironment.create(registry)
            env.reset(GameConfig(players=(0..1).map { seat ->
                if (seat==pest) PlayerConfig("Pest Control v1.0",PestControlPreboardDecks.pestMain(),startingLife=20)
                else PlayerConfig("SoterX Mono-Red Madness",PestControlPreboardDecks.monoRedMain(),startingLife=20)
            },skipMulligans=false,useHandSmoother=false,startingPlayerIndex=0,seed=seed))
            var ai: AIPlayer?=null
            var firstMismatch: JsonElement=JsonNull
            var compared=0; var actions=0
            for (record in trace) {
                val n=record["sequence"]?.jsonPrimitive?.int ?: continue
                if (record["recordType"]?.jsonPrimitive?.content=="ACTION_INTENT") {
                    val recorded=json.decodeFromJsonElement(GameAction.serializer(),record.getValue("selectedAction"))
                    val mulligan=recorded is KeepHand || recorded is TakeMulligan || recorded is BottomCards
                    if (!mulligan && ai==null) ai=AIPlayer.create(registry,env.playerIds[red],AiProfile.PRODUCTION_CANDIDATE_EXPIRING)
                    if (!mulligan && recorded.playerId==env.playerIds[red] && firstMismatch==JsonNull) {
                        val agent=requireNotNull(ai);val pending=env.state.pendingDecision
                        val chosen=if (pending!=null) SubmitDecision(recorded.playerId,agent.respondToDecision(env.state,pending)) else agent.chooseAction(env.state)
                        compared++
                        if (chosen!=recorded) firstMismatch=buildJsonObject {
                            put("sequence",n);put("turn",env.turnNumber);put("step",env.state.step.name)
                            put("recordedCandidate",record.getValue("selectedAction"))
                            put("originalPolicyChoice",json.encodeToJsonElement(GameAction.serializer(),chosen))
                        }
                    }
                    check(env.stepExactlyOne(recorded) is ExactlyOneSubmissionResult.Applied)
                    actions++
                } else if (record["recordType"]?.jsonPrimitive?.content=="ACTION_RESULT") {
                    check(JsonArray(env.lastStepEvents.map { json.encodeToJsonElement(GameEvent.serializer(),it) })==record["emittedEvents"]) { "Engine event mismatch at $seed / $pest / $n" }
                    check(JsonArray(env.playerIds.map { JsonPrimitive(env.state.lifeTotal(it)) })==record["lifeAfter"])
                    check(env.turnNumber==record.getValue("turnAfter").jsonPrimitive.int)
                }
            }
            check(env.state.gameOver)
            reports.add(buildJsonObject {
                put("seed",seed.toString());put("pestSeat",pest);put("redSeat",red)
                put("forcedActionsVerified",actions);put("originalOpponentDecisionsCompared",compared)
                put("firstOpponentPolicyMismatch",firstMismatch)
                put("opponentChoicesIdentical",firstMismatch==JsonNull)
            })
            Files.writeString(Path.of(System.getenv("MTG_AUDIT_REPORT")),buildJsonObject {
                put("diagnosticOnly",true);put("newGamesPlayed",0)
                put("engineCommit","8e9c0877ea51bc0a112891066b2465d5c124ab09")
                put("candidateRun",39191179250L);put("games",JsonArray(reports))
            }.toString())
        }
    }
})
