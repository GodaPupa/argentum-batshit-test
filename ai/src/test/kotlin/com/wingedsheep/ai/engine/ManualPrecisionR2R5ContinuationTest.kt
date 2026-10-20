package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.evaluation.EvalWeights
import com.wingedsheep.ai.engine.knowledge.IntentCatalog
import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Diagnostic ONLY: replay neither a new complete game nor unobserved opponents.
 * Consume exact engine-authorized states emitted by R2 primary real-engine microfork.
 */
class ManualPrecisionR2R5ContinuationTest : FunSpec({
    test("score identical R2 YES and NO leaves and inspect the actual R5 next action") {
        val root = Path.of(requireNotNull(System.getenv("MANUAL_R2_PRIMARY_OUTPUT")))
        val output = Path.of(requireNotNull(System.getenv("MANUAL_R2_CONTINUATION_OUTPUT")))
        Files.createDirectories(output)
        val dir = root.resolve("r2-shock-output")
        fun bytes(name: String) = Files.readAllBytes(dir.resolve(name))
        fun sha(b: ByteArray) = MessageDigest.getInstance("SHA-256")
            .digest(b).joinToString("") { "%02x".format(it) }
        check(sha(bytes("state-before-r0.json")) == "0df555d80bc0a4eab24577dd4cb6de0424564643597d2bd64379c9bde23014d0")
        check(sha(bytes("state-after-yes.json")) == "7e8373fd825b1f50c09158d25b45621152548ff8743c68ca604df13332366031")
        check(sha(bytes("state-after-no.json")) == "552cd7d26bf9965077d5ea8b0e502301d07fd171bbd99bbc745fe9ce0b25afd5")
        val originalSummary = Json.parseToJsonElement(Files.readString(dir.resolve("r2-primary-microfork.json"))).jsonObject
        check(originalSummary["originalActionsReplayedExactly"]?.jsonPrimitive?.int == 13)
        check(originalSummary["yesArborElfEngineCastAccepted"]?.jsonPrimitive?.boolean == true)
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val yes = json.decodeFromString<GameState>(bytes("state-after-yes.json").toString(Charsets.UTF_8))
        val no = json.decodeFromString<GameState>(bytes("state-after-no.json").toString(Charsets.UTF_8))
        val actor = EntityId("manual-mirror-0")
        val elf = EntityId("e1")
        check(yes.lifeTotal(actor) == 38 && no.lifeTotal(actor) == 40)
        check(yes.turnNumber == 1 && no.turnNumber == 1)
        check(elf in yes.getZone(ZoneKey(actor, Zone.HAND)))
        check(elf in no.getZone(ZoneKey(actor, Zone.HAND)))
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val profile = ManualTransmissionR5Candidate.profile
        val intents = if (profile.useCardIntent || EvalWeights.isRawProfile(profile.evalWeightsId))
            IntentCatalog.of(registry) else IntentCatalog.NONE
        val evaluator = EvalWeights.resolveEvaluator(
            id = profile.evalWeightsId,
            intents = intents,
            landDropIsNotCardLoss = profile.landDropIsNotCardLoss,
            sequenceLandsByUsableMana = profile.sequenceLandsByUsableMana,
            discountedRaceClock = profile.discountedRaceClock,
            creatureValuation = profile.creatureValuation,
            priceLandsInHandAsMana = profile.priceLandsInHandAsMana,
        )
        val noBoardScore = evaluator.evaluate(no, no.projectedState, actor)
        val yesBoardScore = evaluator.evaluate(yes, yes.projectedState, actor)
        val noInsights = mutableListOf<JsonElement>()
        val yesInsights = mutableListOf<JsonElement>()
        val noAi = AIPlayer.create(registry, actor, profile,
            insightSink = AiInsightSink { _, insight ->
                noInsights += json.parseToJsonElement(json.encodeToString(insight))
            })
        val yesAi = AIPlayer.create(registry, actor, profile,
            insightSink = AiInsightSink { _, insight ->
                yesInsights += json.parseToJsonElement(json.encodeToString(insight))
            })
        val noAction = noAi.chooseAction(no)
        val yesAction = yesAi.chooseAction(yes)
        val simulator = GameSimulator(registry)
        fun elfAffordable(state: GameState) = simulator.getLegalActions(state, actor).any {
            it.affordable && (it.action as? CastSpell)?.cardId == elf
        }
        check(!elfAffordable(no) && elfAffordable(yes))
        val yesActionResult = ActionProcessor(registry).process(yes, yesAction).result
        check(yesActionResult.error == null)
        val summary = buildJsonObject {
            put("status", "R2_EXACT_R5_CONTINUATION_DIAGNOSTIC")
            put("source", "original R1 action 14 via successful R2 primary real-engine microfork")
            put("primaryRun", 39481840733L); put("primaryArtifact", 12291623583L)
            put("actor", actor.value); put("turn", yes.turnNumber); put("profile", profile.id)
            put("noLife", no.lifeTotal(actor)); put("yesLife", yes.lifeTotal(actor))
            put("noLeafScore", noBoardScore); put("yesLeafScore", yesBoardScore)
            put("yesMinusNoLeafScore", yesBoardScore - noBoardScore)
            put("noNextAction", json.encodeToString<GameAction>(noAction))
            put("yesNextAction", json.encodeToString<GameAction>(yesAction))
            put("yesNextActionCastsArborElf", (yesAction as? CastSpell)?.cardId == elf)
            put("noNextActionCastsArborElf", (noAction as? CastSpell)?.cardId == elf)
            put("yesChosenActionEngineAccepted", yesActionResult.error == null)
            put("noAiInsightRows", JsonArray(noInsights))
            put("yesAiInsightRows", JsonArray(yesInsights))
            put("fullGames", 0); put("policyChanges", 0)
            put("note", "Scores are direct evaluator leaf scores; do not substitute them for unpublished responder internals.")
        }
        Files.writeString(output.resolve("r2-continuation.json"), summary.toString())
        println("MANUAL_R2_R5_CONTINUATION=" + summary)
    }
})
