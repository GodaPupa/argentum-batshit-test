package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Opt-in R5 micro-forks from the exact R4 original GameState checkpoints.
 *
 * NOT new four-player games and NOT opponent-responsive future continuations:
 * GameSimulator automatically passes priority to reach a quiet state.
 * No changes to hardware, CURRENT, policies, gears, or production engine.
 * A different leaf evaluation, counter, or affordable second play is NOT
 * proof the real opponent pod would have produced a better game result.
 */
class ManualIdentityR5Bounded1622ForksTest : FunSpec({
    test("source-bound R4 positions: accepted raw actions, bounded quiet forks and two-cast availability") {
        check(System.getenv("MANUAL_IDENTITY_R5_MODE") == "EXPLICIT_R4_SOURCE_BOUND_MICRO_FORKS")
        val archive = Path.of(requireNotNull(System.getenv("MANUAL_IDENTITY_R5_R4_ARCHIVE")))
        val output = Path.of(requireNotNull(System.getenv("MANUAL_IDENTITY_R5_OUTPUT")))
        Files.createDirectories(output)
        fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        check(sha(Files.readAllBytes(archive)) ==
            "d27a0b8bedc4377c1852cfde23f217e6b92c62a193bdf67972da7320be7ac492") {
            "R4 source evidence ZIP bytes do not match original archived artifact"
        }
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val processor = ActionProcessor(registry)
        val simulator = GameSimulator(registry)
        val evaluator = AIPlayer.defaultEvaluator()
        val actor = EntityId("manual-mirror-3")
        val commanderName = "Animar, Soul of Elements"
        val targets = linkedMapOf(
            1153 to "Paradise Druid",
            1158 to "Fblthp, the Lost",
            1325 to "Nulldrifter",
            1514 to commanderName,
        )
        fun JsonObject.string(field: String) = this[field]?.jsonPrimitive?.contentOrNull
        fun JsonObject.integer(field: String) = this[field]?.jsonPrimitive?.intOrNull
        fun cardsInHand(state: GameState): List<String> =
            state.getZone(ZoneKey(actor, Zone.HAND)).mapNotNull {
                state.getEntity(it)?.get<CardComponent>()?.name
            }
        fun animarCounters(state: GameState, commanderCardId: com.wingedsheep.sdk.model.EntityId): Int? {
            if (commanderCardId !in state.getZone(ZoneKey(actor, Zone.BATTLEFIELD))) return null
            return state.getEntity(commanderCardId)?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
        }

        val rows = mutableListOf<JsonElement>()
        ZipFile(archive.toFile()).use { zip ->
            fun read(name: String): String = zip.getInputStream(
                requireNotNull(zip.getEntry("r4-receipt/" + name)) { "Missing R4 original checkpoint: $name" }
            ).bufferedReader().readText()
            check(read("source-sha.txt").trim() == "68a8e331c84ebf53575deab9951ec80ca75312a3")
            val originalReport = json.parseToJsonElement(read("result.json")).jsonObject
            check(originalReport.string("status") == "EXACT_ORIGINAL_STATE_REPLAY_VERIFIED")
            check(originalReport.integer("archivedSubmissionsReplayed") == 1513)
            check(originalReport.integer("matchingEngineEventRows") == 1513)
            check(originalReport["prefix64FullSerializedStateEqual"]?.jsonPrimitive?.boolean == true)
            check(originalReport.string("seed") == "202610191622")
            check(originalReport.string("arm") == "control")
            check(originalReport.string("profile") == "manual-transmission-r5-survival-opt-in")
            check(originalReport.integer("defectsProven") == 0)
            check(originalReport.integer("fullGamesInitialized") == 0)
            val reportWindows = originalReport.getValue("observedWindows").jsonArray
                .map { it.jsonObject }.associateBy { it.integer("index") }
            check(reportWindows.size == 7 && targets.keys.all { it in reportWindows })

            for ((index, expectedChosenName) in targets) {
                val stateText = read("exact-state-before-$index.json")
                val recorded = reportWindows.getValue(index)
                check(sha(stateText.toByteArray(Charsets.UTF_8)) ==
                    recorded.string("stateBeforeSha256")) {
                    "R4 reconstructed checkpoint bytes unexpectedly changed at $index"
                }
                val state = json.decodeFromString<GameState>(stateText)
                check(state.turnNumber == recorded.integer("globalTurn") &&
                    state.step.name == recorded.string("step") &&
                    state.priorityPlayerId == actor && state.activePlayerId == actor)
                val decision = json.parseToJsonElement(read("decision-before-$index.json")).jsonObject
                check(decision.string("playerId") == actor.value &&
                    decision.string("chosenLabel") == recorded.string("chosenLabel"))
                val options = decision.getValue("options").jsonArray.map { it.jsonObject }
                val chosen = options.single { it["chosen"]?.jsonPrimitive?.booleanOrNull == true }
                val animar = options.single {
                    it.string("cardName") == commanderName &&
                        it.string("actionType") == "CastSpell"
                }
                check(chosen.string("cardName") == expectedChosenName) {
                    "Unexpected original chosen card at $index"
                }
                val originalAction = json.decodeFromString<GameAction>(
                    chosen.getValue("action").toString()
                ) as CastSpell
                val alternative = json.decodeFromString<GameAction>(
                    animar.getValue("action").toString()
                ) as CastSpell
                val commander = state.getEntity(alternative.cardId)?.get<CommanderComponent>()
                check(alternative.cardId in state.getZone(ZoneKey(actor, Zone.COMMAND)))
                check(commander?.castsFromCommandZone == 0)
                check(originalAction.playerId == actor && alternative.playerId == actor)

                // Raw ActionProcessor checks are authoritative about legal initial casts
                // and do not auto-pass an opponent or silently answer pending decisions.
                val rawOriginal = processor.process(state, originalAction).result
                val rawAnimar = processor.process(state, alternative).result
                check(rawOriginal.error == null && rawAnimar.error == null) {
                    "R4 decision cast became illegal at action $index"
                }
                fun branch(first: CastSpell, second: CastSpell, lane: String): JsonObject {
                    val quiet = simulator.simulate(state, first)
                    return buildJsonObject {
                        put("lane", lane)
                        put("firstCastCard", state.getEntity(first.cardId)?.get<CardComponent>()?.name)
                        put("rawFirstCastAccepted", true)
                        put("quietBoundaryType", quiet::class.simpleName)
                        put("opponentsAutopassedDuringSimulation", true)
                        put("opponentResponsive", false)
                        if (quiet is SimulationResult.Terminal) {
                            val after = quiet.state
                            put("firstQuietGameOver", after.gameOver)
                            put("firstQuietGlobalTurn", after.turnNumber)
                            put("firstQuietStateSha256",
                                sha(json.encodeToString(after).toByteArray(Charsets.UTF_8)))
                            put("firstQuietAnimarCounters", animarCounters(after, alternative.cardId)
                                ?.let(::JsonPrimitive) ?: JsonNull)
                            put("firstQuietHand", JsonArray(cardsInHand(after).map(::JsonPrimitive)))
                            put("firstQuietEvaluator", evaluator.evaluate(after, after.projectedState, actor))
                            // Do not infer a future line from mana totals; check actual
                            // affordable legal CastSpell actions in the quiet state.
                            val legal = if (after.gameOver) emptyList() else
                                simulator.getLegalActions(after, actor).filter {
                                    it.affordable && (it.action as? CastSpell)?.let { candidate ->
                                        candidate.cardId == second.cardId &&
                                            candidate.useAlternativeCost == second.useAlternativeCost &&
                                            candidate.alternativeCostType == second.alternativeCostType
                                    } == true
                                }
                            put("secondCastCard", state.getEntity(second.cardId)?.get<CardComponent>()?.name)
                            put("secondCastAffordableLegal", legal.isNotEmpty())
                            if (legal.isNotEmpty()) {
                                val exact = legal.first().action as CastSpell
                                val rawSecond = processor.process(after, exact).result
                                check(rawSecond.error == null) {
                                    "Reported affordable second CastSpell rejected at $index/$lane"
                                }
                                put("rawSecondCastAccepted", true)
                                val secondQuiet = simulator.simulate(after, exact)
                                put("secondQuietBoundaryType", secondQuiet::class.simpleName)
                                if (secondQuiet is SimulationResult.Terminal) {
                                    val end = secondQuiet.state
                                    put("secondQuietGlobalTurn", end.turnNumber)
                                    put("secondQuietAnimarCounters",
                                        animarCounters(end, alternative.cardId)
                                            ?.let(::JsonPrimitive) ?: JsonNull)
                                    put("secondQuietStateSha256",
                                        sha(json.encodeToString(end).toByteArray(Charsets.UTF_8)))
                                    put("secondQuietEvaluator", evaluator.evaluate(end, end.projectedState, actor))
                                }
                            }
                        } else {
                            put("secondCastAffordableLegal", JsonNull)
                            put("unresolved", true)
                        }
                        put("strategicDefectProven", false)
                    }
                }
                val chosenThenAnimar = branch(originalAction, alternative, "CHOSEN_THEN_ANIMAR")
                val animarThenChosen = branch(alternative, originalAction, "ANIMAR_THEN_CHOSEN")
                if (index == 1514) {
                    // Positive control: both branches begin with precisely the same archived cast.
                    check(originalAction == alternative)
                    check(chosenThenAnimar.getValue("firstQuietStateSha256") ==
                        animarThenChosen.getValue("firstQuietStateSha256")) {
                        "Identical positive-control action fork produced different quiet states"
                    }
                }
                val result = buildJsonObject {
                    put("actionIndex", index)
                    put("originalGlobalTurn", state.turnNumber)
                    put("sourceStateSha256", recorded.getValue("stateBeforeSha256"))
                    put("originalChosen", chosen.string("label"))
                    put("originalChosenScore", chosen.getValue("score"))
                    put("originalAnimarScore", animar.getValue("score"))
                    put("originalHand", JsonArray(cardsInHand(state).map(::JsonPrimitive)))
                    put("originalManaCost", recorded.getValue("animarOfferManaCost"))
                    put("originalUntappedLands", recorded.getValue("untappedLandNames"))
                    put("chosenThenAnimar", chosenThenAnimar)
                    put("animarThenChosen", animarThenChosen)
                    put("opponentsAutopassedNotRealCounterfactual", true)
                    put("defectProven", false)
                    put("cardPromotion", false)
                    put("AIProfileCurrentChanged", false)
                }
                rows += result
                Files.writeString(output.resolve("forks.json"), JsonArray(rows).toString())
                println("IDENTITY_R5_FORK_" + index + "=" + result)
            }
        }
        check(rows.size == targets.size)
        val summary = buildJsonObject {
            put("status", "BOUNDED_ORIGINAL_STATE_MICRO_FORKS_COMPLETE")
            put("sourceR4Run", 39454771346L)
            put("sourceR4Commit", "68a8e331c84ebf53575deab9951ec80ca75312a3")
            put("sourceR4Artifact", 12280017350L)
            put("originalR22Seed", "202610191622")
            put("originalArm", "control")
            put("originalAIProfile", "manual-transmission-r5-survival-opt-in")
            put("statesExamined", rows.size)
            put("newGameInitializations", 0)
            put("newFullGameRuns", 0)
            put("productionChanges", 0)
            put("acceptedHardwareChanges", 0)
            put("defectsProven", 0)
            put("automaticCardOrPolicyPromotion", false)
            put("opponentResponsiveStrategicWinComparison", false)
            put("conclusion", "NO_PROVEN_DEFECT")
            put("note", "Simulator auto-pass immediate branches are descriptive, not normal-opponent future continuations.")
        }
        Files.writeString(output.resolve("summary.json"), summary.toString())
        println("IDENTITY_R5_BOUNDED_FORKS_PASS windows=" + rows.size +
            " new_games=0 promotions=0 defects=0")
    }
})
