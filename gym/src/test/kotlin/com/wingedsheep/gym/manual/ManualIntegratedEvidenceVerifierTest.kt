package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.dsl.card
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.*

/** Four-seat, no smoother, excluded synthetic: not a capability-36 or 864-game run. */
class ManualIntegratedEvidenceVerifierTest : FunSpec({
    val names = listOf("Animar, Soul of Elements", "Replay Fixture Partner", "Replay Fixture Opponent")
    fun registry() = CardRegistry().apply {
        register(TestCards.all)
        names.forEach { n -> register(card(n) {
            manaCost = "{0}"; typeLine = "Legendary Creature — Human"; power = 1; toughness = 1
        }) }
    }
    val identity = ManualFixtureIdentity("manual-integrated-excluded-20261009",
        "1155fe50b538930881881897e125531ca71334a3",
        "CRUISE", List(4) { "1".repeat(64) }, List(4) { "2".repeat(64) }, "3".repeat(64))
    fun spec() = ManualInitializationSpec(
        fixtureIdentitySha256 = ManualInitializationReplay.sha256(identity.bytes()),
        seed = 0x4D414E494E4954L, startingPlayerIndex = 0, manualSeat = 0,
        players = (0..3).map { seat -> ManualReplayPlayer("EXCLUDED_REPLAY_$seat", "replay-seat-$seat",
            List(if (seat == 0) 98 else 99) { if (it % 2 == 0) "Forest" else "Island" },
            if (seat == 0) names.take(2) else listOf(names.last())) })
    fun encode() = ManualInitializationReplay.encode(spec())
    fun hash(b: ByteArray) = ManualInitializationReplay.sha256(b)
    fun root(): Path = Files.createTempDirectory("manual-integrated-excluded-").toRealPath()
    fun runner(initial: InitializationResult): ManualPhaseTwoFixtureRunner {
        val adapter = PhaseTwoTelemetryAdapter(initial.state, ActionProcessor(registry()),
            identity.sourceCommit, initial.playerIds, 0)
        return ManualPhaseTwoFixtureRunner(adapter, registry(),
            adapter.state.turnOrder.associateWith { PhaseTwoPilotPolicy { _, _ -> PhaseTwoPilotChoice.Keep } },
            ManualFixtureStepBudget(4, 60_000))
    }
    data class Case(
        val fixtureRoot: Path, val captureRoot: Path, val collectorRoot: Path,
        val pins: ManualIntegratedOriginalPins, val witness: ByteArray,
    )
    fun prepared(): Case {
        val fixtureRoot = root(); val captureRoot = root(); val collectorRoot = root()
        val specBytes = encode(); val specPin = hash(specBytes)
        val captured = ManualOriginalInitializationCapture.runNew(fixtureRoot, captureRoot,
            identity, specBytes, specPin, registry(), buildRunner = ::runner)
        val trace = captured.trace
        val tracePin = hash(ManualPhaseTwoStoredFixtureReplay.encode(trace))
        val stop = requireNotNull(trace.stopObservation)
        val reason = stop.data.getValue("reason").jsonPrimitive.content
        val witness = ManualExplicitStopCorrespondence.encode(identity, specPin, tracePin,
            trace.steps.size, stop.kind, reason)
        val evidence = ManualReplayEvidence.collect(fixtureRoot.resolve(identity.fixtureId),
            identity, specPin, tracePin, registry())
        val evidencePin = hash(evidence)
        ManualStoredReplayEvidence.publish(collectorRoot, fixtureRoot.resolve(identity.fixtureId),
            identity, specPin, tracePin, evidence, evidencePin, registry())
        return Case(fixtureRoot, captureRoot, collectorRoot,
            ManualIntegratedOriginalPins(identity, specPin, tracePin, captured.captureSha256,
                hash(witness), evidencePin, stop.kind, reason, trace.steps.size), witness)
    }
    fun files(root: Path): Map<String, List<Byte>> = Files.walk(root).use { walk ->
        walk.filter { Files.isRegularFile(it) }.toList().associate {
            root.relativize(it).toString() to Files.readAllBytes(it).toList()
        }
    }
    fun check(c: Case, expected: Boolean = true) {
        val before = listOf(c.fixtureRoot, c.captureRoot, c.collectorRoot).map(::files)
        val witnessBefore = c.witness.toList()
        val outcome = runCatching {
            ManualIntegratedEvidenceVerifier.verify(c.fixtureRoot, c.captureRoot, c.collectorRoot,
                c.pins, c.witness, registry())
        }
        outcome.isSuccess shouldBe expected
        listOf(c.fixtureRoot, c.captureRoot, c.collectorRoot).map(::files) shouldBe before
        c.witness.toList() shouldBe witnessBefore
    }
    test("four retained original components jointly verify read only; no writer or state returned") {
        val c = prepared()
        check(c); check(c)
        c.pins.expectedTransitionCount shouldBe 4
        c.pins.expectedStopKind shouldBe "RESOURCE_CAP"
        shouldThrowAny { ManualOriginalInitializationCapture.runNew(c.fixtureRoot, c.captureRoot,
            identity, encode(), hash(encode()), registry(), buildRunner = ::runner) }
    }
    test("original independent identity source and each hash cannot be substituted") {
        val c = prepared()
        for (bad in listOf(
            c.pins.copy(specification = "0".repeat(64)),
            c.pins.copy(trace = "0".repeat(64)),
            c.pins.copy(capturedInitialization = "0".repeat(64)),
            c.pins.copy(explicitStopWitness = "0".repeat(64)),
            c.pins.copy(storedCollector = "0".repeat(64)),
            c.pins.copy(identity = identity.copy(sourceCommit = "f".repeat(40))),
            c.pins.copy(identity = identity.copy(fixtureId = "foreign-fixture"))))
            check(c.copy(pins = bad), false)
    }
    test("explicit STOP kind reason or transition count cannot be rewritten even with a new witness hash") {
        val c = prepared()
        check(c.copy(pins = c.pins.copy(expectedStopKind = "TIMEOUT")), false)
        check(c.copy(pins = c.pins.copy(expectedStopReason = "altered declared reason")), false)
        check(c.copy(pins = c.pins.copy(expectedTransitionCount = 3)), false)
        val altered = ManualExplicitStopCorrespondence.encode(identity, c.pins.specification,
            c.pins.trace, c.pins.expectedTransitionCount, "TIMEOUT", c.pins.expectedStopReason)
        check(c.copy(witness = altered, pins = c.pins.copy(explicitStopWitness = hash(altered))), false)
    }
    test("missing partial faulted altered original capture fixture and collector fail without repair") {
        for (kind in listOf("capture", "fixture", "collector", "fault", "stale", "extra")) {
            val c = prepared()
            when (kind) {
                "capture" -> Files.delete(c.captureRoot.resolve(identity.fixtureId).resolve("capture.json"))
                "fixture" -> Files.delete(c.fixtureRoot.resolve(identity.fixtureId).resolve("transitions/3.result"))
                "collector" -> Files.delete(c.collectorRoot.resolve(identity.fixtureId).resolve("complete.sha256"))
                "fault" -> Files.writeString(c.captureRoot.resolve(identity.fixtureId).resolve("fault.txt"), "FAULT\n")
                "stale" -> Files.writeString(c.collectorRoot.resolve(identity.fixtureId).resolve("evidence.json"), "{}")
                "extra" -> Files.writeString(c.fixtureRoot.resolve(identity.fixtureId).resolve("unknown"), "EXTRA\n")
            }
            check(c, false)
        }
    }
    test("cross-directory substitution or overlapping independent roots must refuse") {
        val c = prepared()
        check(c.copy(captureRoot = c.collectorRoot), false)
        check(c.copy(collectorRoot = c.fixtureRoot), false)
        val foreign = prepared()
        check(c.copy(collectorRoot = foreign.collectorRoot), false)
        check(c.copy(captureRoot = foreign.captureRoot), false)
    }
})
