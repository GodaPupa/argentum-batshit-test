package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.ai.engine.advisor.modules.ManualAnimarDevelopmentAdvisor
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.mechanics.mana.ManaSolver
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
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Read-only, exact-R5 archived replay; identifies actual decision states before proposing a policy change. */
class ManualR6OpportunityReplayTest : FunSpec({
    test("audit thirteen real seat-zero Animar alternatives without changing gameplay") {
        val archive = Path.of(requireNotNull(System.getenv("MANUAL_R6_R5_ARCHIVE")) {
            "R6 diagnostic requires exact, downloaded R5 artifact; no synthetic fallback"
        })
        val out = Path.of(requireNotNull(System.getenv("MANUAL_R6_OUTPUT")))
        Files.createDirectories(out)
        val hash = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive))
            .joinToString("") { "%02x".format(it) }
        hash shouldBe "0562942a8b66a803e6e35265b5005220d3676b21869cca935e5cb1f2f66f71a7"

        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val processor = ActionProcessor(registry)
        val sim = GameSimulator(registry)
        val solver = ManaSolver(registry)
        val evaluator = AIPlayer.defaultEvaluator()
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val chosenIndexes = listOf(434, 816, 1025, 1316, 1517, 1702, 1813, 1921, 2214, 2368, 2428, 2494, 2556)
        val findings = mutableListOf<JsonElement>()
        val bindings = mutableListOf<JsonElement>()
        val prefix = "r5-followup-output/smoke/seed-202610191608/"
        val seat = EntityId("manual-mirror-0")
        fun label(state: GameState, id: EntityId): String =
            state.getEntity(id)?.get<CardComponent>()?.name ?: id.value
        fun battlefield(state: GameState, player: EntityId): JsonArray =
            JsonArray(state.projectedState.getBattlefieldControlledBy(player).map { id ->
                buildJsonObject {
                    put("id", id.value); put("card", label(state, id))
                    put("tapped", state.getEntity(id)?.has<TappedComponent>() == true)
                }
            })
        try {
            ZipFile(archive.toFile()).use { zip ->
                fun entry(name: String): String = zip.getInputStream(
                    requireNotNull(zip.getEntry(prefix + name)) { "Missing $name" }
                ).bufferedReader().readText()
                val insights = entry("decisions.jsonl").lineSequence().filter(String::isNotBlank)
                    .map { json.parseToJsonElement(it).jsonObject }.filter { d ->
                        d["playerId"]?.jsonPrimitive?.content == seat.value &&
                            d.getValue("options").jsonArray.any { o ->
                                o.jsonObject["cardName"]?.jsonPrimitive?.content == "Animar, Soul of Elements" &&
                                o.jsonObject["actionType"]?.jsonPrimitive?.content == "CastSpell"
                            }
                    }.toList()
                insights.size shouldBe 13
                var state = json.decodeFromString<GameState>(entry("initial-state.json"))
                var count = 0
                for (line in entry("actions.jsonl").lineSequence().filter(String::isNotBlank)) {
                    val row = json.parseToJsonElement(line).jsonObject
                    if (row["record"]?.jsonPrimitive?.content != "submit") continue
                    val index = row.getValue("index").jsonPrimitive.int
                    val original = json.decodeFromString<GameAction>(row.getValue("action").jsonPrimitive.content)
                    val action = if (original is ActivateAbility) {
                        val offers = sim.getLegalActions(state, original.playerId)
                            .filter { it.affordable && (original.targets.isEmpty() || it.requiresTargets) }
                            .mapNotNull { it.action as? ActivateAbility }
                            .filter { it.sourceId == original.sourceId }.map { it.abilityId }.distinct()
                        if (original.abilityId in offers) original else {
                            check(offers.size == 1) { "Ambiguous archived ability $index: $offers" }
                            bindings += buildJsonObject { put("index", index); put("source", original.sourceId.value)
                                put("archived", original.abilityId.value); put("runtime", offers.single().value) }
                            original.copy(abilityId = offers.single())
                        }
                    } else original
                    if (index in chosenIndexes) {
                        val insight = insights[count]
                        val options = insight.getValue("options").jsonArray.map { it.jsonObject }
                        val commander = state.getZone(ZoneKey(seat, Zone.COMMAND)).single()
                        val legal = sim.getLegalActions(state, seat).single {
                            it.affordable && (it.action as? CastSpell)?.cardId == commander
                        }
                        val cast = legal.action as CastSpell
                        val target = options.single { it["cardName"]?.jsonPrimitive?.content == "Animar, Soul of Elements" }
                        val archivedChoice = options.single { it["chosen"]?.jsonPrimitive?.booleanOrNull == true }
                        check(json.parseToJsonElement(row.getValue("action").jsonPrimitive.content) ==
                            archivedChoice.getValue("action")) { "Decision/trace mismatch at $index" }
                        check(insight.getValue("turnNumber").jsonPrimitive.int == state.turnNumber)
                        val castResult = sim.simulate(state, cast) as? SimulationResult.Terminal
                            ?: error("Animar unresolved/nonterminal at $index")
                        val actualResult = sim.simulate(state, action) as? SimulationResult.Terminal
                        val before = evaluator.evaluate(state, state.projectedState, seat)
                        val castScore = evaluator.evaluate(castResult.state, castResult.state.projectedState, seat)
                        val originalAdvisor = ManualAnimarDevelopmentAdvisor.evaluateCast(
                            CastContext(state, state.projectedState, seat, legal,
                                insight.getValue("baselineScore").jsonPrimitive.double,
                                castScore, evaluator, sim)
                        )
                        val taxCount = state.getEntity(commander)?.get<CommanderComponent>()?.castsFromCommandZone ?: -1
                        val me = state.getEntity(seat)?.get<ManaPoolComponent>()
                        val others = state.turnOrder.filter { it != seat }
                        val doc = buildJsonObject {
                            put("index", index); put("turn", state.turnNumber); put("step", state.step.name)
                            put("actualChoice", insight["chosenLabel"]); put("animarScore", target.getValue("score"))
                            put("animarRawScore", target["rawScore"])
                            put("baselinePassScore", insight["baselineScore"])
                            put("boardEvalBefore", before); put("boardEvalAfterAnimar", castScore)
                            put("boardEvalAfterChosen", actualResult?.let {
                                evaluator.evaluate(it.state, it.state.projectedState, seat) })
                            put("developmentAdvisorCredit", originalAdvisor?.minus(castScore) ?: 0.0)
                            put("commandTaxCount", taxCount); put("commanderGenericTax", 2 * taxCount)
                            put("commandLegalAndAffordable", legal.affordable); put("commandCastAction", json.encodeToString<GameAction>(cast))
                            put("floatingPool", me?.toString())
                            put("availableManaSources", JsonArray(solver.findAvailableManaSources(state, seat)
                                .map { source -> JsonPrimitive(source.toString()) }))
                            put("myLife", state.lifeTotal(seat))
                            put("myBattlefield", battlefield(state, seat))
                            put("myHand", JsonArray(state.getZone(ZoneKey(seat, Zone.HAND))
                                .map { JsonPrimitive(label(state, it)) }))
                            put("opponents", JsonArray(others.map { player -> buildJsonObject {
                                put("player", player.value); put("life", state.lifeTotal(player))
                                put("battlefield", battlefield(state, player))
                            } }))
                            put("topCompetingScores", JsonArray(options.sortedByDescending {
                                it["score"]?.jsonPrimitive?.doubleOrNull ?: Double.NEGATIVE_INFINITY
                            }.take(9).map { opt -> buildJsonObject {
                                put("card", opt["cardName"]); put("label", opt["label"])
                                put("score", opt["score"]); put("rawScore", opt["rawScore"])
                            } }))
                            put("interpretation", "Read-only state and one-ply legal alternatives; no forced action or predicted opponent response")
                        }
                        findings += doc
                        Files.writeString(out.resolve("opportunities.json"), JsonArray(findings).toString())
                        if (index in setOf(1813, 2368)) {
                            Files.writeString(out.resolve("state-before-$index.json"), json.encodeToString(state))
                            Files.writeString(out.resolve("state-after-animar-$index.json"), json.encodeToString(castResult.state))
                        }
                        println("R6_OPPORTUNITY index=$index turn=${state.turnNumber} choice=${insight["chosenLabel"]} " +
                            "animar=${target.getValue("score")} tax=$taxCount advisor=$originalAdvisor")
                        count++
                    }
                    val result = processor.process(state, action).result
                    check(result.error == null) { "Unaltered R5 archive replay rejected action $index: ${result.error}" }
                    state = result.state
                }
                count shouldBe 13
                state.gameOver shouldBe true
                state.winnerId shouldBe seat
                Files.writeString(out.resolve("ability-bindings.json"), JsonArray(bindings).toString())
                Files.writeString(out.resolve("audit-summary.json"), buildJsonObject {
                    put("originalSource", "e2e92a199bb2c165625bf1505089e8420d91d8b2")
                    put("originalRun", 39293363796); put("artifactSha256", hash)
                    put("opportunityCount", count); put("gameWonBy", state.winnerId?.value)
                    put("originalActionsReplayed", 2571)
                    put("noPolicyModified", true)
                }.toString())
            }
        } finally {
            Files.writeString(out.resolve("opportunities.json"), JsonArray(findings).toString())
            Files.writeString(out.resolve("ability-bindings.json"), JsonArray(bindings).toString())
        }
        AiProfile.CURRENT.advisorModules.isEmpty() shouldBe true
    }
})
