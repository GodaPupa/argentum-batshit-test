package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
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
 * Source-bound R8 state reconstruction (NOT a new game). Exact archival submissions
 * are replayed only until before action 1998. Opponents are never forced to pass.
 * The rules engine, not a baseline score, judges no-attack and commander legality.
 */
class ManualR15ExactDecisionReplayTest : FunSpec({
    test("reconstruct four R8 pre-action positions; classify combat legality and Animar casts") {
        val out = Path.of(requireNotNull(System.getenv("MANUAL_R15_OUTPUT")))
        val controlFile = Path.of(requireNotNull(System.getenv("MANUAL_R15_R8_1612_R5")))
        val siblingFile = Path.of(requireNotNull(System.getenv("MANUAL_R15_R8_1612_R7")))
        Files.createDirectories(out)
        val requested = setOf(1336, 1497, 1701, 1998)
        val r8Source = "d226f1a56eebab1800b85a06bf70617ba439a00b"
        val initialSha = "342ac135199550a95d64bf3b356c8f6a41aebef3f749b35f4e840504216efee0"
        fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        sha(Files.readAllBytes(controlFile)) shouldBe
            "4f823fb61ac29ecc84582b4fd974200d2ba1d89664d906df802013d823f026a9"
        sha(Files.readAllBytes(siblingFile)) shouldBe
            "7ca290085e874cf4eaeee766224ba797d0dd9c5ab37d0b5cd72b04fc386c622c"
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val processor = ActionProcessor(registry)
        val sim = GameSimulator(registry)
        fun JsonObject.value(k: String) = this[k]?.jsonPrimitive?.contentOrNull
        fun JsonObject.intValue(k: String) = this[k]?.jsonPrimitive?.intOrNull
        data class Observed(val state: GameState, val original: GameAction,
            val decision: JsonObject, val index: Int)
        val originals = linkedMapOf<Int, Observed>()
        val abilityBindings = mutableListOf<JsonElement>()
        var replayed = 0
        var exactEvents = 0
        var discrepantEvents = 0

        ZipFile(controlFile.toFile()).use { control ->
            ZipFile(siblingFile.toFile()).use { sibling ->
                fun read(zip: ZipFile, name: String) = zip.getInputStream(
                    requireNotNull(zip.getEntry(name)) { "Missing accepted original entry: $name" }
                ).bufferedReader().readText()
                read(control, "r8-output/source-sha.txt").trim() shouldBe r8Source
                read(sibling, "r8-output/source-sha.txt").trim() shouldBe r8Source
                val prefix = "r8-output/r5-seed-202610191612/"
                val mirrorPrefix = "r8-output/r7-seed-202610191612/"
                val initialText = read(control, prefix + "initial-state.json")
                val siblingInitial = read(sibling, mirrorPrefix + "initial-state.json")
                sha(initialText.toByteArray()) shouldBe initialSha
                siblingInitial shouldBe initialText
                val archived = read(control, prefix + "actions.jsonl").lineSequence()
                    .filter(String::isNotBlank)
                    .map { json.parseToJsonElement(it).jsonObject }.toList()
                val twin = read(sibling, mirrorPrefix + "actions.jsonl").lineSequence()
                    .filter(String::isNotBlank)
                    .map { json.parseToJsonElement(it).jsonObject }.toList()
                val originalPrefix = archived.filter { (it.intValue("index") ?: 0) <= 1998 }
                val twinPrefix = twin.filter { (it.intValue("index") ?: 0) <= 1998 }
                originalPrefix shouldBe twinPrefix  // whole first 1998 decisions and engine results
                val decisions = read(control, prefix + "decisions.jsonl").lineSequence()
                    .filter(String::isNotBlank)
                    .map { json.parseToJsonElement(it).jsonObject }.toList()
                val submissions = archived.filter { it.value("record") == "submit" }
                val resultByIndex = archived.filter { it.value("record") == "result" }
                    .associateBy { it.intValue("index") }
                var state = json.decodeFromString<GameState>(initialText)
                for (row in submissions) {
                    val index = requireNotNull(row.intValue("index"))
                    check(index == replayed + 1) { "Non-contiguous original at $index" }
                    val archivedAction = json.decodeFromString<GameAction>(
                        requireNotNull(row.value("action")))
                    if (index in requested) {
                        val seat = if (index == 1701) "manual-mirror-3" else "manual-mirror-0"
                        row.value("actor") shouldBe seat
                        state.turnNumber shouldBe row.intValue("turn")
                        state.step.name shouldBe row.value("step")
                        val candidates = decisions.filter { d ->
                            d.value("playerId") == seat &&
                                d.intValue("turnNumber") == state.turnNumber &&
                                d.value("step") == state.step.name &&
                                (d["options"] as? JsonArray)?.any { option ->
                                    val o = option.jsonObject
                                    o["chosen"]?.jsonPrimitive?.booleanOrNull == true &&
                                        o["action"] == json.parseToJsonElement(
                                            requireNotNull(row.value("action")))
                                } == true
                        }
                        check(candidates.size == 1) {
                            "No unique chosen decision for accepted R8 action $index"
                        }
                        val before = out.resolve("state-before-$index.json")
                        Files.writeString(before, json.encodeToString(state))
                        originals[index] = Observed(state, archivedAction, candidates.single(), index)
                        Files.writeString(out.resolve("insight-before-$index.json"),
                            candidates.single().toString())
                    }
                    if (index == 1998) break

                    // The historical rules engine could accept a target-bound activated ability
                    // not materialized by its generic AI enumerator. Rebind only a unique live
                    // ability ID; otherwise submit the archived action unchanged and demand
                    // authoritative ActionProcessor acceptance, as in validated R7 replay.
                    val submitted = if (archivedAction is ActivateAbility) {
                        val offered = sim.getLegalActions(state, archivedAction.playerId)
                            .filter { it.affordable &&
                                (archivedAction.targets.isEmpty() || it.requiresTargets) }
                            .mapNotNull { it.action as? ActivateAbility }
                            .filter { it.sourceId == archivedAction.sourceId }
                            .map { it.abilityId }.distinct()
                        if (archivedAction.abilityId !in offered && offered.size == 1) {
                            abilityBindings += buildJsonObject {
                                put("index", index)
                                put("source", archivedAction.sourceId.value)
                                put("old", archivedAction.abilityId.value)
                                put("new", offered.single().value)
                            }
                            archivedAction.copy(abilityId = offered.single())
                        } else archivedAction
                    } else archivedAction
                    val result = processor.process(state, submitted).result
                    check(result.error == null) {
                        "Archived R8 replay rejected action $index: " + result.error
                    }
                    val expected = requireNotNull(resultByIndex[index])
                    val originalLife = expected["life"]?.jsonArray?.map { it.jsonPrimitive.int }
                    val replayLife = state.turnOrder.map { result.state.lifeTotal(it) }
                    replayLife shouldBe originalLife
                    result.state.gameOver shouldBe expected["gameOver"]?.jsonPrimitive?.boolean
                    val archivedEvents = expected["events"]?.jsonArray?.map { it.jsonPrimitive.content }
                    if (result.events.map { it.toString() } == archivedEvents) exactEvents++
                    else discrepantEvents++
                    state = result.state
                    replayed++
                    if (replayed % 200 == 0) Files.writeString(
                        out.resolve("replay-progress.json"), buildJsonObject {
                            put("acceptedArchivedActions", replayed)
                            put("lastGlobalTurn", state.turnNumber)
                            put("sourceArchive", "12228089328")
                        }.toString())
                }
            }
        }
        replayed shouldBe 1997
        originals.keys shouldBe requested.sorted().toSet()
        Files.writeString(out.resolve("ability-bindings.json"), JsonArray(abilityBindings).toString())
        val observations = mutableListOf<JsonElement>()
        var provenDefects = 0
        val combat = originals.getValue(1701)
        val combatPlayer = EntityId("manual-mirror-3")
        combat.original is DeclareAttackers shouldBe true
        val legalAttack = sim.getLegalActions(combat.state, combatPlayer)
            .single { it.action is DeclareAttackers }
        val mandatory = legalAttack.mandatoryAttackers.orEmpty()
        val empty = DeclareAttackers(combatPlayer, emptyMap())
        val emptyResult = processor.process(combat.state, empty).result
        val actualAttack = combat.original as DeclareAttackers
        val actualResult = processor.process(combat.state, actualAttack).result
        check(actualResult.error == null)
        val noAttack = (combat.decision["options"] as JsonArray)
            .map { it.jsonObject }.single { it["baseline"]?.jsonPrimitive?.booleanOrNull == true }
        val selectedAttack = (combat.decision["options"] as JsonArray)
            .map { it.jsonObject }.single { it["chosen"]?.jsonPrimitive?.booleanOrNull == true }
        val forcedNames = mandatory.map { id ->
            combat.state.getEntity(id)?.get<CardComponent>()?.name ?: id.value
        }
        observations += buildJsonObject {
            put("index", 1701)
            put("lane", "COMBAT_LEGALITY")
            put("actor", combatPlayer.value)
            put("mandatoryAttackers", JsonArray(mandatory.map { JsonPrimitive(it.value) }))
            put("mandatoryAttackerNames", JsonArray(forcedNames.map(::JsonPrimitive)))
            put("validAttackers", legalAttack.validAttackers?.size)
            put("archivedAttackerCount", actualAttack.attackers.size)
            put("actualPlanEngineAccepted", true)
            put("noAttackEngineAccepted", emptyResult.error == null)
            put("noAttackEngineError", emptyResult.error?.toString())
            put("noAttacksReferenceScore", noAttack["score"])
            put("chosenPlanScore", selectedAttack["score"])
            put("sourceState", "state-before-1701.json")
            put("defectProven", false)
        }
        println("R15_COMBAT_LEGALITY=" + observations.last())

        val actor = EntityId("manual-mirror-0")
        for (index in listOf(1336, 1497, 1998)) {
            val window = originals.getValue(index)
            window.original is PassPriority shouldBe true
            val offered = sim.getLegalActions(window.state, actor).filter {
                it.affordable &&
                    (it.action as? CastSpell)?.cardId?.let { id ->
                        window.state.getEntity(id)?.get<CardComponent>()?.name ==
                            "Animar, Soul of Elements"
                    } == true
            }
            check(offered.isNotEmpty()) { "No affordable Animar in archived decision $index" }
            val options = (window.decision["options"] as JsonArray).map { it.jsonObject }
            val pass = options.single { it["chosen"]?.jsonPrimitive?.booleanOrNull == true }
            val animar = options.single {
                it["cardName"]?.jsonPrimitive?.contentOrNull == "Animar, Soul of Elements" &&
                    it["actionType"]?.jsonPrimitive?.contentOrNull == "CastSpell"
            }
            val cast = json.decodeFromString<GameAction>(animar["action"].toString())
            cast is CastSpell shouldBe true
            val castResult = processor.process(window.state, cast).result
            val passResult = processor.process(window.state, PassPriority(actor)).result
            check(castResult.error == null && passResult.error == null) {
                "Archived Animar alternative not authoritative legal at action " + index
            }
            val creatureCount = window.state.controlledBattlefield(actor).count {
                window.state.getEntity(it)?.get<CardComponent>()?.isCreature == true
            }
            val landCount = window.state.controlledBattlefield(actor).count {
                window.state.getEntity(it)?.get<CardComponent>()?.isLand == true
            }
            val untappedLands = window.state.controlledBattlefield(actor).count {
                window.state.getEntity(it)?.get<CardComponent>()?.isLand == true &&
                    window.state.getEntity(it)?.has<TappedComponent>() != true
            }
            val commander = window.state.getZone(ZoneKey(actor, Zone.COMMAND))
                .firstOrNull { window.state.getEntity(it)?.get<CardComponent>()?.name ==
                    "Animar, Soul of Elements" }
            val priorCasts = commander?.let { id ->
                window.state.getEntity(id)?.get<CommanderComponent>()?.castsFromCommandZone
            }
            val threats = window.state.getOpponents(actor).associate { opp ->
                opp.value to window.state.controlledBattlefield(opp).count {
                    window.state.getEntity(it)?.get<CardComponent>()?.isCreature == true
                }
            }
            val castScore = animar["score"]?.jsonPrimitive?.doubleOrNull
            val passScore = pass["score"]?.jsonPrimitive?.doubleOrNull
            check(castScore != null && passScore != null && passScore > castScore)
            observations += buildJsonObject {
                put("index", index)
                put("lane", "ANIMAR_CAST_VS_PASS")
                put("actor", actor.value)
                put("globalTurn", window.state.turnNumber)
                put("priorityStep", window.state.step.name)
                put("life", window.state.lifeTotal(actor))
                put("manaCostOffer", offered.first().manaCostString)
                put("commanderTaxCasts", priorCasts)
                put("controlledCreatures", creatureCount)
                put("controlledLands", landCount)
                put("untappedLands", untappedLands)
                put("opponentCreatures", buildJsonObject {
                    threats.forEach { (id, count) -> put(id, count) }
                })
                put("castEngineAccepted", true)
                put("passEngineAccepted", true)
                put("castVsPassScoreGap", passScore - castScore)
                put("castScore", castScore)
                put("passScore", passScore)
                put("sourceState", "state-before-$index.json")
                put("alternativeAction", animar["action"])
                put("defectProven", false)
                put("conclusion", "Both real engine actions legal; one-ply pass scored higher; no causal improvement established")
            }
            println("R15_ANIMAR_DECISION=" + observations.last())
        }
        val summary = buildJsonObject {
            put("status", "REPLAY_VALIDATED")
            put("sourceRun", 39340369095L)
            put("seed", "202610191612")
            put("r5Artifact", "12228089328")
            put("r7Artifact", "12229530628")
            put("sourceSha", r8Source)
            put("initialGameStateSha256", initialSha)
            put("historicalActionsReplayed", replayed)
            put("matchingLifeAndTerminalRecords", replayed)
            put("matchingEngineEventStrings", exactEvents)
            put("nonmatchingEventStrings", discrepantEvents)
            put("archivedAbilityIdRebindings", abilityBindings.size)
            put("observedWindows", JsonArray(observations))
            put("defectsProven", provenDefects)
            put("newFullCommanderGames", 0)
            put("productionPolicyChanges", 0)
        }
        Files.writeString(out.resolve("r15-replay-summary.json"), summary.toString())
        println("R15_EXACT_REPLAY_RESULT=" + summary)
        observations.size shouldBe 4
    }
})
