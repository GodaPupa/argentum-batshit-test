package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.ai.engine.advisor.modules.ManualAnimarDevelopmentAdvisor
import com.wingedsheep.ai.engine.advisor.modules.ManualAnimarDevelopmentAdvisorModule
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Real Round 2 action prefix; no supplied outcomes or fabricated board snapshots. */
class ManualAnimarProductiveContinuationTest : FunSpec({
    test("reject unproductive turn 39 credit while preserving actual turn 27 creature development") {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
        val output = System.getenv("MANUAL_CONTINUATION_OUTPUT")?.let(Path::of)
            ?: root.resolve("ai/build/manual-continuation")
        Files.createDirectories(output)
        val archive = root.resolve("gameplay/manual-round2/evidence/final-seed-3.zip")
        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))
            .joinToString("") { "%02x".format(it) } shouldBe
            "08bdb51cd072208f50596936ef1de0c47ff72f1b35f36d0c9aac69823e862e81"
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val simulator = GameSimulator(registry)
        val processor = ActionProcessor(registry)
        val evaluator = AIPlayer.defaultEvaluator()
        val profile = AiProfile.CURRENT.copy(id = "manual-animar-development-r2",
            advisorModules = listOf(ManualAnimarDevelopmentAdvisorModule))
        val bindings = mutableListOf<JsonElement>()
        val evidence = mutableListOf<JsonElement>()
        var sawProductive = false
        var sawExecutedFollowup = false
        var sawCounterexample = false
        ZipFile(archive.toFile()).use { zip ->
            fun entry(name: String) = zip.getInputStream(zip.getEntry("seed-3-seat-1/candidate/$name"))
                .bufferedReader().readText()
            var state = json.decodeFromString<GameState>(entry("initial-state.json"))
            for (line in entry("actions.jsonl").lineSequence().filter(String::isNotBlank)) {
                val row = json.parseToJsonElement(line).jsonObject
                if (row["record"]?.jsonPrimitive?.content != "submit") continue
                val index = row.getValue("index").jsonPrimitive.int
                val archived = json.decodeFromString<GameAction>(row.getValue("action").jsonPrimitive.content)
                val action = if (archived is ActivateAbility) {
                    val ids = simulator.getLegalActions(state, archived.playerId).filter { it.affordable }
                        .mapNotNull { it.action as? ActivateAbility }.filter { it.sourceId == archived.sourceId }
                        .map { it.abilityId }.distinct()
                    if (archived.abilityId in ids) archived else {
                        check(ids.size == 1) { "Ambiguous ability identity at $index: $ids" }
                        bindings += buildJsonObject {
                            put("index", index); put("source", archived.sourceId.value)
                            put("archivedAbility", archived.abilityId.value); put("runtimeAbility", ids.single().value)
                        }
                        archived.copy(abilityId = ids.single())
                    }
                } else archived
                if (index == 905 || index == 1270) {
                    val legal = simulator.getLegalActions(state, action.playerId).single { it.action == action }
                    val deployed = simulator.simulate(state, action) as SimulationResult.Terminal
                    val before = evaluator.evaluate(state, state.projectedState, action.playerId)
                    val after = evaluator.evaluate(deployed.state, deployed.state.projectedState, action.playerId)
                    val context = CastContext(state, state.projectedState, action.playerId, legal,
                        before, after, evaluator, simulator)
                    val adjusted = ManualAnimarDevelopmentAdvisor.evaluateCast(context)
                    val chosen = AIPlayer.create(registry, action.playerId, profile).chooseAction(state)
                    evidence += buildJsonObject {
                        put("index", index); put("turn", state.turnNumber); put("passScore", before)
                        put("rawAnimarScore", after); put("adjustedAnimarScore", adjusted)
                        put("candidateChoice", json.encodeToString<GameAction>(chosen))
                    }
                    Files.writeString(output.resolve("counterexample-evidence.json"), JsonArray(evidence).toString())
                    Files.writeString(output.resolve("ability-id-bindings.json"), JsonArray(bindings).toString())
                    if (index == 905) {
                        check(adjusted != null && adjusted > after)
                        chosen shouldBe action
                        sawProductive = true
                    } else {
                        Files.writeString(output.resolve("turn39-before.json"), json.encodeToString(state))
                        val purphoros = simulator.getLegalActions(deployed.state, action.playerId).single {
                            it.action is CastSpell && deployed.state.getEntity((it.action as CastSpell).cardId)
                                ?.get<CardComponent>()?.name == "Purphoros, God of the Forge"
                        }
                        purphoros.affordable shouldBe true
                        val followed = simulator.simulate(deployed.state, purphoros.action) as SimulationResult.Terminal
                        val withGain = evaluator.evaluate(followed.state, followed.state.projectedState, action.playerId) - after
                        check(withGain < 0.0) { "Archived Purphoros must remain an unproductive continuation: $withGain" }
                        AIPlayer.create(registry, action.playerId, AiProfile.CURRENT).chooseAction(deployed.state) shouldBe PassPriority(action.playerId)
                        adjusted shouldBe null
                        chosen shouldBe PassPriority(action.playerId)
                        sawCounterexample = true
                        break
                    }
                }
                if (index == 908) {
                    AIPlayer.create(registry, action.playerId, profile).chooseAction(state) shouldBe action
                    sawExecutedFollowup = true
                }
                val result = processor.process(state, action).result
                check(result.error == null) { "Replay failed at $index: ${result.error}" }
                state = result.state
            }
        }
        sawProductive shouldBe true
        sawExecutedFollowup shouldBe true
        sawCounterexample shouldBe true
        AiProfile.CURRENT.advisorModules.isEmpty() shouldBe true
    }
})
