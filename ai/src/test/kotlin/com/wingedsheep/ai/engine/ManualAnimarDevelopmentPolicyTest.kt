package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.ai.engine.advisor.modules.ManualAnimarDevelopmentAdvisor
import com.wingedsheep.ai.engine.advisor.modules.ManualAnimarDevelopmentAdvisorModule
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CommanderComponent
import com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

class ManualAnimarDevelopmentPolicyTest : ScenarioTestBase() {
    init {
        MtgSetCatalog.all.forEach { cardRegistry.register(it.cards); cardRegistry.register(it.basicLands) }
        fun board(followup: String? = "Llanowar Elves", forests: Int = 2, tax: Int = 0): TestGame {
            val builder = scenario().withPlayers("Manual", "Opponent")
                .withCardInCommandZone(1, "Animar, Soul of Elements")
                .withLandsOnBattlefield(1, "Forest", forests)
                .withLandsOnBattlefield(1, "Island", 1).withLandsOnBattlefield(1, "Mountain", 1)
                .withActivePlayer(1).inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN)
            if (followup != null) builder.withCardInHand(1, followup)
            val game = builder.build()
            val commander = game.state.getZone(ZoneKey(game.player1Id, Zone.COMMAND)).single()
            game.state = game.state.updateEntity(commander) {
                it.with(CommanderComponent(ownerId = game.player1Id, castsFromCommandZone = tax))
            }.updateEntity(game.player1Id) { it.with(CommanderRegistryComponent(listOf(commander))) }
            return game
        }
        fun context(game: TestGame): CastContext {
            val sim = GameSimulator(cardRegistry)
            val action = sim.getLegalActions(game.state, game.player1Id).single {
                it.action is CastSpell && (it.action as CastSpell).cardId in game.state.getZone(ZoneKey(game.player1Id, Zone.COMMAND))
            }
            val evaluator = AIPlayer.defaultEvaluator()
            val deployed = sim.simulate(game.state, action.action) as SimulationResult.Terminal
            return CastContext(game.state, game.state.projectedState, game.player1Id, action,
                evaluator.evaluate(game.state, game.state.projectedState, game.player1Id),
                evaluator.evaluate(deployed.state, deployed.state.projectedState, game.player1Id), evaluator, sim)
        }
        test("affordable creature with nonpositive followup value receives no development credit") {
            val c = context(board())
            val deployed = c.simulator.simulate(c.state, c.action.action) as SimulationResult.Terminal
            val elf = c.simulator.getLegalActions(deployed.state, c.playerId).single {
                it.affordable && it.action is CastSpell && deployed.state.getEntity((it.action as CastSpell).cardId)
                    ?.get<com.wingedsheep.engine.state.components.identity.CardComponent>()?.name == "Llanowar Elves"
            }
            val followed = c.simulator.simulate(deployed.state, elf.action) as SimulationResult.Terminal
            val gain = c.evaluator.evaluate(followed.state, followed.state.projectedState, c.playerId) - c.defaultScore
            check(gain <= 0.0) { "This minimal Llanowar fixture must be unproductive: $gain" }
            println("MANUAL_LLANOWAR_FOLLOWUP_GAIN=$gain")
            ManualAnimarDevelopmentAdvisor.evaluateCast(c) shouldBe null
        }
        test("no creature followup is a no-op") { ManualAnimarDevelopmentAdvisor.evaluateCast(context(board(null))) shouldBe null }
        test("creature in hand but unaffordable after Animar is a no-op") {
            ManualAnimarDevelopmentAdvisor.evaluateCast(context(board("Temur Sabertooth", forests = 4))) shouldBe null
        }
        test("commander tax consuming remaining mana cannot invent a followup") {
            ManualAnimarDevelopmentAdvisor.evaluateCast(context(board(forests = 5, tax = 2))) shouldBe null
        }
        test("candidate keeps a real immediate lethal over commander development") {
            val lethal = scenario().withPlayers("Manual", "Opponent").withLifeTotal(2, 3)
                .withCardInCommandZone(1, "Animar, Soul of Elements")
                .withCardInHand(1, "Llanowar Elves").withCardInHand(1, "Lightning Bolt")
                .withLandsOnBattlefield(1, "Forest", 2).withLandsOnBattlefield(1, "Island", 1)
                .withLandsOnBattlefield(1, "Mountain", 2).withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN, Step.PRECOMBAT_MAIN).build()
            val commander = lethal.state.getZone(ZoneKey(lethal.player1Id, Zone.COMMAND)).single()
            lethal.state = lethal.state.updateEntity(commander) { it.with(CommanderComponent(lethal.player1Id)) }
                .updateEntity(lethal.player1Id) { it.with(CommanderRegistryComponent(listOf(commander))) }
            val profile = AiProfile.CURRENT.copy(id = "manual-animar-development-r2", advisorModules = listOf(ManualAnimarDevelopmentAdvisorModule))
            val chosen = AIPlayer.create(cardRegistry, lethal.player1Id, profile).chooseAction(lethal.state)
            val resolved = GameSimulator(cardRegistry).simulate(lethal.state, chosen)
            check(resolved.state.gameOver && resolved.state.winnerId == lethal.player1Id) { "Lethal was not selected: $chosen" }
            AiProfile.CURRENT.advisorModules.isEmpty() shouldBe true
        }
        test("original trace replay audits actual Animar windows and turn thirteen remains unchanged") {
            val root = System.getenv("MANUAL_ROUND2_ROOT")?.let { Path.of(it) } ?: return@test
            val output = Path.of(requireNotNull(System.getenv("MANUAL_ROUND2_OUTPUT"))).resolve("diagnosis")
            Files.createDirectories(output)
            // Match the production catalog exactly. Scenario-only token definitions change minting.
            val cardRegistry = com.wingedsheep.engine.registry.CardRegistry().apply {
                MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
            }
            val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
            ZipFile(root.resolve("tools/manual-round2/original-12161685788.zip").toFile()).use { zip ->
                fun entry(name: String) = zip.getInputStream(zip.getEntry("manual-mirror/$name")).bufferedReader().readText()
                var state = json.decodeFromString<GameState>(entry("initial-state.json"))
                val insights = entry("decisions.jsonl").lineSequence().filter { it.isNotBlank() }.map { json.parseToJsonElement(it).jsonObject }.toList()
                insights.size shouldBe 679
                val opportunities = insights.filter { d -> d.getValue("options").jsonArray.any {
                    it.jsonObject["cardName"]?.jsonPrimitive?.content == "Animar, Soul of Elements"
                } }.toMutableList()
                opportunities.size shouldBe 24
                val processor = ActionProcessor(cardRegistry)
                val sim = GameSimulator(cardRegistry)
                val evaluator = AIPlayer.defaultEvaluator()
                val audits = mutableListOf<JsonElement>()
                val abilityBindings = linkedMapOf<String, JsonElement>()
                val legalWindows = mutableListOf<JsonElement>()
                val candidate = AiProfile.CURRENT.copy(id = "manual-animar-development-r2", advisorModules = listOf(ManualAnimarDevelopmentAdvisorModule))
                for (line in entry("actions.jsonl").lineSequence().filter { it.isNotBlank() }) {
                    val row = json.parseToJsonElement(line).jsonObject
                    if (row["record"]?.jsonPrimitive?.content != "submit") continue
                    val archivedAction = json.decodeFromString<GameAction>(row.getValue("action").jsonPrimitive.content)
                    // AbilityId is process-initialization ordinal data, not a stable card identity.
                    // Rebind only a SINGLE actually legal ability on the SAME physical card.
                    // Record every changed binding; preserve targets/costs/X and refuse ambiguity.
                    val action = if (archivedAction is ActivateAbility) {
                        val currentAbilities = sim.getLegalActions(state, archivedAction.playerId)
                            .filter { it.affordable }.mapNotNull { it.action as? ActivateAbility }
                            .filter { it.sourceId == archivedAction.sourceId }.map { it.abilityId }.distinct()
                        if (archivedAction.abilityId in currentAbilities) archivedAction else {
                            check(currentAbilities.size == 1) { "Ambiguous ability replay at ${row["index"]}: $currentAbilities" }
                            val bound = currentAbilities.single()
                            val key = "${row["sourceName"]}:${archivedAction.abilityId}:$bound"
                            abilityBindings[key] = buildJsonObject { put("source", row["sourceName"]!!)
                                put("archivedId", archivedAction.abilityId.value); put("runtimeId", bound.value)
                                put("reason", "One unique legal activated ability on the same physical source; other action parameters unchanged") }
                            archivedAction.copy(abilityId = bound)
                        }
                    } else archivedAction
                    if (archivedAction !is SubmitDecision && state.activePlayerId == action.playerId && state.phase.isMainPhase) {
                        val ids = state.getZone(ZoneKey(action.playerId, Zone.COMMAND)).toSet()
                        if (ids.isNotEmpty() && sim.getLegalActions(state, action.playerId).any {
                            it.affordable && (it.action as? CastSpell)?.cardId in ids
                        }) legalWindows += buildJsonObject { put("index", row["index"]!!); put("player", action.playerId.value)
                            put("turn", row["turn"]!!); put("declined", action !is CastSpell || action.cardId !in ids) }
                    }
                    val found = opportunities.indexOfFirst { d ->
                        d["turnNumber"] == row["turn"] && d["playerId"]?.jsonPrimitive?.content == action.playerId.value &&
                        d.getValue("options").jsonArray.any { it.jsonObject["chosen"]?.jsonPrimitive?.booleanOrNull == true && it.jsonObject["action"] == json.parseToJsonElement(row.getValue("action").jsonPrimitive.content) }
                    }
                    if (found >= 0) {
                        val d = opportunities.removeAt(found)
                        val option = d.getValue("options").jsonArray.first { it.jsonObject["cardName"]?.jsonPrimitive?.content == "Animar, Soul of Elements" }.jsonObject
                        val commanderAction = json.decodeFromJsonElement<GameAction>(option.getValue("action"))
                        val legal = sim.getLegalActions(state, action.playerId).first { it.action == commanderAction }
                        val c = CastContext(state, state.projectedState, action.playerId, legal,
                            d.getValue("baselineScore").jsonPrimitive.double, option.getValue("score").jsonPrimitive.double, evaluator, sim)
                        val adjusted = ManualAnimarDevelopmentAdvisor.evaluateCast(c)
                        audits += buildJsonObject { put("actionIndex", row["index"]!!); put("turn", row["turn"]!!)
                            put("player", action.playerId.value); put("originalChoice", d["chosenLabel"]!!)
                            put("originalAnimarScore", c.defaultScore); put("candidateAnimarScore", adjusted)
                            put("measuredIncrement", adjusted?.minus(c.defaultScore) ?: 0.0) }
                        if (row.getValue("index").jsonPrimitive.int == 447) {
                            adjusted shouldBe null
                            AIPlayer.create(cardRegistry, action.playerId, AiProfile.CURRENT).chooseAction(state) shouldBe action
                            AIPlayer.create(cardRegistry, action.playerId, candidate).chooseAction(state) shouldBe action
                        }
                    }
                    val result = processor.process(state, action).result
                    check(result.error == null) { "Archive replay ${row["index"]} ${row["sourceName"]}: ${result.error}" }
                    state = result.state
                }
                opportunities.size shouldBe 0
                state.gameOver shouldBe true
                state.winnerId?.value shouldBe "manual-mirror-3"
                Files.writeString(output.resolve("original-opportunities.json"), JsonArray(audits).toString())
                Files.writeString(output.resolve("ability-id-bindings.json"), JsonArray(abilityBindings.values.toList()).toString())
                Files.writeString(output.resolve("original-legal-opportunities.json"), JsonArray(legalWindows).toString())
            }
        }
    }
}
