package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.ai.engine.advisor.modules.ManualAnimarDevelopmentAdvisor
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Exact R15 original checkpoints, no game initializer and no new Commander game.
 * Tests existing advisor, not production scoring modifications. We do NOT assume
 * GameSimulator's automatic opponent priority passes prove the cast survives
 * normal strategic opponent responses.
 */
class ManualR16AnimarValuationTest : FunSpec({
    test("source-bound Animar same-turn development credit for 1336, 1497 and 1998") {
        val archive = Path.of(requireNotNull(System.getenv("MANUAL_R16_R15_ARCHIVE")))
        val output = Path.of(requireNotNull(System.getenv("MANUAL_R16_OUTPUT")))
        Files.createDirectories(output)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(archive)).joinToString("") { "%02x".format(it) }
        digest shouldBe "251dc4cd30d955bbee3fef6172ca285664df78923859efa258299e5cd3fe7a5f"
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val processor = ActionProcessor(registry)
        val simulator = GameSimulator(registry)
        val evaluator = AIPlayer.defaultEvaluator()
        val actor = EntityId("manual-mirror-0")
        val expectedHashes = mapOf(
            1336 to "9fed665708996ce0468b87337bc2924a09e748acaee41dd5877a74a4f537ce1f",
            1497 to "0a8d7182119fd3c7e19f6e02092179131b9fcfdb50d447e8b1e44c794584dffe",
            1998 to "f309851969bdfd5c51c08a89e3dc306840b357ed1c8aebf95039581e947408db"
        )
        val results = mutableListOf<JsonElement>()
        ZipFile(archive.toFile()).use { zip ->
            fun read(name: String): String = zip.getInputStream(
                requireNotNull(zip.getEntry("r15-output/" + name)) { "Missing original R15: " + name }
            ).bufferedReader().readText()
            for (index in listOf(1336, 1497, 1998)) {
                val serialized = read("state-before-$index.json")
                MessageDigest.getInstance("SHA-256").digest(serialized.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) } shouldBe expectedHashes.getValue(index)
                val state = json.decodeFromString<GameState>(serialized)
                val insight = json.parseToJsonElement(read("insight-before-$index.json")).jsonObject
                val options = insight.getValue("options").jsonArray.map { it.jsonObject }
                val chosen = options.single {
                    it["chosen"]?.jsonPrimitive?.booleanOrNull == true
                }
                chosen["action"]?.jsonObject?.get("type")?.jsonPrimitive?.content shouldBe
                    "PassPriority"
                val animar = options.single {
                    it["cardName"]?.jsonPrimitive?.contentOrNull == "Animar, Soul of Elements" &&
                        it["actionType"]?.jsonPrimitive?.contentOrNull == "CastSpell"
                }
                val archived = json.decodeFromString<GameAction>(
                    requireNotNull(animar["action"]).toString()
                ) as CastSpell
                val legal = simulator.getLegalActions(state, actor).single {
                    it.affordable && (it.action as? CastSpell)?.cardId == archived.cardId
                }.copy(action = archived)
                state.getEntity(archived.cardId)?.get<CardComponent>()?.name shouldBe
                    "Animar, Soul of Elements"
                (archived.cardId in state.getZone(ZoneKey(actor, Zone.COMMAND))) shouldBe true
                val historicalPassScore = requireNotNull(chosen["score"]?.jsonPrimitive?.doubleOrNull)
                val historicalCastScore = requireNotNull(animar["score"]?.jsonPrimitive?.doubleOrNull)
                val historicalGap = historicalPassScore - historicalCastScore
                check(kotlin.math.abs(historicalGap - 0.465) < 0.0000001)
                val hand = state.getZone(ZoneKey(actor, Zone.HAND))
                val handNames = hand.mapNotNull {
                    state.getEntity(it)?.get<CardComponent>()?.name
                }
                val creatureInHand = hand.filter { id ->
                    state.getEntity(id)?.get<CardComponent>()?.isCreature == true
                }
                // This advisor credits ONLY an already-owned hand creature cast both
                // before and after commander deployment. No creature in the current
                // hand means that its eligible follow-up intersection must be empty.
                creatureInHand.size shouldBe 0
                val direct = processor.process(state, archived).result
                check(direct.error == null) { "Real Animar cast rejected at index $index" }
                val passDirect = processor.process(state, PassPriority(actor)).result
                check(passDirect.error == null) { "Real pass rejected at index $index" }
                val simulatedCast = simulator.simulate(state, archived)
                val deployed = simulatedCast as? SimulationResult.Terminal
                    ?: error("Animar cast did not reach advisor's quiet boundary: $index")
                val simulatedPass = simulator.simulate(state, PassPriority(actor))
                val after = deployed.state
                check(archived.cardId in after.getZone(ZoneKey(actor, Zone.BATTLEFIELD)))
                val sameTurnCreatureFollowups = simulator.getLegalActions(after, actor).filter {
                    val c = it.action as? CastSpell
                    c != null && c.cardId in creatureInHand && it.affordable
                }
                sameTurnCreatureFollowups.size shouldBe 0
                val advisor = ManualAnimarDevelopmentAdvisor.evaluateCast(
                    CastContext(
                        state = state, projected = state.projectedState,
                        playerId = actor, action = legal,
                        passScore = historicalPassScore, defaultScore = historicalCastScore,
                        evaluator = evaluator, simulator = simulator,
                    )
                )
                advisor shouldBe null
                val independentCastEval = evaluator.evaluate(after, after.projectedState, actor)
                val independentPassEval = (simulatedPass as? SimulationResult.Terminal)?.let {
                    evaluator.evaluate(it.state, it.state.projectedState, actor)
                }
                val taxCount = state.getEntity(archived.cardId)
                    ?.get<CommanderComponent>()?.castsFromCommandZone
                val record = buildJsonObject {
                    put("actionIndex", index)
                    put("globalTurn", state.turnNumber)
                    put("actor", actor.value)
                    put("life", state.lifeTotal(actor))
                    put("handCardNames", JsonArray(handNames.map(::JsonPrimitive)))
                    put("handCreatureCount", creatureInHand.size)
                    put("sameTurnCreatureFollowupsAfterAnimar", sameTurnCreatureFollowups.size)
                    put("commanderCastsFromZone", taxCount)
                    put("currentManaCost", legal.manaCostString)
                    put("originalPassScore", historicalPassScore)
                    put("originalAnimarCastScore", historicalCastScore)
                    put("originalPassScoreAdvantage", historicalGap)
                    put("directBoardEvaluatorCastLeaf", independentCastEval)
                    put("directBoardEvaluatorPassLeaf", independentPassEval?.let(::JsonPrimitive) ?: JsonNull)
                    put("originalAdvisorWouldAddDevelopmentCredit", advisor != null)
                    put("advisorOverrideScore", advisor?.let(::JsonPrimitive) ?: JsonNull)
                    put("rawCastEngineAccepted", direct.error == null)
                    put("rawPassEngineAccepted", passDirect.error == null)
                    put("testDidNotForceOpponentPasses", true)
                    put("directCastNextPriorityPlayer", direct.state.priorityPlayerId?.value)
                    put("directCastStackSize", direct.state.stack.size)
                    put("simulatedCastQuietBoundary", simulatedCast::class.simpleName)
                    put("simulatedPassQuietBoundary", simulatedPass::class.simpleName)
                    put("autopassDisclaimer", "Simulator automatically passes priority while scoring; not an opponent-response counterfactual")
                    put("defectProven", false)
                }
                results += record
                println("R16_ANIMAR_VALUATION=" + record)
                Files.writeString(output.resolve("r16-valuation.json"),
                    JsonArray(results).toString())
            }
        }
        results.size shouldBe 3
        val summary = buildJsonObject {
            put("status", "EXACT_SAME_TURN_ADVISOR_VALIDATED")
            put("sourceR15Run", 39366363405L)
            put("sourceR15Artifact", 12239496615L)
            put("sourceR15Sha256", digest)
            put("threeCheckpointsWithNoHandCreature", 3)
            put("advisorPositiveCredits", 0)
            put("noNewFullCommanderGames", true)
            put("policyChanges", 0)
            put("defectsProven", 0)
            put("conclusion", "NO_PROVEN_DEFECT")
        }
        Files.writeString(output.resolve("r16-summary.json"), summary.toString())
        println("R16_ANIMAR_RESULT=" + summary)
    }
})
