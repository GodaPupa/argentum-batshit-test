package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.dsl.card
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.*

/** Excluded synthetic component fixtures only; no official vector or opponent package. */
class ManualOriginalInitializationCaptureTest : FunSpec({
    val json = PhaseTwoTelemetryAdapter.JSON
    val names = listOf("Animar, Soul of Elements", "Replay Fixture Partner", "Replay Fixture Opponent")
    fun registry() = CardRegistry().apply {
        register(TestCards.all)
        names.forEach { name -> register(card(name) {
            manaCost = "{0}"; typeLine = "Legendary Creature — Human"; power = 1; toughness = 1
        }) }
    }
    val identity = ManualFixtureIdentity("manual-fixture-original-capture", "c180d1cc91f687dc24650de909fa57b3cd6f7e95",
        "CRUISE", List(4) { "1".repeat(64) }, List(4) { "2".repeat(64) }, "3".repeat(64))
    fun hash(bytes: ByteArray) = ManualInitializationReplay.sha256(bytes)
    fun spec() = ManualInitializationSpec(fixtureIdentitySha256 = hash(identity.bytes()),
        seed = 0x4D414E494E4954L, startingPlayerIndex = 0, manualSeat = 0,
        players = (0..3).map { seat -> ManualReplayPlayer("EXCLUDED_REPLAY_$seat", "replay-seat-$seat",
            List(if (seat == 0) 98 else 99) { if (it % 2 == 0) "Forest" else "Island" },
            if (seat == 0) names.take(2) else listOf(names.last())) })
    fun bytes() = ManualInitializationReplay.encode(spec())
    fun root(): Path {
        val evidence = System.getenv("MANUAL_CAPTURE_EVIDENCE_ROOT")
        return (if (evidence == null) Files.createTempDirectory("manual-original-capture-")
            else Files.createTempDirectory(Path.of(evidence), "fixture-")).toRealPath()
    }
    fun runner(i: InitializationResult): ManualPhaseTwoFixtureRunner {
        val adapter = PhaseTwoTelemetryAdapter(i.state, ActionProcessor(registry()), identity.sourceCommit, i.playerIds, 0)
        return ManualPhaseTwoFixtureRunner(adapter, registry(), adapter.state.turnOrder.associateWith {
            PhaseTwoPilotPolicy { _, _ -> PhaseTwoPilotChoice.Keep }
        }, ManualFixtureStepBudget(4, 60_000))
    }
    fun run(f: Path, c: Path, failure: ManualCaptureFailure? = null,
            build: (InitializationResult) -> ManualPhaseTwoFixtureRunner = ::runner) =
        ManualOriginalInitializationCapture.runNew(f, c, identity, bytes(), hash(bytes()), registry(), failure, build)
    fun verify(f: Path, c: Path, r: ManualCapturedFixture, pin: String = r.captureSha256) =
        ManualOriginalInitializationCapture.verify(c.resolve(identity.fixtureId), pin, f.resolve(identity.fixtureId),
            identity, hash(bytes()), hash(ManualPhaseTwoStoredFixtureReplay.encode(r.trace)), registry())
    fun capture(c: Path) = c.resolve(identity.fixtureId).resolve("capture.json")
    fun snapshot(root: Path) = Files.walk(root).use { paths -> paths.filter { Files.isRegularFile(it) }
        .toList().associate { root.relativize(it).toString() to Files.readAllBytes(it).toList() } }
    fun replaceCapture(c: Path, b: ByteArray): String {
        Files.write(capture(c), b)
        Files.writeString(c.resolve(identity.fixtureId).resolve("complete.txt"), hash(b) + "\n")
        return hash(b)
    }

    test("actual initializer events and state are forced before runner handoff and match accepted replay") {
        val f = root(); val c = root(); var calls = 0
        val r = run(f, c) { original ->
            calls++
            val d = c.resolve(identity.fixtureId)
            Files.readAllBytes(d.resolve("specification.json")).toList() shouldBe bytes().toList()
            Files.readAllBytes(f.resolve(identity.fixtureId).resolve("initialization-spec.json")).toList() shouldBe bytes().toList()
            Files.exists(f.resolve(identity.fixtureId).resolve("initialization-intent.txt")) shouldBe true
            Files.exists(f.resolve(identity.fixtureId).resolve("initialized.txt")) shouldBe false
            val b = Files.readAllBytes(capture(c)); val obj = json.parseToJsonElement(b.toString(Charsets.UTF_8)).jsonObject
            Files.readString(d.resolve("complete.txt")) shouldBe hash(b) + "\n"
            obj.getValue("events") shouldBe JsonArray(original.events.map { json.encodeToJsonElement(GameEvent.serializer(), it) })
            obj.getValue("initialState") shouldBe json.encodeToJsonElement(GameState.serializer(), original.state)
            obj.getValue("authenticatedProvenance") shouldBe JsonPrimitive(false)
            obj.getValue("executionAuthorized") shouldBe JsonPrimitive(false)
            original.events.size shouldBe 36
            original.events.filterIsInstance<LibraryShuffledEvent>().size shouldBe 4
            original.events.filterIsInstance<ZoneChangeEvent>().size shouldBe 28
            original.events.filterIsInstance<CardsDrawnEvent>().size shouldBe 4
            runner(original)
        }
        calls shouldBe 1
        val before = snapshot(f) to snapshot(c)
        verify(f, c, r)
        (snapshot(f) to snapshot(c)) shouldBe before
        shouldThrowAny { run(f, c) { calls++; runner(it) } }
        calls shouldBe 1
        (snapshot(f) to snapshot(c)) shouldBe before
    }
    test("each injected local write interruption consumes attempt without exposing state or repairing evidence") {
        for (point in ManualCaptureFailure.entries) {
            val f = root(); val c = root(); var calls = 0
            shouldThrowAny { run(f, c, point) { calls++; runner(it) } }
            calls shouldBe 0
            val fd = f.resolve(identity.fixtureId); val cd = c.resolve(identity.fixtureId)
            Files.exists(cd.resolve("intent.txt")) shouldBe true
            Files.exists(cd.resolve("fault.txt")) shouldBe true
            Files.exists(fd.resolve("fault.txt")) shouldBe true
            Files.exists(fd.resolve("initialized.txt")) shouldBe false
            Files.exists(fd.resolve("transitions")) shouldBe false
            if (point == ManualCaptureFailure.PARTIAL_CAPTURE) {
                (Files.size(capture(c)) > 0) shouldBe true
                shouldThrowAny { json.parseToJsonElement(Files.readString(capture(c))) }
            }
            val before = snapshot(f) to snapshot(c)
            shouldThrowAny { run(f, c) { calls++; runner(it) } }
            shouldThrowAny { ManualOriginalInitializationCapture.verify(cd, "0".repeat(64), fd,
                identity, hash(bytes()), "0".repeat(64), registry()) }
            calls shouldBe 0
            (snapshot(f) to snapshot(c)) shouldBe before
        }
    }
    test("missing duplicated reordered and substituted event captures fail even with recomputed pins") {
        val f = root(); val c = root(); val r = run(f, c)
        val raw = Files.readAllBytes(capture(c)); val obj = json.parseToJsonElement(raw.toString(Charsets.UTF_8)).jsonObject
        val e = obj.getValue("events").jsonArray
        for (bad in listOf(e.drop(1), e + e.first(), e.reversed(), e.toMutableList().also { it[0] = e[1] })) {
            val b = JsonObject(obj + ("events" to JsonArray(bad))).toString().toByteArray()
            val pin = replaceCapture(c, b); val before = snapshot(c)
            shouldThrowAny { verify(f, c, r, pin) }
            snapshot(c) shouldBe before
        }
    }
    test("missing truncated and unresolved capture records cannot be certified or reopened") {
        for (name in listOf("capture.json", "complete.txt", "intent.txt")) {
            val f = root(); val c = root(); val r = run(f, c)
            Files.delete(c.resolve(identity.fixtureId).resolve(name))
            val before = snapshot(c)
            shouldThrowAny { verify(f, c, r) }
            shouldThrowAny { run(f, c) }
            snapshot(c) shouldBe before
        }
        val f = root(); val c = root(); val r = run(f, c)
        val b = Files.readAllBytes(capture(c)).copyOf(40)
        val pin = replaceCapture(c, b)
        shouldThrowAny { verify(f, c, r, pin) }
    }
    test("changed state identity seed or authority and noncanonical capture fail semantic correspondence") {
        val f = root(); val c = root(); val r = run(f, c)
        val raw = Files.readAllBytes(capture(c)); val obj = json.parseToJsonElement(raw.toString(Charsets.UTF_8)).jsonObject
        val changes = listOf("initialState" to JsonNull, "identitySha256" to JsonPrimitive("0".repeat(64)),
            "seed" to JsonPrimitive(spec().seed + 1), "authenticatedProvenance" to JsonPrimitive(true),
            "executionAuthorized" to JsonPrimitive(true), "extra" to JsonPrimitive("unexpected"))
        for ((key, value) in changes) {
            val pin = replaceCapture(c, JsonObject(obj + (key to value)).toString().toByteArray())
            shouldThrowAny { verify(f, c, r, pin) }
        }
        val pin = replaceCapture(c, " ".toByteArray() + raw)
        shouldThrowAny { verify(f, c, r, pin) }
    }
    test("original specification and external pins remain mandatory and unresolved transitions are not repaired") {
        val f = root(); val c = root(); val r = run(f, c)
        shouldThrowAny { verify(f, c, r, "0".repeat(64)) }
        for ((s, t) in listOf("0".repeat(64) to hash(ManualPhaseTwoStoredFixtureReplay.encode(r.trace)),
            hash(bytes()) to "0".repeat(64))) {
            shouldThrowAny { ManualOriginalInitializationCapture.verify(c.resolve(identity.fixtureId), r.captureSha256,
                f.resolve(identity.fixtureId), identity, s, t, registry()) }
        }
        val missing = f.resolve(identity.fixtureId).resolve("transitions/3.result")
        Files.delete(missing)
        val before = snapshot(f) to snapshot(c)
        shouldThrowAny { verify(f, c, r) }
        (snapshot(f) to snapshot(c)) shouldBe before
    }
    test("invalid original specification fails before reservation and real initializer failure stays consumed") {
        val f = root(); val c = root(); var calls = 0
        shouldThrowAny { ManualOriginalInitializationCapture.runNew(f, c, identity, bytes(), "0".repeat(64), registry()) {
            calls++; runner(it)
        } }
        Files.exists(c.resolve(identity.fixtureId)) shouldBe false
        Files.exists(f.resolve(identity.fixtureId)) shouldBe false
        // Empty registry makes the real initializer fail to resolve the explicitly named commanders.
        shouldThrowAny { ManualOriginalInitializationCapture.runNew(f, c, identity, bytes(), hash(bytes()), CardRegistry()) {
            calls++; runner(it)
        } }
        calls shouldBe 0
        Files.exists(c.resolve(identity.fixtureId).resolve("fault.txt")) shouldBe true
        Files.exists(f.resolve(identity.fixtureId).resolve("initialized.txt")) shouldBe false
        val before = snapshot(f) to snapshot(c)
        shouldThrowAny { run(f, c) }
        (snapshot(f) to snapshot(c)) shouldBe before
    }
    test("runner construction failure preserves original capture but cannot certify a completed fixture") {
        val f = root(); val c = root(); var calls = 0
        shouldThrowAny { run(f, c) { calls++; error("EXCLUDED_RUNNER_CONSTRUCTION_FAILURE") } }
        calls shouldBe 1
        Files.exists(capture(c)) shouldBe true
        Files.exists(c.resolve(identity.fixtureId).resolve("complete.txt")) shouldBe true
        Files.exists(c.resolve(identity.fixtureId).resolve("fault.txt")) shouldBe true
        Files.exists(f.resolve(identity.fixtureId).resolve("initialized.txt")) shouldBe false
        val before = snapshot(f) to snapshot(c)
        shouldThrowAny { run(f, c) { calls++; runner(it) } }
        calls shouldBe 1
        (snapshot(f) to snapshot(c)) shouldBe before
    }
})
