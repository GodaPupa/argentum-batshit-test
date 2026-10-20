package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.ai.engine.advisor.modules.ManualAnimarDevelopmentAdvisor
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.CounterType
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
import kotlin.math.abs

/**
 * Two original R8 productive Animar windows and three R16 negative controls.
 * Source-bound prefix replay only: NO GameInitializer, new seed, full-game run,
 * opponent-scripted pass, hidden-card prediction or production AI policy change.
 */
class ManualR17AnimarProductiveWitnessSearchTest : FunSpec({
    test("original Animar deployment with real hand creature followups, and R16 zero-hand controls") {
        val output = Path.of(requireNotNull(System.getenv("MANUAL_R17_OUTPUT")))
        Files.createDirectories(output)
        val r8Archive = Path.of(requireNotNull(System.getenv("MANUAL_R17_R8_ARCHIVE")))
        val r15Archive = Path.of(requireNotNull(System.getenv("MANUAL_R17_R15_ARCHIVE")))
        val r16Archive = Path.of(requireNotNull(System.getenv("MANUAL_R17_R16_ARCHIVE")))
        fun digest(path: Path): String = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
        digest(r8Archive) shouldBe
            "4f823fb61ac29ecc84582b4fd974200d2ba1d89664d906df802013d823f026a9"
        digest(r15Archive) shouldBe
            "251dc4cd30d955bbee3fef6172ca285664df78923859efa258299e5cd3fe7a5f"
        digest(r16Archive) shouldBe
            "425f0ca27da8efb50738f127e1ef378027fa9fd88611f1cf72698be88f4cf1c2"
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val processor = ActionProcessor(registry)
        val sim = GameSimulator(registry)
        val evaluator = AIPlayer.defaultEvaluator()
        fun JsonObject.text(key: String) = this[key]?.jsonPrimitive?.contentOrNull
        fun JsonObject.number(key: String) = this[key]?.jsonPrimitive?.intOrNull
        fun JsonObject.real(key: String) = this[key]?.jsonPrimitive?.doubleOrNull
        fun ZipFile.entryText(path: String): String =
            getInputStream(requireNotNull(getEntry(path)) { "Missing original entry: $path" })
                .bufferedReader().readText()
        fun creatureHand(state: GameState, seat: EntityId): Set<EntityId> =
            state.getZone(ZoneKey(seat, Zone.HAND)).filter { id ->
                state.getEntity(id)?.get<CardComponent>()?.typeLine?.isCreature == true
            }.toSet()

        data class Witness(val index: Int, val seat: String, val handCreature: String,
                           val followupIndex: Int, val originalCredit: Double)
        val targets = listOf(
            Witness(930, "manual-mirror-1", "Eternal Witness", 935, 2.4225),
            Witness(1613, "manual-mirror-2", "Imperial Recruiter", 1618, 2.6025),
        )
        val originalRows = mutableListOf<JsonElement>()
        var replayed = 0
        var exactEvents = 0
        ZipFile(r8Archive.toFile()).use { zip ->
            val prefix = "r8-output/r5-seed-202610191612/"
            zip.entryText("r8-output/source-sha.txt").trim() shouldBe
                "d226f1a56eebab1800b85a06bf70617ba439a00b"
            val originalStateText = zip.entryText(prefix + "initial-state.json")
            MessageDigest.getInstance("SHA-256").digest(originalStateText.toByteArray())
                .joinToString("") { "%02x".format(it) } shouldBe
                    "342ac135199550a95d64bf3b356c8f6a41aebef3f749b35f4e840504216efee0"
            var state = json.decodeFromString<GameState>(originalStateText)
            val actionRows = zip.entryText(prefix + "actions.jsonl").lineSequence()
                .filter(String::isNotBlank).map { json.parseToJsonElement(it).jsonObject }.toList()
            val acceptedSubmits = actionRows.filter { it.text("record") == "submit" }
            val originalResults = actionRows.filter { it.text("record") == "result" }
                .associateBy { it.number("index") }
            val decisions = zip.entryText(prefix + "decisions.jsonl").lineSequence()
                .filter(String::isNotBlank).map { json.parseToJsonElement(it).jsonObject }.toList()
            for (record in acceptedSubmits) {
                val index = requireNotNull(record.number("index"))
                check(index == replayed + 1) { "Original R8 index gap at $index" }
                val action = json.decodeFromString<GameAction>(
                    requireNotNull(record.text("action")))
                val witness = targets.firstOrNull { it.index == index }
                if (witness != null) {
                    val seat = EntityId(witness.seat)
                    record.text("actor") shouldBe witness.seat
                    (action is CastSpell) shouldBe true
                    val cmd = action as CastSpell
                    state.getEntity(cmd.cardId)?.get<CardComponent>()?.name shouldBe
                        "Animar, Soul of Elements"
                    state.turnNumber shouldBe record.number("turn")
                    val scoreInsight = decisions.single { insight ->
                        insight.text("playerId") == witness.seat &&
                            insight.number("turnNumber") == state.turnNumber &&
                            insight.text("step") == state.step.name &&
                            insight["options"]!!.jsonArray.any { o ->
                                val option = o.jsonObject
                                option["chosen"]?.jsonPrimitive?.booleanOrNull == true &&
                                    option["action"] == json.parseToJsonElement(
                                        requireNotNull(record.text("action")))
                            }
                    }
                    val chosen = scoreInsight["options"]!!.jsonArray
                        .map { it.jsonObject }.single {
                            it["chosen"]?.jsonPrimitive?.booleanOrNull == true
                        }
                    chosen.text("cardName") shouldBe "Animar, Soul of Elements"
                    val scored = requireNotNull(chosen.real("score"))
                    val raw = requireNotNull(chosen.real("rawScore"))
                    val gap = scored - raw
                    check(abs(gap - witness.originalCredit) < 1e-7) {
                        "Archive's Animar credit changed at $index: $gap"
                    }
                    val beforeHand = creatureHand(state, seat)
                    check(beforeHand.isNotEmpty()) {
                        "No actual hand creatures at productive witness $index"
                    }
                    val legalCmd = sim.getLegalActions(state, seat).single {
                        it.affordable && (it.action as? CastSpell)?.cardId == cmd.cardId
                    }.copy(action = cmd)
                    val before = sim.getLegalActions(state, seat).filter {
                        val f = it.action as? CastSpell
                        f != null && f.cardId in beforeHand && it.affordable &&
                            !it.requiresTargets && !it.hasXCost
                    }.associateBy { (it.action as CastSpell).cardId }
                    val quiet = sim.simulate(state, cmd) as? SimulationResult.Terminal
                        ?: error("Productive commander cast stopped before quiet state $index")
                    val after = quiet.state
                    check(cmd.cardId in after.getZone(ZoneKey(seat, Zone.BATTLEFIELD)))
                    val deployed = evaluator.evaluate(after, after.projectedState, seat)
                    val originalValue = evaluator.evaluate(state, state.projectedState, seat)
                    val initialCounters = after.getEntity(cmd.cardId)?.get<CountersComponent>()
                        ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
                    val with = sim.getLegalActions(after, seat).filter {
                        val c = it.action as? CastSpell
                        c != null && c.cardId in beforeHand && it.affordable &&
                            !it.requiresTargets && !it.hasXCost
                    }
                    val followupDetails = mutableListOf<JsonElement>()
                    var best = 0.0
                    for (f in with.take(12)) {
                        val id = (f.action as CastSpell).cardId
                        val plain = before[id] ?: continue
                        val withResult = sim.simulate(after, f.action) as? SimulationResult.Terminal
                            ?: continue
                        val withoutResult = sim.simulate(state, plain.action) as? SimulationResult.Terminal
                            ?: continue
                        if (withResult.state.gameOver || withoutResult.state.gameOver) continue
                        val counters = withResult.state.getEntity(cmd.cardId)?.get<CountersComponent>()
                            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
                        if (counters <= initialCounters) continue
                        val withGain = evaluator.evaluate(withResult.state,
                            withResult.state.projectedState, seat) - deployed
                        if (!withGain.isFinite() || withGain <= 0.0) continue
                        val withoutGain = evaluator.evaluate(withoutResult.state,
                            withoutResult.state.projectedState, seat) - originalValue
                        val incremental = withGain - withoutGain
                        if (incremental.isFinite() && incremental > best) best = incremental
                        followupDetails += buildJsonObject {
                            put("name", state.getEntity(id)?.get<CardComponent>()?.name)
                            put("cardId", id.value)
                            put("animarCountersAfterFollowup", counters)
                            put("valueAfterDeploymentAndFollowup", withGain)
                            put("ordinaryCreatureOnlyValue", withoutGain)
                            put("incrementalCredit", incremental)
                        }
                    }
                    val advisor = ManualAnimarDevelopmentAdvisor.evaluateCast(
                        CastContext(state, state.projectedState, seat, legalCmd,
                            requireNotNull(scoreInsight.real("baselineScore")),
                            raw, evaluator, sim)
                    )
                    check(advisor != null && best > 0.0) {
                        "Existing advisor unexpectedly omitted measured productive witness $index"
                    }
                    check(abs((advisor - raw) - best) < 1e-7 &&
                        abs(advisor - scored) < 1e-7) {
                        "Measured value and original AI score disagree: $index"
                    }
                    val historicalFollowup = acceptedSubmits.first {
                        it.number("index") == witness.followupIndex
                    }
                    historicalFollowup.text("sourceName") shouldBe witness.handCreature
                    historicalFollowup.text("actor") shouldBe witness.seat
                    historicalFollowup.number("turn") shouldBe state.turnNumber
                    val recordOut = buildJsonObject {
                        put("index", index)
                        put("seed", "202610191612")
                        put("seat", seat.value)
                        put("globalTurn", state.turnNumber)
                        put("historicalAction", "Cast Animar, Soul of Elements")
                        put("historicalSameTurnFollowup", witness.handCreature)
                        put("historicalFollowupActionIndex", witness.followupIndex)
                        put("creatureCardsInOriginalHand", JsonArray(beforeHand.map { id ->
                            JsonPrimitive(state.getEntity(id)?.get<CardComponent>()?.name ?: id.value)
                        }))
                        put("plainAffordableFollowups", before.size)
                        put("postAnimarAffordableFollowups", with.size)
                        put("measuredFollowupOutcomes", JsonArray(followupDetails))
                        put("rawCastScore", raw)
                        put("originalAdvisorAdjustedScore", scored)
                        put("originalAdvisorCredit", gap)
                        put("recomputedBestIncrementalCredit", best)
                        put("recomputedAdvisorScore", advisor)
                        put("commanderInitialCounters", initialCounters)
                        put("manualPolicyChanged", false)
                        put("defectProven", false)
                        put("simulatorCaveat", "Simulated stack resolution autopasses opponent priority; historical cast sequence independently witnessed normal opponent choices")
                    }
                    originalRows += recordOut
                    Files.writeString(output.resolve("r17-positive-state-before-$index.json"),
                        json.encodeToString(state))
                    Files.writeString(output.resolve("r17-productive-witnesses.json"),
                        JsonArray(originalRows).toString())
                    println("R17_PRODUCTIVE_WITNESS=" + recordOut)
                }
                if (index == 1613) break
                val processed = processor.process(state, action).result
                check(processed.error == null) {
                    "Original R8 replay rejected action $index: " + processed.error
                }
                val expected = requireNotNull(originalResults[index])
                val referenceEvents = expected["events"]!!.jsonArray.map {
                    it.jsonPrimitive.content
                }
                val actualEvents = processed.events.map { it.toString() }
                check(actualEvents == referenceEvents) {
                    "Original event replay diverged at action $index"
                }
                val expectedLife = expected["life"]!!.jsonArray.map { it.jsonPrimitive.int }
                val currentLife = state.turnOrder.map { processed.state.lifeTotal(it) }
                currentLife shouldBe expectedLife
                state = processed.state
                exactEvents++
                replayed++
            }
        }
        replayed shouldBe 1612
        originalRows.size shouldBe 2

        // Exact R16 no-hand-creature states are retained as negative controls.
        val negativeRows = mutableListOf<JsonElement>()
        ZipFile(r15Archive.toFile()).use { z ->
            ZipFile(r16Archive.toFile()).use { older ->
                fun readR15(i: Int, kind: String): String = z.entryText(
                    "r15-output/" + kind + "-before-$i.json")
                val originalNegatives = json.parseToJsonElement(
                    older.entryText("r16-output/r16-valuation.json")).jsonArray.map { it.jsonObject }
                for (index in listOf(1336, 1497, 1998)) {
                    val state = json.decodeFromString<GameState>(readR15(index,"state"))
                    val insight = json.parseToJsonElement(readR15(index,"insight")).jsonObject
                    val seat = EntityId("manual-mirror-0")
                    val creatures = creatureHand(state, seat)
                    creatures.size shouldBe 0
                    val animar = insight["options"]!!.jsonArray.map { it.jsonObject }.single {
                        it.text("cardName") == "Animar, Soul of Elements" &&
                            it.text("actionType") == "CastSpell"
                    }
                    val cast = json.decodeFromString<GameAction>(
                        requireNotNull(animar["action"]).toString()) as CastSpell
                    val legal = sim.getLegalActions(state,seat).single {
                        it.affordable && (it.action as? CastSpell)?.cardId == cast.cardId
                    }.copy(action=cast)
                    val chosen = insight["options"]!!.jsonArray.map { it.jsonObject }.single {
                        it["chosen"]?.jsonPrimitive?.booleanOrNull == true
                    }
                    val advisor = ManualAnimarDevelopmentAdvisor.evaluateCast(
                        CastContext(state, state.projectedState, seat, legal,
                            requireNotNull(chosen.real("score")),
                            requireNotNull(animar.real("score")), evaluator, sim)
                    )
                    advisor shouldBe null
                    val r16 = originalNegatives.single { it.number("actionIndex")==index }
                    r16.number("handCreatureCount") shouldBe 0
                    r16["originalAdvisorWouldAddDevelopmentCredit"]?.jsonPrimitive?.boolean shouldBe false
                    negativeRows += buildJsonObject {
                        put("index", index)
                        put("handCreatureCount", creatures.size)
                        put("advisorOverride", JsonNull)
                        put("originalR16Decision", "PassPriority")
                        put("verifiedNegativeControl", true)
                    }
                }
            }
        }
        Files.writeString(output.resolve("r17-negative-controls.json"),
            JsonArray(negativeRows).toString())
        val summary = buildJsonObject {
            put("status", "PRODUCTIVE_WITNESSES_VALIDATED")
            put("positiveWitnessCount", originalRows.size)
            put("negativeControlCount", negativeRows.size)
            put("sourceR8Run", 39340369095L)
            put("sourceR8Seed", "202610191612")
            put("sourceR8R5Artifact", 12228089328L)
            put("historicalActionsReplayed", replayed)
            put("exactOriginalEventMatches", exactEvents)
            put("newFullCommanderGames", 0)
            put("productionPolicyChanges", 0)
            put("defectsProven", 0)
            put("conclusion", "NO_PROVEN_DEFECT")
        }
        Files.writeString(output.resolve("r17-summary.json"),summary.toString())
        println("R17_PRODUCTIVE_SEARCH_RESULT=" + summary)
    }
})
