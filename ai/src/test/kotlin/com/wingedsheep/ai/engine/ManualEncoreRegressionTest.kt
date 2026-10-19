package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.combat.MustAttackDefenderThisTurnComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
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

internal object EncoreArchive {
    val root: Path = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("settings.gradle.kts")) }
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    fun registry() = CardRegistry().apply { MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) } }
    fun savedState(): GameState = ZipFile(path().toFile()).use { z ->
        json.decodeFromString(z.getInputStream(z.getEntry(prefix + "final-state.json")).bufferedReader().readText())
    }
    const val prefix = "continuation-output/games/seed-0-seat-3/candidate/"
    fun path() = root.resolve("gameplay/manual-continuation-r2/evidence/cells/manual-continuation-seed-0-seat-3-39207390012.zip")
    fun rejected() = DeclareAttackers(EntityId("manual-mirror-2"), linkedMapOf(
        EntityId("e756") to EntityId("manual-mirror-1"),
        EntityId("e757") to EntityId("manual-mirror-1"),
        EntityId("e232") to EntityId("manual-mirror-1")))
    fun output(): Path = (System.getenv("MANUAL_R3_OUTPUT")?.let(Path::of)
        ?: root.resolve("ai/build/manual-r3-encore")).also { Files.createDirectories(it) }
}

class ManualEncoreRegressionTest : FunSpec({
    val registry = EncoreArchive.registry()
    val simulator = GameSimulator(registry)
    val processor = ActionProcessor(registry)
    test("archived real action prefix reproduces exact Encore rejection") {
        MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(EncoreArchive.path()))
            .joinToString("") { "%02x".format(it) } shouldBe "ae57686c12bdf09cd81bbbeccf64ee839dc3eff8143c1167863c43b3cc8c28b1"
        ZipFile(EncoreArchive.path().toFile()).use { z ->
            fun entry(n: String) = z.getInputStream(z.getEntry(EncoreArchive.prefix + n)).bufferedReader().readText()
            var state = EncoreArchive.json.decodeFromString<GameState>(entry("initial-state.json"))
            var count = 0
            val bindings = mutableListOf<JsonElement>()
            for (line in entry("actions.jsonl").lineSequence().filter(String::isNotBlank)) {
                val row = EncoreArchive.json.parseToJsonElement(line).jsonObject
                if (row["record"]?.jsonPrimitive?.content != "submit") continue
                val index = row.getValue("index").jsonPrimitive.int
                val archived = EncoreArchive.json.decodeFromString<GameAction>(row.getValue("action").jsonPrimitive.content)
                val action = if (archived is ActivateAbility) {
                    val ids = simulator.getLegalActions(state, archived.playerId).filter { it.affordable }
                        .mapNotNull { it.action as? ActivateAbility }.filter { it.sourceId == archived.sourceId }
                        .map { it.abilityId }.distinct()
                    if (archived.abilityId in ids) archived else {
                        check(ids.size == 1) { "Ambiguous binding at $index: $ids" }
                        bindings += buildJsonObject { put("index", index); put("source", archived.sourceId.value)
                            put("archived", archived.abilityId.value); put("runtime", ids.single().value) }
                        archived.copy(abilityId = ids.single())
                    }
                } else archived
                val result = processor.process(state, action).result
                if (index == 3562) {
                    action shouldBe EncoreArchive.rejected()
                    result.error shouldBe "Amphin Mutineer must attack its designated defender this turn if able"
                    Files.writeString(EncoreArchive.output().resolve("replayed-before-rejection.json"), EncoreArchive.json.encodeToString(state))
                    Files.writeString(EncoreArchive.output().resolve("ability-bindings.json"), JsonArray(bindings).toString())
                    count shouldBe 3561
                    break
                }
                check(result.error == null) { "Replay $index: ${result.error}" }
                state = result.state; count++
            }
            count shouldBe 3561
        }
    }
    test("required defenders survive seed and local search while other legal attacker remains available") {
        val state = EncoreArchive.savedState()
        val actor = EncoreArchive.rejected().playerId
        val legal = simulator.getLegalActions(state, actor).single { it.action is DeclareAttackers }
        legal.mandatoryAttackers!!.containsAll(listOf(EntityId("e756"), EntityId("e757"))) shouldBe true
        val baseline = AIPlayer.create(registry, actor, AiProfile.CURRENT).chooseAction(state)
        baseline shouldBe EncoreArchive.rejected()
        val profile = AiProfile.CURRENT.copy(id = "manual-encore-defenders-r3", respectRequiredAttackDefenders = true)
        val chosen = AIPlayer.create(registry, actor, profile).chooseAction(state) as DeclareAttackers
        chosen.attackers[EntityId("e756")] shouldBe EntityId("manual-mirror-1")
        chosen.attackers[EntityId("e757")] shouldBe EntityId("manual-mirror-3")
        check(EntityId("e232") in legal.validAttackers!!)
        processor.process(state, chosen).result.error shouldBe null
        Files.writeString(EncoreArchive.output().resolve("corrected-attack.json"), EncoreArchive.json.encodeToString<GameAction>(chosen))
    }
    test("four-seat seed respects a different required defender and does not claim split lethal") {
        var state = EncoreArchive.savedState()
        val actor = EncoreArchive.rejected().playerId
        state.turnOrder.size shouldBe 4
        // Synthetic regression variant only; archived replay/smoke remain untouched.
        state = state.updateEntity(EntityId("manual-mirror-0")) {
            it.with(com.wingedsheep.engine.state.components.identity.LifeTotalComponent(40))
                .without<com.wingedsheep.engine.state.components.player.PlayerLostComponent>()
                .without<com.wingedsheep.engine.state.components.player.PlayerLeftGameComponent>()
        }
        state.getOpponents(actor).size shouldBe 3
        val id = EntityId("e757")
        state = state.updateEntity(id) { it.with(MustAttackDefenderThisTurnComponent(EntityId("manual-mirror-1"))) }
        val legal = simulator.getLegalActions(state, actor).single { it.action is DeclareAttackers }
        val seed = CombatSeed.attackers(state, state.projectedState, legal, actor, registry,
            respectRequiredAttackDefenders = true)
        seed.attackers[id] shouldBe EntityId("manual-mirror-1")
        val original = EncoreArchive.savedState()
        val split = CombatSeed.attackers(original, original.projectedState,
            simulator.getLegalActions(original, actor).single { it.action is DeclareAttackers }, actor, registry,
            respectRequiredAttackDefenders = true)
        split.lethal shouldBe false
    }
    test("impossible designated defender is not forced into attack plan") {
        var state = EncoreArchive.savedState()
        val id = EntityId("e757")
        val actor = EncoreArchive.rejected().playerId
        state = state.updateEntity(id) { it.with(MustAttackDefenderThisTurnComponent(actor)) }
        val legal = simulator.getLegalActions(state, actor).single { it.action is DeclareAttackers }
        val chosen = AIPlayer.create(registry, actor,
            AiProfile.CURRENT.copy(id = "manual-encore-defenders-r3", respectRequiredAttackDefenders = true)).chooseAction(state) as DeclareAttackers
        check(chosen.attackers[id] != actor)
        processor.process(state, chosen).result.error shouldBe null
    }
})
