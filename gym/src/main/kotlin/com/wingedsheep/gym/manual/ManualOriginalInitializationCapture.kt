package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.json.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*

internal data class ManualCapturedFixture(val trace: PhaseTwoEngineTrace, val captureSha256: String)

/** Deterministic local I/O fault injection, only for excluded component qualification. */
internal enum class ManualCaptureFailure { BEFORE_INITIALIZER, PARTIAL_CAPTURE, AFTER_CAPTURE, AFTER_COMPLETE }

/**
 * Original writer for the fixed excluded synthetic initialization profile. The capture directory
 * is separate because accepted fixture replay requires its exact existing layout. Neither accepted
 * predecessor files nor their contracts change. A reserved slot is never reopened, even on failure.
 * Local forced writes are not authenticated custody, rollback protection or power-loss guarantees.
 */
internal object ManualOriginalInitializationCapture {
    private val json = PhaseTwoTelemetryAdapter.JSON
    private val names = setOf("identity.txt", "specification.json", "specification.sha256",
        "intent.txt", "capture.json", "complete.txt")
    private fun hash(b: ByteArray) = ManualInitializationReplay.sha256(b)

    fun runNew(fixtureRoot: Path, captureRoot: Path, identity: ManualFixtureIdentity,
               specification: ByteArray, specPin: String, registry: CardRegistry,
               failure: ManualCaptureFailure? = null,
               buildRunner: (InitializationResult) -> ManualPhaseTwoFixtureRunner): ManualCapturedFixture {
        val frozen = identity.copy(deckSha256 = identity.deckSha256.toList(), pilotSha256 = identity.pilotSha256.toList())
        val bytes = specification.copyOf()
        val spec = validate(bytes, specPin, frozen)
        for (root in listOf(fixtureRoot, captureRoot)) {
            require(root.isAbsolute && root.normalize() == root && root.toRealPath() == root)
            require(Files.isDirectory(root, NOFOLLOW_LINKS))
        }
        require(!fixtureRoot.startsWith(captureRoot) && !captureRoot.startsWith(fixtureRoot))
        val directory = captureRoot.resolve(frozen.fixtureId)
        Files.createDirectory(directory) // consume before any initializer; no reopen/repair path
        try {
            force(captureRoot)
            write(directory.resolve("identity.txt"), frozen.bytes())
            write(directory.resolve("specification.json"), bytes)
            write(directory.resolve("specification.sha256"), (specPin + "\n").toByteArray())
            write(directory.resolve("intent.txt"), "CAPTURE_ORIGINAL_ONCE\n".toByteArray())
            var pin: String? = null
            val trace = ManualBoundInitialization.runNew(fixtureRoot, frozen, bytes, specPin, registry) {
                inject(failure, ManualCaptureFailure.BEFORE_INITIALIZER)
                // This invocation supplies both captured events and the eventual runner state.
                // No caller-supplied event array or reconstruct-and-substitute API exists.
                val original = initialize(spec, registry)
                val payload = encode(frozen, specPin, original)
                write(directory.resolve("capture.json"), payload,
                    failure == ManualCaptureFailure.PARTIAL_CAPTURE)
                inject(failure, ManualCaptureFailure.AFTER_CAPTURE)
                require(Files.readAllBytes(directory.resolve("capture.json")).contentEquals(payload))
                // Reconstruction is a comparison only; it is never the captured payload or handoff.
                require(payload.contentEquals(encode(frozen, specPin, initialize(spec, registry))))
                pin = hash(payload)
                write(directory.resolve("complete.txt"), (pin + "\n").toByteArray())
                inject(failure, ManualCaptureFailure.AFTER_COMPLETE)
                val snapshot = snapshot(directory)
                require(snapshot.getValue("capture.json").toByteArray().contentEquals(payload))
                require(snapshot.getValue("specification.json").toByteArray().contentEquals(bytes))
                buildRunner(original)
            }
            val capturePin = requireNotNull(pin)
            val tracePin = hash(ManualPhaseTwoStoredFixtureReplay.encode(trace))
            verify(directory, capturePin, fixtureRoot.resolve(frozen.fixtureId), frozen, specPin, tracePin, registry)
            return ManualCapturedFixture(trace, capturePin)
        } catch (error: Throwable) {
            try { write(directory.resolve("fault.txt"), (error.javaClass.name + "\n").toByteArray()) }
            catch (recording: Throwable) { error.addSuppressed(recording) }
            throw error
        }
    }

    /** Read-only; pins must be independently retained by the trusted caller, not inferred here. */
    fun verify(directory: Path, capturePin: String, fixtureDirectory: Path, identity: ManualFixtureIdentity,
               specPin: String, tracePin: String, registry: CardRegistry) {
        val frozen = identity.copy(deckSha256 = identity.deckSha256.toList(), pilotSha256 = identity.pilotSha256.toList())
        require(directory.fileName.toString() == frozen.fixtureId)
        require(capturePin.matches(Regex("[0-9a-f]{64}")))
        val original = snapshot(directory)
        fun bytes(name: String) = original.getValue(name).toByteArray()
        try {
            require(bytes("identity.txt").contentEquals(frozen.bytes()))
            require(bytes("intent.txt").contentEquals("CAPTURE_ORIGINAL_ONCE\n".toByteArray()))
            require(bytes("specification.sha256").contentEquals((specPin + "\n").toByteArray()))
            val spec = validate(bytes("specification.json"), specPin, frozen)
            val capture = bytes("capture.json")
            require(hash(capture) == capturePin)
            require(bytes("complete.txt").contentEquals((capturePin + "\n").toByteArray()))
            val reconstructed = initialize(spec, registry)
            require(capture.contentEquals(encode(frozen, specPin, reconstructed))) { "Original capture differs" }
            val trace = ManualBoundActionReplay.verify(fixtureDirectory, frozen, specPin, tracePin, registry)
            require(Files.readAllBytes(fixtureDirectory.resolve("initialization-spec.json"))
                .contentEquals(bytes("specification.json")))
            val obj = json.parseToJsonElement(capture.toString(Charsets.UTF_8)).jsonObject
            require(obj.getValue("initialState") == trace.initialState)
            // Use the actual retained event bytes, not the reconstructed event array, in the
            // accepted history verifier; bind them to the original trace and specification.
            val events = obj.getValue("events").jsonArray.map { json.decodeFromJsonElement(GameEvent.serializer(), it) }
            val history = ManualInitializationHistory.encode(frozen, specPin, tracePin, trace.initialStateSha256, events)
            ManualInitializationHistory.verify(history, hash(history), fixtureDirectory, frozen, specPin, tracePin, registry)
        } finally {
            require(snapshot(directory) == original) { "Capture changed during verification" }
        }
    }

    private fun validate(bytes: ByteArray, pin: String, identity: ManualFixtureIdentity): ManualInitializationSpec {
        require(bytes.size in 1..(1024 * 1024) && pin.matches(Regex("[0-9a-f]{64}")) && hash(bytes) == pin)
        val s = json.decodeFromString(ManualInitializationSpec.serializer(), bytes.toString(Charsets.UTF_8))
        require(ManualInitializationReplay.encode(s).contentEquals(bytes))
        require(s.schema == "MANUAL_SYNTHETIC_INITIALIZATION_V1" && s.fixtureIdentitySha256 == hash(identity.bytes()))
        require(s.players.size == 4 && s.manualSeat in 0..3 && s.startingPlayerIndex in 0..3)
        require(s.players.map { it.playerId }.distinct().size == 4)
        s.players.forEach {
            require(it.name.isNotBlank() && it.playerId.isNotBlank())
            require(it.commanders.size in 1..2 && it.commanders.distinct().size == it.commanders.size)
            require(it.cards.size + it.commanders.size == 100 && (it.cards + it.commanders).all(String::isNotBlank))
        }
        return s
    }

    private fun initialize(s: ManualInitializationSpec, registry: CardRegistry): InitializationResult =
        GameInitializer(registry).initializeGame(GameConfig(
            players = s.players.map { PlayerConfig(it.name, Deck(it.cards), playerId = EntityId(it.playerId),
                commanderCardNames = it.commanders) }, format = Format.Commander(), startingHandSize = 7,
            skipMulligans = false, useHandSmoother = false, startingPlayerIndex = s.startingPlayerIndex, seed = s.seed))

    private fun encode(identity: ManualFixtureIdentity, specPin: String, result: InitializationResult): ByteArray =
        buildJsonObject {
            put("schema", "manual-excluded-original-initializer-capture-v1")
            put("executionAuthorized", false); put("authenticatedProvenance", false)
            put("identitySha256", hash(identity.bytes())); put("specificationSha256", specPin)
            put("seed", result.seed); put("playerIds", JsonArray(result.playerIds.map { JsonPrimitive(it.value) }))
            put("initialState", json.encodeToJsonElement(GameState.serializer(), result.state))
            put("events", JsonArray(result.events.map { json.encodeToJsonElement(GameEvent.serializer(), it) }))
        }.toString().toByteArray(Charsets.UTF_8)

    private fun snapshot(directory: Path): Map<String, List<Byte>> {
        require(directory.isAbsolute && directory.normalize() == directory && directory.toRealPath() == directory)
        require(Files.isDirectory(directory, NOFOLLOW_LINKS))
        require(Files.list(directory).use { it.map { p -> p.fileName.toString() }.toList().toSet() } == names)
        return names.associateWith { name ->
            val p = directory.resolve(name)
            require(Files.isRegularFile(p, NOFOLLOW_LINKS) && Files.size(p) in 1..(16L * 1024 * 1024))
            Files.readAllBytes(p).toList()
        }
    }
    private fun inject(actual: ManualCaptureFailure?, point: ManualCaptureFailure) {
        if (actual == point) throw java.io.IOException("EXCLUDED_INJECTED_$point")
    }
    private fun force(directory: Path) { FileChannel.open(directory, READ).use { it.force(true) } }
    private fun write(path: Path, bytes: ByteArray, partial: Boolean = false) {
        require(bytes.size in 1..(16 * 1024 * 1024))
        FileChannel.open(path, CREATE_NEW, WRITE, NOFOLLOW_LINKS).use { channel ->
            val buffer = ByteBuffer.wrap(if (partial) bytes.copyOf(bytes.size / 2) else bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
            if (partial) throw java.io.IOException("EXCLUDED_INJECTED_PARTIAL_CAPTURE")
        }
        force(path.parent)
    }
}
