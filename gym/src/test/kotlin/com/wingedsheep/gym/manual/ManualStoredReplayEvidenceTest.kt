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
class ManualStoredReplayEvidenceTest : FunSpec({
    val names = listOf("Animar, Soul of Elements", "Replay Fixture Partner", "Replay Fixture Opponent")
    fun registry() = CardRegistry().apply {
        register(TestCards.all)
        names.forEach { name -> register(card(name) {
            manaCost = "{0}"; typeLine = "Legendary Creature — Human"; power = 1; toughness = 1
        }) }
    }
    val identity = ManualFixtureIdentity("manual-fixture-initialization-replay", "8687be658de4e3192133f57703d580e7c77f3c98",
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

    fun sink() = Files.createTempDirectory("manual-excluded-collector-").toRealPath()
    fun hash(b: ByteArray) = ManualInitializationReplay.sha256(b)
    fun publish(sink: Path, root: Path, trace: PhaseTwoEngineTrace, b: ByteArray = collect(root, trace), p: String = hash(b)) =
        ManualStoredReplayEvidence.publish(sink, root.resolve(identity.fixtureId), identity, pin(), tracePin(trace), b, p, registry())
    fun inspect(sink: Path, root: Path, trace: PhaseTwoEngineTrace, p: String) =
        ManualStoredReplayEvidence.verify(sink.resolve(identity.fixtureId), root.resolve(identity.fixtureId),
            identity, pin(), tracePin(trace), p, registry())

    test("durable exact collector survives independent reader and denies second publication") {
        val root = root(); val trace = run(root); val sink = sink(); val b = collect(root, trace)
        val dir = publish(sink, root, trace, b)
        Files.readAllBytes(dir.resolve("evidence.json")).toList() shouldBe b.toList()
        inspect(sink, root, trace, hash(b))
        shouldThrowAny { publish(sink, root, trace, b) }
        inspect(sink, root, trace, hash(b))
    }
    test("semantic rejection retains reservation and cannot be repaired by valid resubmission") {
        val root = root(); val trace = run(root); val sink = sink()
        val bad = "{}".toByteArray()
        shouldThrowAny { publish(sink, root, trace, bad) }
        val dir = sink.resolve(identity.fixtureId)
        Files.exists(dir.resolve("request.json")) shouldBe true
        Files.exists(dir.resolve("evidence.json")) shouldBe false
        shouldThrowAny { publish(sink, root, trace) }
        shouldThrowAny { inspect(sink, root, trace, hash(bad)) }
    }
    test("missing completion leaves evidence visible but never certifies or reopens") {
        val root = root(); val trace = run(root); val sink = sink(); val b = collect(root, trace)
        val dir = publish(sink, root, trace, b); Files.delete(dir.resolve("complete.sha256"))
        shouldThrowAny { inspect(sink, root, trace, hash(b)) }
        Files.readAllBytes(dir.resolve("evidence.json")).toList() shouldBe b.toList()
        shouldThrowAny { publish(sink, root, trace, b) }
    }
    test("pin drift altered bytes extra files and symlink evidence fail closed") {
        for (kind in listOf("pin", "bytes", "extra", "symlink")) {
            val root = root(); val trace = run(root); val sink = sink(); val b = collect(root, trace)
            val dir = publish(sink, root, trace, b)
            when (kind) {
                "bytes" -> Files.writeString(dir.resolve("evidence.json"), "{}")
                "extra" -> Files.writeString(dir.resolve("unexpected"), "x")
                "symlink" -> {
                    val target = Files.createTempFile("collector-copy", ".json")
                    Files.write(target, b); Files.delete(dir.resolve("evidence.json"))
                    Files.createSymbolicLink(dir.resolve("evidence.json"), target)
                }
            }
            shouldThrowAny { inspect(sink, root, trace, if (kind == "pin") "0".repeat(64) else hash(b)) }
        }
    }
    test("stored completion does not conceal subsequently unresolved original transition") {
        val root = root(); val trace = run(root); val sink = sink(); val b = collect(root, trace)
        val dir = publish(sink, root, trace, b)
        Files.delete(root.resolve(identity.fixtureId).resolve("transitions/3.result"))
        shouldThrowAny { inspect(sink, root, trace, hash(b)) }
        Files.readAllBytes(dir.resolve("evidence.json")).toList() shouldBe b.toList()
    }
    test("identity and original reference substitution fail without changing stored records") {
        val root = root(); val trace = run(root); val sink = sink(); val b = collect(root, trace)
        val dir = publish(sink, root, trace, b); val request = Files.readAllBytes(dir.resolve("request.json")).toList()
        shouldThrowAny { ManualStoredReplayEvidence.verify(dir, root.resolve(identity.fixtureId),
            identity.copy(sourceCommit = "9".repeat(40)), pin(), tracePin(trace), hash(b), registry()) }
        shouldThrowAny { ManualStoredReplayEvidence.verify(dir, root.resolve(identity.fixtureId),
            identity, "0".repeat(64), tracePin(trace), hash(b), registry()) }
        Files.readAllBytes(dir.resolve("request.json")).toList() shouldBe request
    }
})
