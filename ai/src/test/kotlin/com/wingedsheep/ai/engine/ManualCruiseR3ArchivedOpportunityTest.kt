package com.wingedsheep.ai.engine

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
import java.util.zip.ZipFile

/**
 * CRUISE R3: ONLY original trace prefix replay; never GameInitializer, seed,
 * future draw, automated opponent passing, policy mutation, or a new full game.
 * Fails closed on original event mismatch, not a 'new' gameplay observation.
 */
class ManualCruiseR3ArchivedOpportunityTest : FunSpec({
    val root = Path.of(requireNotNull(System.getenv("MANUAL_R3_ROOT")))
    val archiveRoot = Path.of(requireNotNull(System.getenv("MANUAL_R3_ARCHIVES")))
    val output = Path.of(requireNotNull(System.getenv("MANUAL_R3_OUTPUT")))
    Files.createDirectories(output)
    val manifest = Json.parseToJsonElement(Files.readString(root.resolve(
        "experiments/manual-transmission/research/cruise-sport-race-20261021/CRUISE_R3_FIXTURES.json"
    ))).jsonObject
    check(manifest["schema"]!!.jsonPrimitive.content ==
        "MANUAL_CRUISE_R3_FROZEN_PREDECLARED_OPPORTUNITY_FIXTURES_V1")
    check(manifest["declared_before_new_replay"]!!.jsonPrimitive.boolean)
    check(manifest["games_used"]!!.jsonPrimitive.int == 10)
    check(manifest["new_full_games_authorized"]!!.jsonPrimitive.int == 0)
    check(manifest["reserved_games"]!!.jsonPrimitive.int == 2)

    fun JsonObject.str(name: String) = requireNotNull(this[name]).jsonPrimitive.content
    fun JsonObject.num(name: String) = requireNotNull(this[name]).jsonPrimitive.int
    fun ZipFile.entryString(path: String): String = getInputStream(
        requireNotNull(getEntry(path)) { "Original archive missing $path" }
    ).bufferedReader().readText()
    fun digest(path: Path) = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }

    val json = Json {
        serializersModule = engineSerializersModule
        allowStructuredMapKeys = true
    }
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }
    val processor = ActionProcessor(registry)
    val simulator = GameSimulator(registry)

    for (item in manifest["fixtures"]!!.jsonArray) {
        val fixture = item.jsonObject
        val id = fixture.str("id")
        val seed = fixture.str("seed")
        val cohort = fixture.str("cohort")
        val arm = fixture.str("arm")
        val target = fixture.num("target_action")
        val actor = EntityId(fixture.str("seat"))
        test("$id: exact archived state before scored Animar decision, no games") {
            check(arm == "control")
            val input = archiveRoot.resolve("$id-original.zip")
            check(Files.isRegularFile(input))
            check(digest(input) == fixture.str("archive_sha256")) {
                "Original archive digest mismatch for $id"
            }
            val prefix = if (cohort == "R1")
                "precision-r1-stage1-output/" else "precision-r6-prospective-output/"
            val sourceSha = if (cohort == "R1")
                "84e6347bbcdfa2990eb304051771007daf849ce9" else
                "53a5162eb3659310b1878894a90ae58c244a6745"
            val gamePrefix = prefix + arm + "-seed-" + seed + "/"
            ZipFile(input.toFile()).use { zip ->
                check(zip.entryString(prefix + "source-sha.txt").trim() == sourceSha)
                val summary = Json.parseToJsonElement(zip.entryString(
                    gamePrefix + "summary.json")).jsonObject
                check(summary.str("profile") == "manual-transmission-r5-survival-opt-in")
                check(summary.str("status") == "COMPLETE")
                check(summary.num("accepted") == summary.num("submitted"))
                check(summary["prefixOnly"]!!.jsonPrimitive.boolean == false)
                var state = json.decodeFromString<GameState>(zip.entryString(
                    gamePrefix + "initial-state.json"))
                val allActions = zip.entryString(gamePrefix + "actions.jsonl")
                    .lineSequence().filter(String::isNotBlank)
                    .map { json.parseToJsonElement(it).jsonObject }.toList()
                check(allActions.size == summary.num("accepted") * 2)
                val submissions = allActions.filter { it.str("record") == "submit" }
                val results = allActions.filter { it.str("record") == "result" }
                check(submissions.size == results.size)
                val insights = zip.entryString(gamePrefix + "decisions.jsonl")
                    .lineSequence().filter(String::isNotBlank)
                    .map { json.parseToJsonElement(it).jsonObject }.toList()

                // Re-apply ONLY prior accepted historical actions. Detect a code
                // version or source behavior divergence immediately; do not repair
                // the historical trace or substitute a new simulation.
                for (index in 1 until target) {
                    val original = submissions[index - 1]
                    val reference = results[index - 1]
                    check(original.num("index") == index)
                    check(reference.num("index") == index)
                    val action = json.decodeFromString<GameAction>(original.str("action"))
                    val processed = processor.process(state, action).result
                    check(processed.error == null) {
                        "$id original historical replay rejected action $index: ${processed.error}"
                    }
                    val expectedEvents = reference["events"]!!.jsonArray.map {
                        it.jsonPrimitive.content
                    }
                    val actualEvents = processed.events.map { it.toString() }
                    check(actualEvents == expectedEvents) {
                        "$id original event stream diverged at index $index; HOLD source drift"
                    }
                    val actualLife = state.turnOrder.map { processed.state.lifeTotal(it) }
                    val expectedLife = reference["life"]!!.jsonArray.map {
                        it.jsonPrimitive.int
                    }
                    check(actualLife == expectedLife) {
                        "$id historical life diverged at index $index; HOLD"
                    }
                    state = processed.state
                }
                val submission = submissions[target - 1]
                val originalChosenAction = json.decodeFromString<GameAction>(
                    submission.str("action"))
                check(submission.num("index") == target)
                check(submission.str("actor") == actor.value)
                check(state.turnNumber == submission.num("turn"))
                check(state.pendingDecision == null)
                check(state.priorityPlayerId == actor)
                check(submission.str("sourceName") == fixture.str("historical_choice"))
                val choiceJson = Json.parseToJsonElement(submission.str("action"))
                val matching = insights.filter { d ->
                    d.str("kind") == "PRIORITY" &&
                    d.str("playerId") == actor.value &&
                    d.num("turnNumber") == state.turnNumber &&
                    d.str("step") == submission.str("step") &&
                    d["options"]!!.jsonArray.any { o ->
                        val j = o.jsonObject
                        j["chosen"]?.jsonPrimitive?.booleanOrNull == true &&
                            j["action"] == choiceJson
                    }
                }
                check(matching.size == 1) {
                    "$id original AI decision at target index cannot be identified uniquely"
                }
                val insight = matching.single()
                val chosenOption = insight["options"]!!.jsonArray
                    .map { it.jsonObject }.single { o ->
                        o["chosen"]?.jsonPrimitive?.booleanOrNull == true
                    }
                val commanderOption = insight["options"]!!.jsonArray
                    .map { it.jsonObject }.single { o ->
                        o["cardName"]?.jsonPrimitive?.contentOrNull ==
                            "Animar, Soul of Elements" &&
                        o["actionType"]?.jsonPrimitive?.contentOrNull == "CastSpell"
                    }
                val historicalCommand = json.decodeFromString<GameAction>(
                    commanderOption["action"].toString()) as CastSpell
                val actualCommandZone = state.getZone(ZoneKey(actor, Zone.COMMAND))
                check(historicalCommand.cardId in actualCommandZone)
                val actualLegal = simulator.getLegalActions(state, actor)
                val independentOffer = actualLegal.filter { option ->
                    option.affordable &&
                        (option.action as? CastSpell)?.cardId == historicalCommand.cardId
                }
                check(independentOffer.isNotEmpty()) {
                    "$id R9-source independent legal enumerator did not find original commander; HOLD"
                }
                // One cast ACTION only. Do NOT auto-pass opponents or assert a
                // resolved battlefield outcome or competitive improvement.
                val alternative = processor.process(state, historicalCommand).result
                check(alternative.error == null) {
                    "$id independent immediate Animar cast rejected: ${alternative.error}"
                }
                val original = processor.process(state, originalChosenAction).result
                check(original.error == null)
                val expectedChosenEvents = results[target - 1]["events"]!!.jsonArray.map {
                    it.jsonPrimitive.content
                }
                check(original.events.map { it.toString() } == expectedChosenEvents)
                val currentR5 = AIPlayer.create(
                    registry, actor, ManualTransmissionR5Candidate.profile
                ).chooseAction(state)
                val out = buildJsonObject {
                    put("fixture", id)
                    put("seed", seed)
                    put("originalHistoricalSourceSHA", sourceSha)
                    put("targetActionIndex", target)
                    put("historicalChoice", insight.str("chosenLabel"))
                    put("historicalChoiceScore", chosenOption["score"]!!)
                    put("originalAnimarScore", commanderOption["score"]!!)
                    put("originalScoreMargin", chosenOption["score"]!!.jsonPrimitive.double -
                        commanderOption["score"]!!.jsonPrimitive.double)
                    put("exactPriorActionAndEventReplays", target - 1)
                    put("originalCommanderInCommandZone", true)
                    put("independentCurrentSourceAffordableCommander", true)
                    put("originalActionSingleStepReproduced", true)
                    put("independentCommanderCastOneStepAccepted", true)
                    put("immediateCastStackSize", alternative.state.stack.size)
                    put("immediateCastNextPriority",
                        alternative.state.priorityPlayerId?.value ?: "none")
                    put("currentR5ActionType", currentR5::class.simpleName ?: "unknown")
                    put("currentR5MatchesOriginal",
                        json.encodeToString<GameAction>(currentR5) ==
                        json.encodeToString<GameAction>(originalChosenAction))
                    put("playedOpponentResponses", false)
                    put("newFullGames", 0)
                    put("promotedPolicy", false)
                    put("strategicDefectProven", false)
                    put("disposition", "LEGAL_OPPORTUNITY_VERIFIED_NOT_STRATEGIC_SUPERIORITY")
                }
                Files.writeString(output.resolve("$id.json"), out.toString() + "\n")
                println("CRUISE_R3_EXACT_WITNESS=" + out)
            }
        }
    }
})
