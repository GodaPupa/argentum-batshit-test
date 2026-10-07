package com.wingedsheep.gym.manual

import com.wingedsheep.engine.registry.CardRegistry
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*

/**
 * Synthetic-only composition: accepted lifecycle reserves the attempt before this wrapper writes
 * the exact spec and independent pin; both are forced before the trusted initializer is called.
 * The factory must use that supplied spec. Post-run reconstruction detects mismatching initial
 * states, but does not authenticate factory/deck/policy/runtime provenance or replay actions.
 */
internal object ManualBoundInitialization {
    private val JSON = Json { encodeDefaults = true; explicitNulls = true }
    fun runNew(root: Path, identity: ManualFixtureIdentity, specification: ByteArray,
               expectedSpecificationSha256: String, registry: CardRegistry,
               initializeRunner: (ManualInitializationSpec) -> ManualPhaseTwoFixtureRunner): PhaseTwoEngineTrace {
        val frozen = identity.copy(deckSha256 = identity.deckSha256.toList(), pilotSha256 = identity.pilotSha256.toList())
        val bytes = specification.copyOf()
        val spec = validate(bytes, expectedSpecificationSha256, frozen)
        val directory = root.resolve(frozen.fixtureId)
        val trace = ManualPhaseTwoFixtureRunner.runNewJournaledFixture(root, frozen, {
            writeNew(directory.resolve("initialization-spec.json"), bytes)
            writeNew(directory.resolve("initialization-spec.sha256"), (expectedSpecificationSha256 + "\n").toByteArray())
            initializeRunner(spec)
        }, ManualPhaseTwoStoredFixtureReplay::encode)
        verify(directory, frozen, expectedSpecificationSha256, registry)
        return trace
    }

    /** Reads committed spec bytes; expected pin remains independently supplied by trusted evidence. */
    fun verify(directory: Path, identity: ManualFixtureIdentity, expectedSpecificationSha256: String,
               registry: CardRegistry): ManualInitializationCheck {
        require(directory.isAbsolute && directory.normalize() == directory && directory.toRealPath() == directory)
        require(directory.fileName.toString() == identity.fixtureId)
        fun read(name: String, limit: Long): ByteArray {
            val path = directory.resolve(name)
            require(Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.size(path) in 1..limit)
            return Files.readAllBytes(path)
        }
        val bytes = read("initialization-spec.json", 1024L * 1024)
        val marker = read("initialization-spec.sha256", 65)
        require(marker.contentEquals((expectedSpecificationSha256 + "\n").toByteArray()))
        validate(bytes, expectedSpecificationSha256, identity)
        val result = ManualInitializationReplay.verify(directory.resolve("transitions"), bytes,
            expectedSpecificationSha256, identity, registry)
        require(bytes.contentEquals(read("initialization-spec.json", 1024L * 1024)) &&
            marker.contentEquals(read("initialization-spec.sha256", 65))) { "Committed specification changed during verification" }
        return result
    }

    private fun validate(bytes: ByteArray, pin: String, identity: ManualFixtureIdentity): ManualInitializationSpec {
        require(bytes.size in 1..(1024 * 1024) && pin.matches(Regex("[0-9a-f]{64}")))
        require(ManualInitializationReplay.sha256(bytes) == pin) { "Specification pin mismatch" }
        val spec = JSON.decodeFromString(ManualInitializationSpec.serializer(), bytes.toString(Charsets.UTF_8))
        require(ManualInitializationReplay.encode(spec).contentEquals(bytes))
        require(spec.schema == "MANUAL_SYNTHETIC_INITIALIZATION_V1")
        require(spec.fixtureIdentitySha256 == ManualInitializationReplay.sha256(identity.bytes()))
        return spec
    }
    private fun writeNew(path: Path, bytes: ByteArray) {
        FileChannel.open(path, CREATE_NEW, WRITE, NOFOLLOW_LINKS).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        FileChannel.open(path.parent, READ).use { it.force(true) }
    }
}
