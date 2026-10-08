package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.*

/** Excluded synthetic initialization/replay fixtures; no official vector or opponent package. */
class ManualReplayEvidenceTest : FunSpec({
    val names = listOf("Animar, Soul of Elements", "Replay Fixture Partner", "Replay Fixture Opponent")
    fun registry() = CardRegistry().apply {
        register(TestCards.all)
        names.forEach { name -> register(card(name) {
            manaCost = "{0}"; typeLine = "Legendary Creature — Human"; power = 1; toughness = 1
        }) }
    }
    val identity = ManualFixtureIdentity("manual-fixture-initialization-replay", "8".repeat(40),
        "CRUISE", List(4) { "1".repeat(64) }, List(4) { "2".repeat(64) }, "3".repeat(64))
    fun spec() = ManualInitializationSpec(
        fixtureIdentitySha256 = ManualInitializationReplay.sha256(identity.bytes()),
        seed = 0x4D414E494E4954L, startingPlayerIndex = 0, manualSeat = 0,
        players = (0..3).map { seat -> ManualReplayPlayer("EXCLUDED_REPLAY_$seat", "replay-seat-$seat",
            List(if (seat == 0) 98 else 99) { if (it % 2 == 0) "Forest" else "Island" },
            if (seat == 0) names.take(2) else listOf(names.last())) })
    fun initialize(s: ManualInitializationSpec) = GameInitializer(registry()).initializeGame(GameConfig(
        players = s.players.map { PlayerConfig(it.name, Deck(it.cards), playerId = EntityId(it.playerId),
            commanderCardNames = it.commanders) }, format = Format.Commander(), startingPlayerIndex = s.startingPlayerIndex,
        skipMulligans = false, useHandSmoother = false, seed = s.seed))
    fun adapter(s: ManualInitializationSpec): PhaseTwoTelemetryAdapter {
        val i = initialize(s)
        return PhaseTwoTelemetryAdapter(i.state, ActionProcessor(registry()), identity.sourceCommit, i.playerIds, s.manualSeat)
    }

    fun runner(s: ManualInitializationSpec): ManualPhaseTwoFixtureRunner {
        val a = adapter(s)
        return ManualPhaseTwoFixtureRunner(a, registry(), a.state.turnOrder.associateWith {
            PhaseTwoPilotPolicy { _, _ -> PhaseTwoPilotChoice.Keep }
        }, ManualFixtureStepBudget(4, 60_000))
    }
    fun root() = Files.createTempDirectory("manual-bound-spec-").toRealPath()
    fun bytes() = ManualInitializationReplay.encode(spec())
    fun pin() = ManualInitializationReplay.sha256(bytes())
    fun run(root: Path, factory: (ManualInitializationSpec) -> ManualPhaseTwoFixtureRunner = ::runner) =
        ManualBoundInitialization.runNew(root, identity, bytes(), pin(), registry(), factory)

    fun tracePin(trace: PhaseTwoEngineTrace) = ManualInitializationReplay.sha256(ManualPhaseTwoStoredFixtureReplay.encode(trace))
    fun check(root: Path, trace: PhaseTwoEngineTrace) = ManualBoundActionReplay.verify(
        root.resolve(identity.fixtureId), identity, pin(), tracePin(trace), registry())
    fun frame(path: Path, payload: ByteArray) = Files.write(path,
        ("${payload.size}:${ManualInitializationReplay.sha256(payload)}\n").toByteArray() + payload)
    fun payload(path: Path): ByteArray {
        val raw = Files.readAllBytes(path)
        return raw.copyOfRange(raw.indexOf(10.toByte()) + 1, raw.size)
    }
    fun collect(root: Path, trace: PhaseTwoEngineTrace) = ManualReplayEvidence.collect(
        root.resolve(identity.fixtureId), identity, pin(), tracePin(trace), registry())
    fun verify(evidence: ByteArray, root: Path, trace: PhaseTwoEngineTrace) = ManualReplayEvidence.verify(
        evidence, root.resolve(identity.fixtureId), identity, pin(), tracePin(trace), registry())

    test("collector binds reconstructed original initialization and four ordered engine actions") {
        val root = root(); val trace = run(root); val evidence = collect(root, trace)
        verify(evidence, root, trace)
        val obj = Json.parseToJsonElement(evidence.toString(Charsets.UTF_8)).jsonObject
        obj.getValue("originalInitializationSpecSha256").jsonPrimitive.content shouldBe pin()
        obj.getValue("originalTraceSha256").jsonPrimitive.content shouldBe tracePin(trace)
        obj.getValue("steps").jsonArray.size shouldBe 4
        obj.getValue("executionAuthorized").jsonPrimitive.boolean shouldBe false
        obj.getValue("authenticatedProvenance").jsonPrimitive.boolean shouldBe false
        obj.keys shouldBe setOf("schema", "executionAuthorized", "authenticatedProvenance", "identitySha256",
            "sourceCommit", "originalInitializationSpecSha256", "originalTraceSha256", "initialStateSha256", "steps", "stopKind")
        collect(root, trace).toList() shouldBe evidence.toList()
    }
    test("changed missing duplicate and reordered collector rows reject") {
        val root = root(); val trace = run(root); val evidence = collect(root, trace)
        val obj = Json.parseToJsonElement(evidence.toString(Charsets.UTF_8)).jsonObject
        val rows = obj.getValue("steps").jsonArray
        for (changed in listOf(rows.dropLast(1), rows + rows.last(), rows.reversed(),
            rows.mapIndexed { i, row -> if (i == 0) JsonObject(row.jsonObject + ("classification" to JsonPrimitive("REJECTED"))) else row })) {
            shouldThrowAny { verify(JsonObject(obj + ("steps" to JsonArray(changed))).toString().toByteArray(), root, trace) }
        }
    }
    test("collector cannot substitute source original pins stop or admission flags") {
        val root = root(); val trace = run(root); val obj = Json.parseToJsonElement(collect(root, trace).toString(Charsets.UTF_8)).jsonObject
        for (key in listOf("sourceCommit", "originalInitializationSpecSha256", "originalTraceSha256", "identitySha256", "stopKind",
            "executionAuthorized", "authenticatedProvenance")) {
            shouldThrowAny { verify(JsonObject(obj + (key to JsonPrimitive("forged"))).toString().toByteArray(), root, trace) }
        }
    }
    test("previously collected bytes cannot conceal newly unresolved durable intent") {
        val root = root(); val trace = run(root); val evidence = collect(root, trace)
        val dir = root.resolve(identity.fixtureId).resolve("transitions")
        Files.delete(dir.resolve("3.result"))
        shouldThrowAny { verify(evidence, root, trace) }
        Files.exists(dir.resolve("3.intent")) shouldBe true
        Files.exists(dir.resolve("3.result")) shouldBe false
    }
    test("real rejected engine action has rejected evidence without accepted state promotion") {
        val root = root(); val dir = root.resolve(identity.fixtureId)
        val trace = ManualPhaseTwoFixtureLifecycle.runNew(root, identity, {
            Files.write(dir.resolve("initialization-spec.json"), bytes())
            Files.writeString(dir.resolve("initialization-spec.sha256"), pin() + "\n")
            adapter(spec()).also { it.attachJournal(dir.resolve("transitions"), identity) }
        }, { a ->
            val before = a.state
            a.process(CastSpell(before.turnOrder.first(), EntityId("missing-synthetic-card")))
            a.state shouldBe before
            a.finish()
        }, ManualPhaseTwoStoredFixtureReplay::encode)
        val evidence = collect(root, trace)
        verify(evidence, root, trace)
        val row = Json.parseToJsonElement(evidence.toString(Charsets.UTF_8)).jsonObject.getValue("steps").jsonArray.single().jsonObject
        row.getValue("classification").jsonPrimitive.content shouldBe "REJECTED"
    }
    test("noncanonical and extra collector fields fail closed without evidence writes") {
        val root = root(); val trace = run(root); val evidence = collect(root, trace)
        shouldThrowAny { verify(" ".toByteArray() + evidence, root, trace) }
        val obj = Json.parseToJsonElement(evidence.toString(Charsets.UTF_8)).jsonObject
        shouldThrowAny { verify(JsonObject(obj + ("privateState" to JsonPrimitive("unexpected"))).toString().toByteArray(), root, trace) }
        collect(root, trace).toList() shouldBe evidence.toList()
    }
})
