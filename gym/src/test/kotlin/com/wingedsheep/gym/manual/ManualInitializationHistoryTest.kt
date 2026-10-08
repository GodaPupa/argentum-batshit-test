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
class ManualInitializationHistoryTest : FunSpec({
    val names = listOf("Animar, Soul of Elements", "Replay Fixture Partner", "Replay Fixture Opponent")
    fun registry() = CardRegistry().apply {
        register(TestCards.all)
        names.forEach { name -> register(card(name) {
            manaCost = "{0}"; typeLine = "Legendary Creature — Human"; power = 1; toughness = 1
        }) }
    }
    val identity = ManualFixtureIdentity("manual-fixture-initialization-replay", "dc10c36ca682f7c97d7273724c4251b9a78a1e2d",
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


    fun history(trace: PhaseTwoEngineTrace, events: List<GameEvent> = initialize(spec()).events) =
        ManualInitializationHistory.encode(identity, pin(), tracePin(trace), trace.initialStateSha256, events)
    fun verifyHistory(b: ByteArray, root: Path, trace: PhaseTwoEngineTrace,
                      h: String = ManualInitializationReplay.sha256(b)) =
        ManualInitializationHistory.verify(b, h, root.resolve(identity.fixtureId), identity, pin(), tracePin(trace), registry())

    test("original initializer emits exact ordered four shuffles and four seven-card draw histories") {
        val root = root(); val trace = run(root)
        val original = initialize(spec()); val events = original.events
        events.size shouldBe 36
        events.take(4).all { it is LibraryShuffledEvent } shouldBe true
        events.filterIsInstance<ZoneChangeEvent>().size shouldBe 28
        events.filterIsInstance<CardsDrawnEvent>().size shouldBe 4
        verifyHistory(history(trace, events), root, trace)
        check(root, trace) shouldBe trace
    }
    test("missing duplicate reordered and substituted real events fail even with recomputed history pins") {
        val root = root(); val trace = run(root); val events = initialize(spec()).events
        for (bad in listOf(events.drop(1), events + events.first(), events.reversed(),
            events.toMutableList().also { it[0] = events[1] })) {
            shouldThrowAny { verifyHistory(history(trace, bad), root, trace) }
        }
    }
    test("external history spec trace and identity pins cannot be replaced") {
        val root = root(); val trace = run(root); val b = history(trace)
        shouldThrowAny { verifyHistory(b, root, trace, "0".repeat(64)) }
        for ((s, t) in listOf("0".repeat(64) to tracePin(trace), pin() to "0".repeat(64))) {
            shouldThrowAny { ManualInitializationHistory.verify(b, ManualInitializationReplay.sha256(b),
                root.resolve(identity.fixtureId), identity, s, t, registry()) }
        }
        shouldThrowAny { ManualInitializationHistory.verify(b, ManualInitializationReplay.sha256(b),
            root.resolve(identity.fixtureId), identity.copy(sourceCommit = "9".repeat(40)), pin(), tracePin(trace), registry()) }
    }
    test("noncanonical extra fields and authority promotion fail exact history correspondence") {
        val root = root(); val trace = run(root); val b = history(trace)
        val obj = Json.parseToJsonElement(b.toString(Charsets.UTF_8)).jsonObject
        for (bad in listOf(" ".toByteArray() + b,
            JsonObject(obj + ("authenticatedProvenance" to JsonPrimitive(true))).toString().toByteArray(),
            JsonObject(obj + ("extra" to JsonPrimitive("x"))).toString().toByteArray())) {
            shouldThrowAny { verifyHistory(bad, root, trace) }
        }
    }
    test("history cannot conceal unresolved transition or repair missing original result") {
        val root = root(); val trace = run(root); val b = history(trace)
        val missing = root.resolve(identity.fixtureId).resolve("transitions/3.result")
        Files.delete(missing)
        shouldThrowAny { verifyHistory(b, root, trace) }
        Files.exists(missing) shouldBe false
    }
    test("different deterministic initialization events cannot replace original history") {
        val root = root(); val trace = run(root)
        val other = initialize(spec().copy(seed = spec().seed + 1)).events
        (other == initialize(spec()).events) shouldBe false
        shouldThrowAny { verifyHistory(history(trace, other), root, trace) }
        verifyHistory(history(trace), root, trace)
    }
})
