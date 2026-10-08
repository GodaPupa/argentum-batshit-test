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
class ManualBoundActionReplayTest : FunSpec({
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
    test("reconstructed initialization and four ordered real accepted actions match durable evidence") {
        val root = root(); val trace = run(root)
        check(root, trace) shouldBe trace
        trace.steps.size shouldBe 4
        trace.steps.all { it.accepted == true } shouldBe true
    }
    test("real rejected action replays exactly without accepted state promotion") {
        val root = root(); val dir = root.resolve(identity.fixtureId)
        val trace = ManualPhaseTwoFixtureLifecycle.runNew(root, identity, {
            Files.write(dir.resolve("initialization-spec.json"), bytes())
            Files.writeString(dir.resolve("initialization-spec.sha256"), pin() + "\n")
            adapter(spec()).also { it.attachJournal(dir.resolve("transitions"), identity) }
        }, { a ->
            val before = a.state
            a.process(CastSpell(before.turnOrder.first(), EntityId("missing-synthetic-card"))).error?.isNotBlank() shouldBe true
            a.state shouldBe before
            a.finish()
        }, ManualPhaseTwoStoredFixtureReplay::encode)
        check(root, trace) shouldBe trace
        trace.steps.single().accepted shouldBe false
    }
    test("missing or duplicated records reject without rewriting evidence") {
        for (duplicate in listOf(false, true)) {
            val root = root(); val trace = run(root); val dir = root.resolve(identity.fixtureId).resolve("transitions")
            if (duplicate) Files.copy(dir.resolve("0.intent"), dir.resolve("4.intent")) else Files.delete(dir.resolve("1.intent"))
            val kept = Files.readAllBytes(dir.resolve("0.result")).toList()
            shouldThrowAny { check(root, trace) }
            Files.readAllBytes(dir.resolve("0.result")).toList() shouldBe kept
        }
    }
    test("reordered complete pairs reject even with intact storage checksums") {
        val root = root(); val trace = run(root); val dir = root.resolve(identity.fixtureId).resolve("transitions")
        for (suffix in listOf("intent", "result")) {
            val a = Files.readAllBytes(dir.resolve("0.$suffix")); val b = Files.readAllBytes(dir.resolve("1.$suffix"))
            Files.write(dir.resolve("0.$suffix"), b); Files.write(dir.resolve("1.$suffix"), a)
        }
        shouldThrowAny { check(root, trace) }
    }
    test("altered canonical action and result envelopes reject after checksums are recomputed") {
        for (suffix in listOf("intent", "result")) {
            val root = root(); val trace = run(root); val path = root.resolve(identity.fixtureId).resolve("transitions/0.$suffix")
            val obj = Json.parseToJsonElement(payload(path).toString(Charsets.UTF_8)).jsonObject
            val changed = JsonObject(obj + (if (suffix == "intent") "source" to JsonPrimitive("9".repeat(40))
                else "classification" to JsonPrimitive("REJECTED")))
            frame(path, changed.toString().toByteArray())
            shouldThrowAny { check(root, trace) }
        }
    }
    test("unresolved intent and missing stop never become replay instructions") {
        val root = root(); val trace = run(root); val dir = root.resolve(identity.fixtureId).resolve("transitions")
        Files.delete(dir.resolve("3.result")); Files.delete(dir.resolve("stop"))
        shouldThrowAny { check(root, trace) }
        Files.exists(dir.resolve("3.result")) shouldBe false
        Files.exists(dir.resolve("3.intent")) shouldBe true
        shouldThrowAny { run(root) }
    }
    test("independent trace pin initialization pin and source identity drift reject") {
        val root = root(); val trace = run(root); val dir = root.resolve(identity.fixtureId)
        shouldThrowAny { ManualBoundActionReplay.verify(dir, identity, pin(), "0".repeat(64), registry()) }
        shouldThrowAny { ManualBoundActionReplay.verify(dir, identity, "0".repeat(64), tracePin(trace), registry()) }
        shouldThrowAny { ManualBoundActionReplay.verify(dir, identity.copy(sourceCommit = "9".repeat(40)), pin(), tracePin(trace), registry()) }
    }
    test("altered full response and stop classification reject semantically") {
        val root = root(); val trace = run(root); val dir = root.resolve(identity.fixtureId).resolve("transitions")
        val path = dir.resolve("0.result")
        val obj = Json.parseToJsonElement(payload(path).toString(Charsets.UTF_8)).jsonObject
        val response = obj.getValue("response").jsonObject
        frame(path, JsonObject(obj + ("response" to JsonObject(response + ("events" to JsonArray(emptyList()))))).toString().toByteArray())
        // Also change STOP to a structurally valid but false classification.
        frame(dir.resolve("stop"), "4\nTIMEOUT\n".toByteArray())
        shouldThrowAny { check(root, trace) }
    }
})
