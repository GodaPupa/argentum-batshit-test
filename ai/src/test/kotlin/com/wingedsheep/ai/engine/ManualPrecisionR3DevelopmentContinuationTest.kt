package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.evaluation.EvalWeights
import com.wingedsheep.ai.engine.knowledge.IntentCatalog
import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
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
 * Three separately labelled R3 continuations from EXACT source-bound original R1 action 14:
 *   NO + unchanged R5; YES + unchanged R5; YES + forced LEGAL Arbor Elf + unchanged R5.
 * An explicit personal-turn cutoff keeps this a bounded forensic microfork, NOT a full game.
 *
 * No original R1 seed rerun; no synthetic card effects; no AI production / CURRENT changes.
 */
class ManualPrecisionR3DevelopmentContinuationTest : FunSpec({
    test("compare three original-state development paths only through the next own turn boundary") {
        val archive = Path.of(requireNotNull(System.getenv("MANUAL_R3_PRIMARY_ARCHIVE")))
        val output = Path.of(requireNotNull(System.getenv("MANUAL_R3_OUTPUT")))
        Files.createDirectories(output)
        val dir = archive.resolve("r2-shock-output")
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        fun readPinned(filename: String, expectedHash: String): String {
            val bytes = Files.readAllBytes(dir.resolve(filename))
            check(hash(bytes) == expectedHash) { "Original R2 real-engine fork drift: " + filename }
            return bytes.toString(Charsets.UTF_8)
        }
        val json = Json {
            serializersModule = engineSerializersModule
            allowStructuredMapKeys = true
        }
        val originalSummary = Json.parseToJsonElement(
            Files.readString(dir.resolve("r2-primary-microfork.json"))
        ).jsonObject
        check(originalSummary["originalRun"]?.jsonPrimitive?.long == 39477314282L)
        check(originalSummary["originalActionsReplayedExactly"]?.jsonPrimitive?.int == 13)
        check(originalSummary["r5RespondedNo"]?.jsonPrimitive?.boolean == true)
        check(originalSummary["yesArborElfEngineCastAccepted"]?.jsonPrimitive?.boolean == true)
        val noState = json.decodeFromString<GameState>(readPinned(
            "state-after-no.json", "552cd7d26bf9965077d5ea8b0e502301d07fd171bbd99bbc745fe9ce0b25afd5"))
        val yesState = json.decodeFromString<GameState>(readPinned(
            "state-after-yes.json", "7e8373fd825b1f50c09158d25b45621152548ff8743c68ca604df13332366031"))
        readPinned(
            "state-before-r0.json", "0df555d80bc0a4eab24577dd4cb6de0424564643597d2bd64379c9bde23014d0")
        val pilot = EntityId("manual-mirror-0")
        val elfId = EntityId("e1")
        val poolId = EntityId("e83")
        val entId = EntityId("e40")
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val processor = ActionProcessor(registry)
        val simulator = GameSimulator(registry)
        val profile = ManualTransmissionR5Candidate.profile
        check(profile.id == "manual-transmission-r5-survival-opt-in")
        check(AiProfile.CURRENT.advisorModules.isEmpty())
        check(noState.turnNumber == 1 && yesState.turnNumber == 1)
        check(noState.activePlayerId == pilot && yesState.activePlayerId == pilot)
        check(noState.priorityPlayerId == pilot && yesState.priorityPlayerId == pilot)
        check(noState.lifeTotal(pilot) == 40 && yesState.lifeTotal(pilot) == 38)
        check(noState.getEntity(poolId)?.has<TappedComponent>() == true)
        check(yesState.getEntity(poolId)?.has<TappedComponent>() != true)
        check(noState.turnOrder == yesState.turnOrder)
        check(elfId in yesState.getZone(ZoneKey(pilot, Zone.HAND)))
        check(entId in yesState.getZone(ZoneKey(pilot, Zone.HAND)))
        val elfAction = simulator.getLegalActions(yesState, pilot).firstOrNull {
            it.affordable && (it.action as? CastSpell)?.cardId == elfId
        }?.action as? CastSpell
        check(elfAction != null) { "No legally affordable exact Arbor Elf cast after Yes" }
        val intents = if (profile.useCardIntent || EvalWeights.isRawProfile(profile.evalWeightsId))
            IntentCatalog.of(registry) else IntentCatalog.NONE
        val evaluator = EvalWeights.resolveEvaluator(
            id = profile.evalWeightsId,
            intents = intents,
            landDropIsNotCardLoss = profile.landDropIsNotCardLoss,
            sequenceLandsByUsableMana = profile.sequenceLandsByUsableMana,
            discountedRaceClock = profile.discountedRaceClock,
            creatureValuation = profile.creatureValuation,
            priceLandsInHandAsMana = profile.priceLandsInHandAsMana
        )
        fun measure(state: GameState): JsonObject {
            val hand = state.getZone(ZoneKey(pilot, Zone.HAND))
            val battlefield = state.getZone(ZoneKey(pilot, Zone.BATTLEFIELD))
            val landIds = battlefield.filter {
                state.getEntity(it)?.get<CardComponent>()?.isLand == true
            }
            val namesInHand = hand.mapNotNull { state.getEntity(it)?.get<CardComponent>()?.name }
            val namesInPlay = battlefield.mapNotNull { state.getEntity(it)?.get<CardComponent>()?.name }
            val mana = state.getEntity(pilot)?.get<ManaPoolComponent>() ?: ManaPoolComponent()
            return buildJsonObject {
                put("globalTurn", state.turnNumber)
                put("step", state.step.name)
                put("activePlayer", state.activePlayerId?.value)
                put("life", state.lifeTotal(pilot))
                put("boardScore", evaluator.evaluate(state, state.projectedState, pilot))
                put("landCount", landIds.size)
                put("untappedLandCount", landIds.count { state.getEntity(it)?.has<TappedComponent>() != true })
                put("handSize", hand.size)
                put("handNames", JsonArray(namesInHand.map(::JsonPrimitive)))
                put("battlefieldNames", JsonArray(namesInPlay.map(::JsonPrimitive)))
                put("arborElfInHand", elfId in hand)
                put("arborElfOnBattlefield", elfId in battlefield)
                put("entInHand", entId in hand)
                put("entOnBattlefield", entId in battlefield)
                put("forestInHand", namesInHand.count { it == "Forest" })
                put("arborElfCastSpellsPossibleNow",
                    if (state.priorityPlayerId == pilot && state.pendingDecision == null)
                        simulator.getLegalActions(state, pilot).count {
                            it.affordable && (it.action as? CastSpell)?.cardId == elfId
                        } else 0)
                put("manaGreen", mana.green)
                put("manaBlue", mana.blue)
                put("manaRed", mana.red)
                put("gameOver", state.gameOver)
            }
        }
        val firstYesAi = AIPlayer.create(registry, pilot, profile)
        val nextYes = firstYesAi.chooseAction(yesState)
        check(nextYes is TypecycleCard && nextYes.cardId == entId) {
            "R5 no longer chooses Generous Ent forestcycling on the exact paid-state"
        }
        val firstNoAi = AIPlayer.create(registry, pilot, profile)
        val nextNo = firstNoAi.chooseAction(noState)
        check(nextNo is PassPriority) {
            "Original R5 No-path immediate action changed; comparison is not source-bound"
        }
        data class Observation(val id: String, val data: JsonObject)
        fun run(name: String, initial: GameState, forcedFirst: GameAction?): Observation {
            var state = initial
            val insights = mutableListOf<JsonElement>()
            val ai = state.turnOrder.associateWith { player ->
                AIPlayer.create(registry, player, profile,
                    insightSink = if (player == pilot) AiInsightSink { _, insight ->
                        if (insights.size < 12)
                            insights += json.parseToJsonElement(json.encodeToString(insight))
                    } else null)
            }
            var accepted = 0
            var firstOwnTurnEnd: JsonObject? = null
            var terminal = "STARTED"
            var firstAction = ""
            var firstActionIsArborElf = false
            val startNs = System.nanoTime()
            val prefix = output.resolve("r3-" + name)
            Files.newBufferedWriter(output.resolve("r3-" + name + "-actions.jsonl")).use { writer ->
                while (true) {
                    if (state.gameOver) { terminal = "EARLY_TERMINAL"; break }
                    if (state.turnNumber >= 5) { terminal = "NEXT_OWN_TURN_BOUNDARY"; break }
                    if (accepted >= 550) { terminal = "CENSORED_ACTION_CAP"; break }
                    if ((System.nanoTime() - startNs) / 1_000_000 > 70_000L) {
                        terminal = "CENSORED_TIME"; break
                    }
                    val decision = state.pendingDecision
                    val actor = decision?.playerId ?: requireNotNull(state.priorityPlayerId)
                    val action: GameAction = if (accepted == 0 && forcedFirst != null) {
                        check(actor == pilot && decision == null)
                        forcedFirst
                    } else if (decision != null)
                        SubmitDecision(actor, ai.getValue(actor).respondToDecision(state, decision))
                    else ai.getValue(actor).chooseAction(state)
                    if (accepted == 0) {
                        firstAction = json.encodeToString<GameAction>(action)
                        firstActionIsArborElf = (action as? CastSpell)?.cardId == elfId
                    }
                    val before = state
                    val result = processor.process(before, action).result
                    check(result.error == null) { "R3 rejected " + name + " action " +
                        (accepted + 1) + ": " + result.error }
                    accepted++
                    writer.write(buildJsonObject {
                        put("index", accepted)
                        put("actor", actor.value)
                        put("globalTurn", before.turnNumber)
                        put("step", before.step.name)
                        put("action", json.encodeToString<GameAction>(action))
                        put("engineEvents", JsonArray(result.events.map { JsonPrimitive(it.toString()) }))
                    }.toString())
                    writer.newLine()
                    state = result.state
                    if (firstOwnTurnEnd == null && before.turnNumber == 1 && state.turnNumber > 1)
                        firstOwnTurnEnd = measure(state)
                }
            }
            Files.writeString(prefix.resolveSibling(prefix.fileName.toString() + "-endstate.json"),
                json.encodeToString(state))
            val summary = buildJsonObject {
                put("arm", name)
                put("status", terminal)
                put("firstAction", firstAction)
                put("firstActionIsArborElf", firstActionIsArborElf)
                put("acceptedActions", accepted)
                put("engineRejections", 0)
                put("initialState", measure(initial))
                put("endOfPersonalTurnOne", firstOwnTurnEnd ?: JsonNull)
                put("firstNextOwnTurnBoundary", measure(state))
                put("pilotInsightRows", JsonArray(insights))
                put("extraFullGames", 0)
                put("r5Profile", profile.id)
            }
            println("MANUAL_R3_BRANCH=" + summary)
            return Observation(name, summary)
        }
        val no = run("no-r5", noState, null)
        val yesR5 = run("yes-r5", yesState, null)
        val yesElf = run("yes-elf-r5", yesState, elfAction)
        for (obs in listOf(no, yesR5, yesElf)) {
            check(obs.data["status"]?.jsonPrimitive?.content == "NEXT_OWN_TURN_BOUNDARY") {
                "R3 incomplete bounded comparison for " + obs.id + ": " + obs.data
            }
        }
        check(no.data["firstActionIsArborElf"]?.jsonPrimitive?.boolean == false)
        check(yesR5.data["firstActionIsArborElf"]?.jsonPrimitive?.boolean == false)
        check(yesElf.data["firstActionIsArborElf"]?.jsonPrimitive?.boolean == true)
        val report = buildJsonObject {
            put("status", "R3_EXACT_THREE_BRANCH_BOUNDED_CONTINUATION")
            put("sourceRun", 39477314282L)
            put("sourceArtifact", 12290286293L)
            put("primaryMicroforkRun", 39481840733L)
            put("primaryMicroforkArtifact", 12291623583L)
            put("primaryR5ContinuityRun", 39483773523L)
            put("seed", 202610202112L)
            put("seat", pilot.value)
            put("startingTurn", 1)
            put("stopBeforeGlobalTurn", 5)
            put("sharedOpponentPolicy", profile.id)
            put("branches", JsonArray(listOf(no, yesR5, yesElf).map { it.data }))
            put("productionChanges", 0)
            put("fullFourPlayerGames", 0)
            put("noPolicySelectionOrPromotion", true)
            put("interpretation", "Bounded real-engine early sequence comparison; no terminal win-rate inference.")
        }
        Files.writeString(output.resolve("r3-early-continuation-summary.json"), report.toString())
        println("MANUAL_PRECISION_R3_RESULT=" + report)
    }
})
