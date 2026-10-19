package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.ai.engine.advisor.modules.ManualBallistaContinuationAdvisor
import com.wingedsheep.ai.engine.advisor.modules.ManualR7BallistaCombinedAdvisor
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.CounterType
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Frozen-state, public-information regression:
 * - positive: 10-toughness Animar, eight remaining counters, seven other shots queued.
 * - negative: nine-toughness Animar, nine remaining counters, then eight more queued at Animar.
 * Never assumes the controlled fork win will repeat from a single first action.
 */
class ManualR7BallistaAllocationRegressionTest : FunSpec({
    test("original nonlethal 1609 target is suppressed, but all nine lethal 1610 shots remain eligible") {
        val archive = Path.of(requireNotNull(System.getenv("MANUAL_R7_FORK_ARCHIVE")))
        val output = Path.of(requireNotNull(System.getenv("MANUAL_R7_ALLOCATION_OUTPUT")))
        Files.createDirectories(output)
        val hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))
            .joinToString("") { "%02x".format(it) }
        hash shouldBe "cf638cfef6e88e63094c126ed8dc1dc5ad84bc200a071ddd22b5f0dc058e769b"
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val sim = GameSimulator(registry)
        val processor = ActionProcessor(registry)
        val evaluator = AIPlayer.defaultEvaluator()
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val observations = mutableListOf<JsonElement>()

        ZipFile(archive.toFile()).use { zip ->
            fun read(file: String): String = zip.getInputStream(
                requireNotNull(zip.getEntry("r7-ballista-output/" + file)) { "Missing original " + file }
            ).bufferedReader().readText()
            fun score(state: GameState, archived: ActivateAbility, label: String): JsonObject {
                val player = archived.playerId
                val offered = sim.getLegalActions(state, player).singleOrNull {
                    it.affordable && it.requiresTargets &&
                    (it.action as? ActivateAbility)?.sourceId == archived.sourceId
                } ?: error("R7 original Ballista activation not legal at " + label)
                val action = (offered.action as ActivateAbility).copy(targets = archived.targets)
                val legal = offered.copy(action = action)
                val simulated = sim.simulate(state, action)
                check(simulated !is SimulationResult.Illegal) {
                    "Candidate activation illegal: " + label
                }
                val passed = sim.simulate(state, PassPriority(player))
                check(passed !is SimulationResult.Illegal)
                val passScore = evaluator.evaluate(passed.state, passed.state.projectedState, player)
                val defaultScore = evaluator.evaluate(simulated.state, simulated.state.projectedState, player)
                val context = CastContext(state, state.projectedState, player, legal, passScore,
                    defaultScore, evaluator, sim)
                val retained = ManualBallistaContinuationAdvisor.evaluateCast(context)
                val candidate = ManualR7BallistaCombinedAdvisor.evaluateCast(context)
                val observation = buildJsonObject {
                    put("label", label)
                    put("turn", state.turnNumber)
                    put("sourceCounters", state.getEntity(action.sourceId)
                        ?.get<CountersComponent>()?.getCount(CounterType.PLUS_ONE_PLUS_ONE))
                    put("stackSize", state.stack.size)
                    put("baselineScore", defaultScore)
                    put("passScore", passScore)
                    put("retainedAdvisorScore", retained?.let(::JsonPrimitive) ?: JsonNull)
                    put("candidateScore", candidate?.let(::JsonPrimitive) ?: JsonNull)
                }
                observations += observation
                Files.writeString(output.resolve("allocation-observations.json"), JsonArray(observations).toString())
                println("R7_ALLOCATION_CONTROL=" + observation)
                return observation
            }

            val seed1609 = json.decodeFromString<GameState>(read("seed-202610191609-before-2659.json"))
            val original1609 = json.decodeFromString<GameAction>(
                json.parseToJsonElement(read("replay-202610191609-progress.json"))
                    .jsonObject.getValue("originalAction").jsonPrimitive.content
            ) as ActivateAbility
            val positive = score(seed1609, original1609, "1609-nonlethal-2659")
            check(positive.getValue("candidateScore") !is JsonNull) {
                "Candidate must explicitly override ineffectual shot, not return null to default"
            }
            check(positive.getValue("candidateScore").jsonPrimitive.double <
                positive.getValue("passScore").jsonPrimitive.double) {
                "Positive case must score below passing, not merely add no bonus"
            }

            val seed1610 = json.decodeFromString<GameState>(read("seed-202610191610-before-2764.json"))
            val first1610 = json.decodeFromString<GameAction>(
                json.parseToJsonElement(read("replay-202610191610-progress.json"))
                    .jsonObject.getValue("originalAction").jsonPrimitive.content
            ) as ActivateAbility
            var state = seed1610
            repeat(9) { i ->
                val negative = score(state, first1610, "1610-lethal-queued-shot-" + (i + 1))
                // Preserve R4 terminal protections; no new tactical penalty against
                // a real nine-shot lethal removal line while shots accumulate.
                negative.getValue("candidateScore") shouldBe negative.getValue("retainedAdvisorScore")
                val processed = processor.process(state, first1610).result
                check(processed.error == null) {
                    "Actual nine-shot sequence rejected at step " + (i + 1) + ": " + processed.error
                }
                state = processed.state
            }
        }
        val composed = ManualR7BallistaAllocationCandidate.profile
        val moduleCount = composed.advisorModules.count {
            it == com.wingedsheep.ai.engine.advisor.modules.ManualR7BallistaAllocationAdvisorModule
        }
        moduleCount shouldBe 1
        check(composed.advisorModules.none {
            it == com.wingedsheep.ai.engine.advisor.modules.ManualBallistaContinuationAdvisorModule
        })
        val resolver = CardAdvisorRegistry()
        composed.advisorModules.forEach { it.register(resolver) }
        check(resolver.getAdvisor("Walking Ballista") != null)
        check(resolver.getAdvisor("Ancestral Statue") != null)
        check(resolver.getAdvisor("Pact of Negation") != null)
        AiProfile.CURRENT.advisorModules.isEmpty() shouldBe true
        observations.size shouldBe 10
    }
})
