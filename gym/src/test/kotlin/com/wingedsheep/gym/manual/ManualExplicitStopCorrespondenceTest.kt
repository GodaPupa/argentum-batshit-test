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
class ManualExplicitStopCorrespondenceTest : FunSpec({
    val names = listOf("Animar, Soul of Elements", "Replay Fixture Partner", "Replay Fixture Opponent")
    fun registry() = CardRegistry().apply {
        register(TestCards.all)
        names.forEach { name -> register(card(name) {
            manaCost = "{0}"; typeLine = "Legendary Creature — Human"; power = 1; toughness = 1
        }) }
    }
    val identity = ManualFixtureIdentity("manual-fixture-initialization-replay", "6cf03d4e84672444f7b33c8abcf9338b4e2d37e6",
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

    fun explicit(root: Path, kind: String, reason: String, rejected: Boolean = false): PhaseTwoEngineTrace {
        val dir = root.resolve(identity.fixtureId)
        return ManualPhaseTwoFixtureLifecycle.runNew(root, identity, {
            Files.write(dir.resolve("initialization-spec.json"), bytes())
            Files.writeString(dir.resolve("initialization-spec.sha256"), pin() + "\n")
            adapter(spec()).also { it.attachJournal(dir.resolve("transitions"), identity) }
        }, { a ->
            if (rejected) a.process(CastSpell(a.state.turnOrder.first(), EntityId("missing-synthetic-card")))
            else {
                a.state.turnOrder.toList().forEach { a.process(KeepHand(it)).error shouldBe null }
                a.stop(kind, reason)
            }
            a.finish()
        }, ManualPhaseTwoStoredFixtureReplay::encode)
    }
    fun witness(trace: PhaseTwoEngineTrace, kind: String, reason: String, count: Int = 4) =
        ManualExplicitStopCorrespondence.encode(identity, pin(), tracePin(trace), count, kind, reason)
    fun verifyStop(b: ByteArray, root: Path, trace: PhaseTwoEngineTrace, p: String = ManualInitializationReplay.sha256(b)) =
        ManualExplicitStopCorrespondence.verify(b, p, root.resolve(identity.fixtureId), identity, pin(), tracePin(trace), registry())

    test("three explicit stop kinds bind exact retained reason after four real accepted actions") {
        for (kind in listOf("RESOURCE_CAP", "TIMEOUT", "INTEGRITY_FAILURE")) {
            val root = root(); val reason = "EXCLUDED fixture boundary: $kind"
            val trace = explicit(root, kind, reason); val b = witness(trace, kind, reason)
            verifyStop(b, root, trace)
            trace.steps.size shouldBe 4
            trace.steps.all { it.accepted == true } shouldBe true
        }
    }
    test("changed reason kind or transition position fails even with a matching new witness hash") {
        val root = root(); val trace = explicit(root, "RESOURCE_CAP", "EXCLUDED four-step cap")
        for (bad in listOf(witness(trace, "RESOURCE_CAP", "different reason"),
            witness(trace, "TIMEOUT", "EXCLUDED four-step cap"), witness(trace, "RESOURCE_CAP", "EXCLUDED four-step cap", 3))) {
            shouldThrowAny { verifyStop(bad, root, trace) }
        }
    }
    test("original witness pin and original trace or identity pins cannot be substituted") {
        val root = root(); val trace = explicit(root, "TIMEOUT", "EXCLUDED synthetic deadline")
        val b = witness(trace, "TIMEOUT", "EXCLUDED synthetic deadline")
        shouldThrowAny { verifyStop(b, root, trace, "0".repeat(64)) }
        shouldThrowAny { ManualExplicitStopCorrespondence.verify(b, ManualInitializationReplay.sha256(b),
            root.resolve(identity.fixtureId), identity, pin(), "0".repeat(64), registry()) }
        shouldThrowAny { ManualExplicitStopCorrespondence.verify(b, ManualInitializationReplay.sha256(b),
            root.resolve(identity.fixtureId), identity.copy(sourceCommit = "9".repeat(40)), pin(), tracePin(trace), registry()) }
    }
    test("noncanonical extra fields and promoted authority fail byte correspondence") {
        val root = root(); val trace = explicit(root, "RESOURCE_CAP", "EXCLUDED fixed cap")
        val b = witness(trace, "RESOURCE_CAP", "EXCLUDED fixed cap")
        val obj = Json.parseToJsonElement(b.toString(Charsets.UTF_8)).jsonObject
        for (bad in listOf(" ".toByteArray() + b,
            JsonObject(obj + ("executionAuthorized" to JsonPrimitive(true))).toString().toByteArray(),
            JsonObject(obj + ("extra" to JsonPrimitive("x"))).toString().toByteArray())) {
            shouldThrowAny { verifyStop(bad, root, trace) }
        }
        verifyStop(b, root, trace)
    }
    test("retained reason cannot certify an unresolved transition and does not repair evidence") {
        val root = root(); val trace = explicit(root, "RESOURCE_CAP", "EXCLUDED fixed cap")
        val b = witness(trace, "RESOURCE_CAP", "EXCLUDED fixed cap")
        val missing = root.resolve(identity.fixtureId).resolve("transitions/3.result")
        Files.delete(missing)
        shouldThrowAny { verifyStop(b, root, trace) }
        Files.exists(missing) shouldBe false
    }
    test("engine rejection without explicit stop observation cannot acquire an invented reason") {
        val root = root(); val trace = explicit(root, "INTEGRITY_FAILURE", "unused", rejected = true)
        trace.steps.single().accepted shouldBe false
        trace.stopObservation shouldBe null
        val b = witness(trace, "INTEGRITY_FAILURE", "invented explicit reason", 1)
        shouldThrowAny { verifyStop(b, root, trace) }
        check(root, trace) shouldBe trace
    }
})
