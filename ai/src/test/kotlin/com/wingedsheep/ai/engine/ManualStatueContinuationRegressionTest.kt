package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.modules.ManualStatueContinuationAdvisorModule
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Zone
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.util.zip.ZipFile

class ManualStatueContinuationRegressionTest : FunSpec({
    test("archived 213 returns are legal but candidate stops counter-only continuation") {
        val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }.first { Files.exists(it.resolve("settings.gradle.kts")) }
        val output = System.getenv("MANUAL_R3_OUTPUT")?.let(Path::of) ?: root.resolve("ai/build/manual-statue")
        Files.createDirectories(output)
        val registry = CardRegistry().apply { MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) } }
        val processor = ActionProcessor(registry)
        val simulator = GameSimulator(registry)
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val profile = AiProfile.CURRENT.copy(id = "manual-statue-continuation-r3", advisorModules = listOf(ManualStatueContinuationAdvisorModule))
        ZipFile(root.resolve("gameplay/manual-continuation-r2/evidence/cells/manual-continuation-seed-0-seat-1-39207390012.zip").toFile()).use { zip ->
            val prefix = "continuation-output/games/seed-0-seat-1/candidate/"
            fun read(name: String) = zip.getInputStream(zip.getEntry(prefix + name)).bufferedReader().readText()
            var state = json.decodeFromString<GameState>(read("initial-state.json"))
            val returnRows = mutableListOf<JsonElement>()
            var casts = 0
            var checkedExit = false
            for (line in read("actions.jsonl").lineSequence().filter(String::isNotBlank)) {
                val row = json.parseToJsonElement(line).jsonObject
                if (row["record"]?.jsonPrimitive?.content != "submit") continue
                val index = row.getValue("index").jsonPrimitive.int
                val archived = json.decodeFromString<GameAction>(row.getValue("action").jsonPrimitive.content)
                val action = if (archived is ActivateAbility) {
                    val legal = simulator.getLegalActions(state, archived.playerId).filter { it.affordable }.mapNotNull { it.action as? ActivateAbility }
                        .filter { it.sourceId == archived.sourceId }.map { it.abilityId }.distinct()
                    if (archived.abilityId in legal) archived else { check(legal.size == 1); archived.copy(abilityId = legal.single()) }
                } else archived
                if (action is CastSpell && state.getEntity(action.cardId)?.get<CardComponent>()?.name == "Ancestral Statue") casts++
                if (index == 1345) {
                    Files.writeString(output.resolve("first-return-state.json"), json.encodeToString(state))
                    val player = action.playerId
                    val chosen = SubmitDecision(player, AIPlayer.create(registry, player, profile).respondToDecision(state, requireNotNull(state.pendingDecision)))
                    val response = chosen.response as CardsSelectedResponse
                    check(response.selectedCards != listOf(com.wingedsheep.sdk.model.EntityId("e341"))) { "Candidate must exit counter-only loop" }
                    val result = processor.process(state, chosen).result
                    check(result.error == null)
                    check(com.wingedsheep.sdk.model.EntityId("e341") in result.state.getZone(ZoneKey(player, Zone.BATTLEFIELD)))
                    Files.writeString(output.resolve("candidate-exit-action.json"), json.encodeToString<GameAction>(chosen))
                    checkedExit = true
                }
                val result = processor.process(state, action).result
                check(result.error == null) { "Replay $index: ${result.error}" }
                if (action is SubmitDecision && (action.response as? CardsSelectedResponse)?.selectedCards == listOf(com.wingedsheep.sdk.model.EntityId("e341"))) {
                    check(com.wingedsheep.sdk.model.EntityId("e341") in result.state.getZone(ZoneKey(action.playerId, Zone.HAND)))
                    returnRows += buildJsonObject { put("index", index); put("turn", state.turnNumber); put("action", json.encodeToString<GameAction>(action)); put("events", JsonArray(result.events.map { JsonPrimitive(it.toString()) })) }
                }
                state = result.state
            }
            Files.writeString(output.resolve("all-213-returns.json"), JsonArray(returnRows).toString())
            returnRows.size shouldBe 213
            casts shouldBe 214
            checkedExit shouldBe true
            state.turnNumber shouldBe 32
            println("STATUE_ARCHIVE_REPLAY casts=$casts returns=${returnRows.size} turn=${state.turnNumber}")
        }
        AiProfile.CURRENT.advisorModules.isEmpty() shouldBe true
    }
})
