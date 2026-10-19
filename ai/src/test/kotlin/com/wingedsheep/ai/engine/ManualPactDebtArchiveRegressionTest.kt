package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.ai.engine.advisor.modules.ManualPactDebtAdvisor
import com.wingedsheep.ai.engine.advisor.modules.ManualPactDebtAdvisorModule
import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.PlayerLostComponent
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

class ManualPactDebtArchiveRegressionTest : FunSpec({
    test("real R4 prefix distinguishes two fatal Pact debts from affordable Beast Within protection") {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val output = (System.getenv("MANUAL_R5_PACT_OUTPUT")?.let(Path::of) ?: root.resolve("ai/build/manual-r5-pact")).resolve("archive")
        Files.createDirectories(output)
        val archive = root.resolve("gameplay/manual-r5/input/r4-integration-39270737788.zip")
        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive)).joinToString("") { "%02x".format(it) } shouldBe
            "bd541f2a72aa7ec229c115a83c4d59ef44d35131ee11322ac52a10f9e9d3c6d0"
        val registry = CardRegistry().apply { MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) } }
        val processor = ActionProcessor(registry)
        val simulator = GameSimulator(registry)
        val evaluator = AIPlayer.defaultEvaluator()
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val profile = ManualTransmissionR4Candidate.profile.copy(id = "manual-pact-debt-r5",
            advisorModules = ManualTransmissionR4Candidate.profile.advisorModules + ManualPactDebtAdvisorModule)
        val evidence = mutableListOf<JsonElement>()
        val bindings = mutableListOf<JsonElement>()
        val insights = Files.newBufferedWriter(output.resolve("decisions.jsonl"))
        val sink = AiInsightSink { _, insight -> insights.write(json.encodeToString(insight)); insights.newLine(); insights.flush() }
        var count = 0
        val seen = mutableSetOf<Int>()
        try {
        ZipFile(archive.toFile()).use { zip ->
            fun read(name: String) = zip.getInputStream(zip.getEntry("r4-output/smoke/seed-202610191401/$name")).bufferedReader().readText()
            var state = json.decodeFromString<GameState>(read("initial-state.json"))
            for (line in read("actions.jsonl").lineSequence().filter(String::isNotBlank)) {
                val row = json.parseToJsonElement(line).jsonObject
                if (row["record"]?.jsonPrimitive?.content != "submit") continue
                val index = row.getValue("index").jsonPrimitive.int
                val archived = json.decodeFromString<GameAction>(row.getValue("action").jsonPrimitive.content)
                val action = if (archived is ActivateAbility) {
                    val ids = simulator.getLegalActions(state, archived.playerId).filter { it.affordable &&
                        (archived.targets.isEmpty() || it.requiresTargets) && (it.action as? ActivateAbility)?.sourceId == archived.sourceId }
                        .map { (it.action as ActivateAbility).abilityId }.distinct()
                    if (archived.abilityId in ids) archived else {
                        check(ids.size == 1) { "Ambiguous replay ability at $index: $ids" }
                        bindings += buildJsonObject { put("index", index); put("source", archived.sourceId.value)
                            put("archived", archived.abilityId.value); put("runtime", ids.single().value) }
                        archived.copy(abilityId = ids.single())
                    }
                } else archived
                if (index in setOf(47, 393, 588)) {
                    val cast = action as CastSpell
                    val original = AIPlayer.create(registry, action.playerId, ManualTransmissionR4Candidate.profile).chooseAction(state)
                    original shouldBe action
                    val chosen = AIPlayer.create(registry, action.playerId, profile, insightSink = sink).chooseAction(state)
                    val offer = simulator.getLegalActions(state, action.playerId).single { (it.action as? CastSpell)?.cardId == cast.cardId }.copy(action = action)
                    val after = simulator.simulate(state, action) as SimulationResult.Terminal
                    val passed = simulator.simulate(state, PassPriority(action.playerId)) as SimulationResult.Terminal
                    val passValue = evaluator.evaluate(passed.state, passed.state.projectedState, action.playerId)
                    val raw = evaluator.evaluate(after.state, after.state.projectedState, action.playerId)
                    val ctx = CastContext(state, state.projectedState, action.playerId, offer, passValue, raw, evaluator, simulator)
                    val debt = ManualPactDebtAdvisor.nextUpkeepDebt(ctx, after.state)
                    val expected = if (index == 588) ManualPactDebtAdvisor.Debt.FUNDED else ManualPactDebtAdvisor.Debt.UNFUNDED
                    evidence += buildJsonObject { put("index", index); put("turn", state.turnNumber); put("actor", action.playerId.value)
                        put("debt", debt.name); put("rawScore", raw); put("passScore", passValue)
                        put("candidateChoice", json.encodeToString<GameAction>(chosen)) }
                    Files.writeString(output.resolve("decision-$index-before.json"), json.encodeToString(state))
                    Files.writeString(output.resolve("pact-decision-evidence.json"), JsonArray(evidence).toString())
                    debt shouldBe expected
                    if (index == 588) chosen shouldBe action else check(chosen !is CastSpell || chosen.cardId != cast.cardId)
                    val alternate = processor.process(state, chosen).result
                    check(alternate.error == null)
                    Files.writeString(output.resolve("decision-$index-candidate-after.json"), json.encodeToString(alternate.state))
                    seen += index
                }
                val before = state
                val result = processor.process(state, action).result
                check(result.error == null) { "Replay $index: ${result.error}" }
                state = result.state; count++
                if (index == 133 || index == 453) {
                    val player = com.wingedsheep.sdk.model.EntityId(if (index == 133) "manual-mirror-0" else "manual-mirror-2")
                    state.getEntity(player)?.has<PlayerLostComponent>() shouldBe true
                    evidence += buildJsonObject { put("index", index); put("lost", player.value); put("life", state.lifeTotal(player))
                        put("events", JsonArray(result.events.map { JsonPrimitive(it.toString()) })) }
                    seen += index
                }
                if (index == 619) {
                    val actor = action.playerId
                    val tappedBefore = before.projectedState.getBattlefieldControlledBy(actor).count { before.getEntity(it)?.has<TappedComponent>() == true }
                    val tappedAfter = state.projectedState.getBattlefieldControlledBy(actor).count { state.getEntity(it)?.has<TappedComponent>() == true }
                    evidence += buildJsonObject { put("index", index); put("tappedBefore", tappedBefore); put("tappedAfter", tappedAfter)
                        put("events", JsonArray(result.events.map { JsonPrimitive(it.toString()) })); put("action", json.encodeToString<GameAction>(action)) }
                    Files.writeString(output.resolve("pact-decision-evidence.json"), JsonArray(evidence).toString())
                    Files.writeString(output.resolve("payment-619-before.json"), json.encodeToString(before))
                    Files.writeString(output.resolve("payment-619-after.json"), json.encodeToString(state))
                    check(state.getEntity(actor)?.has<PlayerLostComponent>() != true)
                    check(state.pendingDecision == null)
                    check(tappedAfter - tappedBefore == 5) { "Five-source Pact payment must really tap five sources: before=$tappedBefore after=$tappedAfter" }
                    seen += index
                    break
                }
            }
        }
        } finally {
            insights.close()
            Files.writeString(output.resolve("pact-decision-evidence.json"), JsonArray(evidence).toString())
            Files.writeString(output.resolve("ability-bindings.json"), JsonArray(bindings).toString())
        }
        seen shouldBe setOf(47, 133, 393, 453, 588, 619)
        count shouldBe 619
        AiProfile.CURRENT.advisorModules.isEmpty() shouldBe true
    }
})
