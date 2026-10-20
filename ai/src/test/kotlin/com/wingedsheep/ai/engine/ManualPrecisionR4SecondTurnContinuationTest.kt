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
 * R4 read-only original-state experiment, NOT full four-player gameplay.
 *
 * Load the three byte-pinned R3 real-engine GameState snapshots at the start
 * of global turn 5 (Seat 1's second personal turn). Execute unchanged R5
 * on every seat until global turn 6 begins. No forced actions; no new seeds;
 * no changes to any AI profile, engine, frozen control, or accepted evidence.
 */
class ManualPrecisionR4SecondTurnContinuationTest : FunSpec({
    test("continue three exact original R3 states to end of second personal turn only") {
        val archive = Path.of(requireNotNull(System.getenv("MANUAL_R4_R3_ARCHIVE")))
        val out = Path.of(requireNotNull(System.getenv("MANUAL_R4_PRECISION_OUTPUT")))
        Files.createDirectories(out)
        val dir = archive.resolve("r3-early-output")
        val json = Json {
            serializersModule = engineSerializersModule
            allowStructuredMapKeys = true
        }
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        fun checkedState(name: String, expected: String): GameState {
            val bytes = Files.readAllBytes(dir.resolve("r3-" + name + "-endstate.json"))
            check(hash(bytes) == expected) { "Original R3 state bytes drifted: " + name }
            return json.decodeFromString<GameState>(bytes.toString(Charsets.UTF_8))
        }
        val expected = linkedMapOf(
            "no-r5" to "fb00c73279e64ca5b13c05ef9bef952e96ea1d864f1164cf417e8286192f62ac",
            "yes-r5" to "70b12ac34651e0bbcdddc4d2cdcd62527f5a833c2ec2abc6585a5b87cbcd4af0",
            "yes-elf-r5" to "ac2165c71b5f441d9d079010ad604bcbfc7ba4c51b87eae9d20f0b09b847d49a",
        )
        val originalSummaryBytes = Files.readAllBytes(dir.resolve("r3-early-continuation-summary.json"))
        check(hash(originalSummaryBytes) == "19c805675e902bc012fd754313478037ae41c6857a634ddeb407dce71d73b7f2")
        val originalSummary = Json.parseToJsonElement(
            originalSummaryBytes.toString(Charsets.UTF_8)
        ).jsonObject
        check(originalSummary["status"]?.jsonPrimitive?.content ==
            "R3_EXACT_THREE_BRANCH_BOUNDED_CONTINUATION")
        check(originalSummary["sourceRun"]?.jsonPrimitive?.long == 39477314282L)
        check(originalSummary["fullFourPlayerGames"]?.jsonPrimitive?.int == 0)
        val summaryArms = originalSummary["branches"]?.jsonArray?.map {
            it.jsonObject["arm"]?.jsonPrimitive?.content
        }?.toSet()
        check(summaryArms == expected.keys.toSet())

        val pilot = EntityId("manual-mirror-0")
        val arborElf = EntityId("e1")
        val breedingPool = EntityId("e83")
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val profile = ManualTransmissionR5Candidate.profile
        check(profile.id == "manual-transmission-r5-survival-opt-in")
        check(AiProfile.CURRENT.advisorModules.isEmpty())
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
        val processor = ActionProcessor(registry)
        val simulator = GameSimulator(registry)
        fun cardName(state: GameState, id: EntityId?): String? =
            id?.let { state.getEntity(it)?.get<CardComponent>()?.name }

        fun stateMetrics(state: GameState): JsonObject {
            val hand = state.getZone(ZoneKey(pilot, Zone.HAND))
            val battlefield = state.getZone(ZoneKey(pilot, Zone.BATTLEFIELD))
            val lands = battlefield.filter {
                state.getEntity(it)?.get<CardComponent>()?.isLand == true
            }
            val untapped = lands.filter {
                state.getEntity(it)?.has<TappedComponent>() != true
            }
            val pool = state.getEntity(pilot)?.get<ManaPoolComponent>() ?: ManaPoolComponent()
            val commander = state.getZone(ZoneKey(pilot, Zone.COMMAND))
            val inHand = hand.mapNotNull { cardName(state, it) }
            val inPlay = battlefield.mapNotNull { cardName(state, it) }
            val affordableAnimar = state.priorityPlayerId == pilot &&
                state.pendingDecision == null && state.activePlayerId == pilot &&
                state.phase.isMainPhase &&
                simulator.getLegalActions(state, pilot).any { legal ->
                    legal.affordable && (legal.action as? CastSpell)?.cardId in commander &&
                        cardName(state, (legal.action as? CastSpell)?.cardId) == "Animar, Soul of Elements"
                }
            return buildJsonObject {
                put("turnNumber", state.turnNumber)
                put("step", state.step.name)
                put("activePlayer", state.activePlayerId?.value)
                put("pilotLife", state.lifeTotal(pilot))
                put("landCount", lands.size)
                put("untappedLandCount", untapped.size)
                put("landNames", JsonArray(lands.mapNotNull { cardName(state, it) }.map(::JsonPrimitive)))
                put("handNames", JsonArray(inHand.map(::JsonPrimitive)))
                put("battlefieldNames", JsonArray(inPlay.map(::JsonPrimitive)))
                put("arborElfInHand", arborElf in hand)
                put("arborElfOnBattlefield", arborElf in battlefield)
                put("arborElfTapped", state.getEntity(arborElf)?.has<TappedComponent>() == true)
                put("breedingPoolTapped", state.getEntity(breedingPool)?.has<TappedComponent>() == true)
                put("manaPoolGreen", pool.green)
                put("manaPoolBlue", pool.blue)
                put("manaPoolRed", pool.red)
                put("animarInCommandZone", commander.any { cardName(state, it) == "Animar, Soul of Elements" })
                put("animarOnBattlefield", inPlay.contains("Animar, Soul of Elements"))
                put("animarCurrentlyAffordable", affordableAnimar)
                put("opponentLife", buildJsonObject {
                    state.turnOrder.filter { it != pilot }.forEach {
                        put(it.value, state.lifeTotal(it))
                    }
                })
                put("rawLegacyBoardScore", evaluator.evaluate(state, state.projectedState, pilot))
                put("gameOver", state.gameOver)
            }
        }

        val results = mutableListOf<JsonObject>()
        for ((arm, digest) in expected) {
            val initial = checkedState(arm, digest)
            check(initial.turnNumber == 5 && initial.activePlayerId == pilot)
            check(initial.priorityPlayerId == pilot && initial.pendingDecision == null)
            check(initial.step.name == "UPKEEP")
            check(initial.turnOrder.toSet() == (0..3).map {
                EntityId("manual-mirror-" + it)
            }.toSet())
            check(initial.gameOver.not())
            val expectedLife = if (arm == "no-r5") 40 else 38
            check(initial.lifeTotal(pilot) == expectedLife)
            check(initial.getZone(ZoneKey(pilot, Zone.BATTLEFIELD)).contains(breedingPool))
            check((arborElf in initial.getZone(ZoneKey(pilot, Zone.BATTLEFIELD))) ==
                (arm == "yes-elf-r5"))
            val sourceRow = originalSummary["branches"]!!.jsonArray.single {
                it.jsonObject["arm"]?.jsonPrimitive?.content == arm
            }.jsonObject
            check(sourceRow["status"]?.jsonPrimitive?.content == "NEXT_OWN_TURN_BOUNDARY")
            check(sourceRow["firstNextOwnTurnBoundary"]?.jsonObject
                ?.get("life")?.jsonPrimitive?.int == expectedLife)

            var current = initial
            var submitted = 0
            var status = "START"
            val started = System.nanoTime()
            val insights = mutableListOf<JsonElement>()
            val cardsCast = mutableListOf<String>()
            val landPlays = mutableListOf<JsonObject>()
            val shockChoices = mutableListOf<JsonObject>()
            val elfActions = mutableListOf<JsonObject>()
            val spentEvents = mutableListOf<JsonObject>()
            val manaEvents = mutableListOf<JsonObject>()
            val searchedOrDrawn = mutableListOf<JsonObject>()
            val animarOpportunities = mutableListOf<JsonObject>()
            var animarCasts = 0
            val ai = initial.turnOrder.associateWith { player ->
                AIPlayer.create(registry, player, profile, insightSink =
                    if (player == pilot) AiInsightSink { _, insight ->
                        if (insights.size < 64)
                            insights += json.parseToJsonElement(json.encodeToString(insight))
                    } else null
                )
            }
            val trace = out.resolve("r4-" + arm + "-actions.jsonl")
            Files.newBufferedWriter(trace).use { writer ->
                while (true) {
                    if (current.gameOver) { status = "EARLY_GAME_OVER"; break }
                    if (current.turnNumber >= 6) { status = "SECOND_PERSONAL_TURN_FINISHED"; break }
                    if (submitted >= 450) { status = "CENSORED_ACTION_CAP"; break }
                    if ((System.nanoTime() - started) / 1_000_000 > 75_000L) {
                        status = "CENSORED_TIME"; break
                    }
                    val decision = current.pendingDecision
                    val actor = decision?.playerId ?: requireNotNull(current.priorityPlayerId)
                    if (actor == pilot && decision == null && current.phase.isMainPhase &&
                        current.activePlayerId == pilot) {
                        val offered = simulator.getLegalActions(current, pilot).filter {
                            it.affordable && (it.action as? CastSpell)?.cardId
                                ?.let { id -> cardName(current, id) == "Animar, Soul of Elements" } == true
                        }
                        if (offered.isNotEmpty() && animarOpportunities.size < 12) {
                            animarOpportunities += buildJsonObject {
                                put("acceptedActionBeforeOffer", submitted)
                                put("step", current.step.name)
                                put("turn", current.turnNumber)
                                put("offerCount", offered.size)
                            }
                        }
                    }
                    val action: GameAction = if (decision != null) {
                        SubmitDecision(actor, ai.getValue(actor).respondToDecision(current, decision))
                    } else ai.getValue(actor).chooseAction(current)
                    val sourceId = when (action) {
                        is PlayLand -> action.cardId
                        is CastSpell -> action.cardId
                        is ActivateAbility -> action.sourceId
                        is TypecycleCard -> action.cardId
                        is CycleCard -> action.cardId
                        else -> null
                    }
                    val source = cardName(current, sourceId)
                    val shockChoice = (decision as? YesNoDecision)?.takeIf {
                        actor == pilot && it.context.sourceName in
                            setOf("Breeding Pool", "Steam Vents", "Stomping Ground") &&
                            it.prompt.startsWith("Pay 2 life")
                    }
                    val chosenShock = (action as? SubmitDecision)?.response as? YesNoResponse
                    if (shockChoice != null && chosenShock != null) {
                        shockChoices += buildJsonObject {
                            put("index", submitted + 1)
                            put("land", shockChoice.context.sourceName)
                            put("payTwoLife", chosenShock.choice)
                            put("lifeBefore", current.lifeTotal(pilot))
                        }
                    }
                    if (actor == pilot && action is PlayLand) {
                        landPlays += buildJsonObject {
                            put("index", submitted + 1); put("landName", source)
                            put("globalTurn", current.turnNumber)
                        }
                    }
                    val result = processor.process(current, action).result
                    check(result.error == null) {
                        "R4 second-turn rejected arm=" + arm + " action=" +
                            (submitted + 1) + " error=" + result.error
                    }
                    submitted++
                    val eventStrings = result.events.map { it.toString() }
                    if (actor == pilot && source == "Arbor Elf") {
                        elfActions += buildJsonObject {
                            put("index", submitted)
                            put("actionType", action::class.simpleName)
                            put("events", JsonArray(eventStrings.map(::JsonPrimitive)))
                        }
                    }
                    if (eventStrings.any { it.contains("SpellCastEvent(") &&
                            it.contains("cardName=Animar, Soul of Elements") &&
                            it.contains("casterId=" + pilot.value) }) animarCasts++
                    eventStrings.forEach { event ->
                        if (event.startsWith("SpellCastEvent(") &&
                            event.contains("casterId=" + pilot.value)) {
                            cardsCast += event
                        }
                        if (event.startsWith("ManaSpentEvent(") &&
                            event.contains("playerId=" + pilot.value)) {
                            spentEvents += buildJsonObject {
                                put("index", submitted); put("event", event)
                            }
                        }
                        if (event.startsWith("Mana") || event.startsWith("TappedEvent(") ||
                            event.startsWith("UntappedEvent(") ||
                            event.startsWith("AbilityActivatedEvent(")) {
                            if (actor == pilot || event.contains(pilot.value) ||
                                event.contains("entityName=Arbor Elf")) {
                                manaEvents += buildJsonObject {
                                    put("index", submitted); put("event", event)
                                }
                            }
                        }
                        if ((event.contains("CardsDrawnEvent(") ||
                             event.contains("LibrarySearchedEvent(") ||
                             event.contains("ZoneChangeEvent(")) &&
                            (event.contains(pilot.value) || actor == pilot)) {
                            if (searchedOrDrawn.size < 100) {
                                searchedOrDrawn += buildJsonObject {
                                    put("index", submitted); put("event", event)
                                }
                            }
                        }
                    }
                    writer.write(buildJsonObject {
                        put("index", submitted)
                        put("actor", actor.value)
                        put("globalTurn", current.turnNumber)
                        put("step", current.step.name)
                        put("sourceCard", source)
                        put("action", json.encodeToString<GameAction>(action))
                        put("engineEvents", JsonArray(eventStrings.map(::JsonPrimitive)))
                        put("pilotLifeAfter", result.state.lifeTotal(pilot))
                        put("nextTurn", result.state.turnNumber)
                    }.toString())
                    writer.newLine()
                    current = result.state
                }
            }
            Files.writeString(out.resolve("r4-" + arm + "-endstate.json"),
                json.encodeToString(current))
            val data = buildJsonObject {
                put("arm", arm)
                put("status", status)
                put("startStateSha256", digest)
                put("acceptedActions", submitted)
                put("engineRejections", 0)
                put("sourceAtTurnFive", stateMetrics(initial))
                put("atTurnSix", stateMetrics(current))
                put("landPlays", JsonArray(landPlays))
                put("shockDecisions", JsonArray(shockChoices))
                put("arborElfActions", JsonArray(elfActions))
                put("manaRelatedEvents", JsonArray(manaEvents))
                put("manaSpentEvents", JsonArray(spentEvents))
                put("pilotCastSpellEvents", JsonArray(cardsCast.map(::JsonPrimitive)))
                put("searchDrawAndZoneChanges", JsonArray(searchedOrDrawn))
                put("animarLegalCastingWindows", JsonArray(animarOpportunities))
                put("animarCasts", animarCasts)
                put("pilotAiInsights", JsonArray(insights))
                put("r5Profile", profile.id)
                put("fullGames", 0)
                put("policyChanges", 0)
            }
            Files.writeString(out.resolve("r4-" + arm + "-summary.json"), data.toString())
            println("MANUAL_R4_SECOND_TURN_BRANCH=" + data)
            check(status == "SECOND_PERSONAL_TURN_FINISHED") {
                "R4 incomplete arm=" + arm + " status=" + status + " actions=" + submitted
            }
            check(current.turnNumber == 6)
            check(current.gameOver.not())
            results += data
        }
        check(results.size == 3)
        val summary = buildJsonObject {
            put("status", "R4_REAL_ENGINE_SECOND_TURN_THREE_BRANCHES_COMPLETE")
            put("sourceRun", 39486360088L)
            put("sourceArtifact", 12294360902L)
            put("originalGameRun", 39477314282L)
            put("originalSeed", 202610202112L)
            put("inputR3Source", "50456046cc25878591df9a498f6e950aeb1a93b5")
            put("inputR3SummarySha256", "19c805675e902bc012fd754313478037ae41c6857a634ddeb407dce71d73b7f2")
            put("stopAtGlobalTurn", 6)
            put("policy", profile.id)
            put("branches", JsonArray(results))
            put("fullFourPlayerGames", 0)
            put("productionChanges", 0)
            put("candidatePromotions", 0)
            put("disclaimer", "Legacy 99-turn race score is not a win-probability estimate; identical AI profiles do not guarantee identical draws after divergent cycling.")
        }
        Files.writeString(out.resolve("r4-second-turn-comparison.json"), summary.toString())
        println("MANUAL_R4_SECOND_TURN_RESULT=" + summary)
    }
})
