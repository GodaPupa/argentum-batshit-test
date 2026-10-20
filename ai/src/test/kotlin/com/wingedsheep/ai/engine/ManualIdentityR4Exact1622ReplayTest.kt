package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
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
import java.util.zip.ZipFile

/**
 * Reprocess exact original R22 control seed 202610191622 actions only.
 * Never GameInitializer, new seeds, AI actions, policy/candidate mutations, or
 * artificial opponent passes. No counterfactual fork is admitted until strict
 * prefix+event+life fidelity is proven. This first gate NEVER forks.
 */
class ManualIdentityR4Exact1622ReplayTest : FunSpec({
    test("strict seed-1622 archived action reconstruction, no forks") {
        check(System.getenv("MANUAL_IDENTITY_R4_MODE") == "ARCHIVAL_REPLAY_ONLY")
        val original = Path.of(requireNotNull(System.getenv("MANUAL_IDENTITY_R4_R22_ZIP")))
        val output = Path.of(requireNotNull(System.getenv("MANUAL_IDENTITY_R4_OUTPUT")))
        Files.createDirectories(output)
        fun sha(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        check(sha(Files.readAllBytes(original)) ==
            "418d9d30cef71a4d1c025d9986294f55c8be8d295555cbbaa3fc3a27c5d2ad22") {
            "Original R22 control seed-1622 archive has changed"
        }
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val processor = ActionProcessor(registry)
        val simulator = GameSimulator(registry)
        fun JsonObject.str(field: String) = this[field]?.jsonPrimitive?.contentOrNull
        fun JsonObject.num(field: String) = this[field]?.jsonPrimitive?.intOrNull
        val selected = setOf(451, 826, 987, 1153, 1158, 1325, 1514)
        val actorExpected = "manual-mirror-3"
        val observations = mutableListOf<JsonElement>()
        val selectedStates = mutableMapOf<Int, GameState>()
        var exactEvents = 0
        var replayed = 0
        var prefixMatches = false
        ZipFile(original.toFile()).use { zip ->
            fun read(path: String): String = zip.getInputStream(
                requireNotNull(zip.getEntry(path)) { "Missing original R22 entry: " + path }
            ).bufferedReader().readText()
            check(read("r22-game-output/source-sha.txt").trim() ==
                "10675d8dc3133294c035606f656f9e4d12d50939")
            val fullPrefix = "r22-game-output/control-seed-202610191622/"
            val smokePrefix = "r22-game-output/control-seed-202610191622-prefix/"
            val initialText = read(fullPrefix + "initial-state.json")
            check(read(smokePrefix + "initial-state.json") == initialText) {
                "Original smoke and full initial states differ"
            }
            val expectedPrefixState = json.parseToJsonElement(
                read(smokePrefix + "final-state.json"))
            val summary = json.parseToJsonElement(read(fullPrefix + "summary.json")).jsonObject
            check(summary.str("status") == "COMPLETE" &&
                summary.str("arm") == "control" &&
                summary.str("profile") == "manual-transmission-r5-survival-opt-in" &&
                summary.str("winner") == "manual-mirror-3" &&
                summary.num("accepted") == 2325 &&
                summary.num("seed") == null) {
                "Original run binding, profile, winner, or action count mismatch"
            }
            check(summary["seed"]?.jsonPrimitive?.longOrNull == 202610191622L)
            val archived = read(fullPrefix + "actions.jsonl").lineSequence()
                .filter(String::isNotBlank).map { json.parseToJsonElement(it).jsonObject }.toList()
            val submitted = archived.filter { it.str("record") == "submit" }
            val results = archived.filter { it.str("record") == "result" }
                .associateBy { it.num("index") }
            val decisions = read(fullPrefix + "decisions.jsonl").lineSequence()
                .filter(String::isNotBlank).map { json.parseToJsonElement(it).jsonObject }.toList()
            check(submitted.size == 2325 && results.size == 2325)
            var state = json.decodeFromString<GameState>(initialText)
            for (row in submitted) {
                val index = requireNotNull(row.num("index"))
                check(index == replayed + 1) { "Original index gap at " + index }
                if (index in selected) {
                    check(row.str("actor") == actorExpected) { "Wrong original target actor at " + index }
                    check(state.turnNumber == row.num("turn") && state.step.name == row.str("step")) {
                        "Turn/step mismatch before action " + index
                    }
                    val originalAction = json.parseToJsonElement(requireNotNull(row.str("action")))
                    val eligible = decisions.filter { insight ->
                        insight.str("playerId") == actorExpected &&
                        insight.num("turnNumber") == state.turnNumber &&
                        insight.str("step") == state.step.name &&
                        (insight["options"] as? JsonArray)?.any { option ->
                            val o = option.jsonObject
                            o["chosen"]?.jsonPrimitive?.booleanOrNull == true &&
                                o["action"] == originalAction
                        } == true
                    }
                    check(eligible.size == 1) {
                        "Scored original decision not uniquely identified at action " + index
                    }
                    val decision = eligible.single()
                    val options = decision.getValue("options").jsonArray.map { it.jsonObject }
                    val chosen = options.single {
                        it["chosen"]?.jsonPrimitive?.booleanOrNull == true
                    }
                    val animar = options.single {
                        it.str("cardName") == "Animar, Soul of Elements" &&
                            it.str("actionType") == "CastSpell"
                    }
                    val cast = json.decodeFromString<GameAction>(
                        requireNotNull(animar["action"]).toString()) as CastSpell
                    val actor = EntityId(actorExpected)
                    check(cast.cardId in state.getZone(ZoneKey(actor, Zone.COMMAND)))
                    val offer = simulator.getLegalActions(state, actor).single {
                        it.affordable && (it.action as? CastSpell)?.cardId == cast.cardId
                    }
                    val cmd = state.getEntity(cast.cardId)?.get<CommanderComponent>()
                    val pool = state.getEntity(actor)?.get<ManaPoolComponent>()
                    val hand = state.getZone(ZoneKey(actor, Zone.HAND)).map { id ->
                        state.getEntity(id)?.get<CardComponent>()?.name ?: id.value
                    }
                    val own = state.controlledBattlefield(actor)
                    val lands = own.filter { state.getEntity(it)?.get<CardComponent>()?.isLand == true }
                    val untappedLands = lands.filter {
                        state.getEntity(it)?.has<TappedComponent>() != true
                    }
                    val opponents = state.getOpponents(actor).associate { id ->
                        id.value to state.controlledBattlefield(id)
                            .mapNotNull { state.getEntity(it)?.get<CardComponent>()?.name }
                    }
                    val stateText = json.encodeToString(state)
                    Files.writeString(output.resolve("exact-state-before-" + index + ".json"), stateText)
                    Files.writeString(output.resolve("decision-before-" + index + ".json"), decision.toString())
                    observations += buildJsonObject {
                        put("index", index)
                        put("stateBeforeSha256", sha(stateText.toByteArray()))
                        put("actor", actor.value)
                        put("globalTurn", state.turnNumber)
                        put("step", state.step.name)
                        put("chosenLabel", chosen.str("label"))
                        put("chosenScore", chosen["score"] ?: JsonNull)
                        put("animarScore", animar["score"] ?: JsonNull)
                        put("animarOfferManaCost", offer.manaCostString)
                        put("animarActuallyAffordable", offer.affordable)
                        put("previousCommanderCasts", cmd?.castsFromCommandZone)
                        put("commanderTaxGeneric", (cmd?.castsFromCommandZone ?: 0) * 2)
                        put("hand", JsonArray(hand.map(::JsonPrimitive)))
                        put("controlledLandCount", lands.size)
                        put("untappedLandCount", untappedLands.size)
                        put("untappedLandNames", JsonArray(untappedLands.map {
                            JsonPrimitive(state.getEntity(it)?.get<CardComponent>()?.name ?: it.value)
                        }))
                        put("floatingManaWhite", pool?.white ?: 0)
                        put("floatingManaBlue", pool?.blue ?: 0)
                        put("floatingManaRed", pool?.red ?: 0)
                        put("floatingManaGreen", pool?.green ?: 0)
                        put("floatingManaColorless", pool?.colorless ?: 0)
                        put("opponents", buildJsonObject {
                            opponents.forEach { (id, names) ->
                                put(id, JsonArray(names.map(::JsonPrimitive)))
                            }
                        })
                        put("originalAction", originalAction)
                        put("alternativeAnimarCast", animar["action"] ?: JsonNull)
                        put("forkExecuted", false)
                        put("strategicDefectProven", false)
                    }
                    selectedStates[index] = state
                    Files.writeString(output.resolve("positions.json"),
                        JsonArray(observations).toString())
                    println("IDENTITY_R4_EXACT_WINDOW " + observations.last().toString())
                }
                if (index == 1514) break
                val action = json.decodeFromString<GameAction>(requireNotNull(row.str("action")))
                val processed = processor.process(state, action).result
                check(processed.error == null) {
                    "Archival rules-engine replay rejected action " + index + ": " + processed.error
                }
                val expected = requireNotNull(results[index]) { "No archived result " + index }
                check(expected.str("error") == null) { "Original had error " + index }
                val actualEvents = processed.events.map { it.toString() }
                val recordedEvents = expected.getValue("events").jsonArray.map {
                    it.jsonPrimitive.content
                }
                check(actualEvents == recordedEvents) {
                    Files.writeString(output.resolve("event-divergence.json"), buildJsonObject {
                        put("actionIndex", index)
                        put("actualEventCount", actualEvents.size)
                        put("archivedEventCount", recordedEvents.size)
                        put("firstActual", actualEvents.firstOrNull())
                        put("firstArchived", recordedEvents.firstOrNull())
                    }.toString())
                    "Original engine-event fidelity diverged at action " + index
                }
                val expectedLife = expected.getValue("life").jsonArray.map { it.jsonPrimitive.int }
                val actualLife = state.turnOrder.map { processed.state.lifeTotal(it) }
                check(actualLife == expectedLife) { "Archived life totals diverged at " + index }
                check(processed.state.gameOver == expected.getValue("gameOver").jsonPrimitive.boolean)
                state = processed.state
                replayed++
                exactEvents++
                if (index == 64) {
                    val actualSnapshot = json.parseToJsonElement(json.encodeToString(state))
                    prefixMatches = actualSnapshot == expectedPrefixState
                    check(prefixMatches) {
                        Files.writeString(output.resolve("prefix-parity-failure.txt"),
                            "Actions/events match but the full serialized R22 64-action prefix state does not.")
                        "R22 64-action original full-state prefix fidelity failed"
                    }
                }
                if (index % 200 == 0) {
                    Files.writeString(output.resolve("progress.json"), buildJsonObject {
                        put("archivedActionsExactlyReplayed", replayed)
                        put("lastTurn", state.turnNumber)
                        put("noNewGames", true)
                    }.toString())
                }
            }
        }
        check(replayed == 1513 && exactEvents == 1513 && prefixMatches)
        check(selectedStates.keys == selected)
        val result = buildJsonObject {
            put("status", "EXACT_ORIGINAL_STATE_REPLAY_VERIFIED")
            put("sourceRun", 39435937911L)
            put("sourceHead", "10675d8dc3133294c035606f656f9e4d12d50939")
            put("sourceArtifact", 12271846670L)
            put("seed", "202610191622")
            put("arm", "control")
            put("profile", "manual-transmission-r5-survival-opt-in")
            put("archivedSubmissionsReplayed", replayed)
            put("matchingEngineEventRows", exactEvents)
            put("prefix64FullSerializedStateEqual", prefixMatches)
            put("observedWindows", JsonArray(observations))
            put("futureCounterfactualsRequireSeparateOptInStage", true)
            put("fullGamesInitialized", 0)
            put("AIBehaviorsChanged", 0)
            put("cardsChanged", 0)
            put("defectsProven", 0)
        }
        Files.writeString(output.resolve("result.json"), result.toString())
        println("IDENTITY_R4_EXACT_REPLAY_PASS actions=" + replayed +
            " windows=" + selectedStates.size + " full_prefix_equal=" + prefixMatches)
    }
})
