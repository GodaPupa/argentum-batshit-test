package com.wingedsheep.gym.manual

import com.wingedsheep.engine.registry.CardRegistry
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import kotlinx.serialization.json.*

/**
 * Create-once durable storage of excluded synthetic collector correspondence, outside original
 * evidence. Trusted local filesystem only. Not authenticated provenance or official admission.
 * An incomplete reservation is permanent: no repair, resume, overwrite or writer-reopen API.
 */
internal object ManualStoredReplayEvidence {
    fun publish(root: Path, fixture: Path, identity: ManualFixtureIdentity, specPin: String,
                tracePin: String, evidence: ByteArray, evidencePin: String, registry: CardRegistry): Path {
        require(root.isAbsolute && root.normalize() == root && root.toRealPath() == root)
        require(fixture.isAbsolute && fixture.normalize() == fixture && fixture.toRealPath() == fixture)
        val destination = root.resolve(identity.fixtureId)
        require(destination.parent == root && !destination.startsWith(fixture) && !fixture.startsWith(destination))
        // Reserve before semantic validation. Any failure leaves a consumed, non-reopenable slot.
        Files.createDirectory(destination)
        force(root)
        write(destination.resolve("request.json"), request(identity, specPin, tracePin, evidencePin))
        require(hash(evidence) == evidencePin)
        ManualReplayEvidence.verify(evidence, fixture, identity, specPin, tracePin, registry)
        write(destination.resolve("evidence.json"), evidence)
        write(destination.resolve("complete.sha256"), (evidencePin + "\n").toByteArray())
        return destination
    }

    /** A durable file is never sufficient alone: re-establish correspondence to current originals. */
    fun verify(destination: Path, fixture: Path, identity: ManualFixtureIdentity, specPin: String,
               tracePin: String, evidencePin: String, registry: CardRegistry) {
        require(destination.isAbsolute && destination.normalize() == destination && destination.toRealPath() == destination)
        require(destination.fileName.toString() == identity.fixtureId)
        fun snapshot() = Files.list(destination).use { paths -> paths.toList().associate { p ->
            require(Files.isRegularFile(p, NOFOLLOW_LINKS) && Files.size(p) in 1..(16L * 1024 * 1024))
            p.fileName.toString() to Files.readAllBytes(p).toList()
        } }
        val original = snapshot()
        try {
            require(original.keys == setOf("request.json", "evidence.json", "complete.sha256"))
            require(original.getValue("request.json").toByteArray().contentEquals(request(identity, specPin, tracePin, evidencePin)))
            val bytes = original.getValue("evidence.json").toByteArray()
            require(hash(bytes) == evidencePin)
            require(original.getValue("complete.sha256").toByteArray().contentEquals((evidencePin + "\n").toByteArray()))
            ManualReplayEvidence.verify(bytes, fixture, identity, specPin, tracePin, registry)
        } finally { require(snapshot() == original) { "Stored collector evidence changed" } }
    }

    private fun request(identity: ManualFixtureIdentity, spec: String, trace: String, evidence: String): ByteArray {
        require(listOf(spec, trace, evidence).all { it.matches(Regex("[0-9a-f]{64}")) })
        return buildJsonObject {
            put("schema", "manual-excluded-collector-storage-v1")
            put("identitySha256", hash(identity.bytes()))
            put("originalSpecSha256", spec); put("originalTraceSha256", trace); put("evidenceSha256", evidence)
            put("executionAuthorized", false); put("authenticatedProvenance", false)
        }.toString().toByteArray(Charsets.UTF_8)
    }
    private fun hash(bytes: ByteArray) = ManualInitializationReplay.sha256(bytes)
    private fun force(directory: Path) = FileChannel.open(directory, READ).use { it.force(true) }
    private fun write(path: Path, bytes: ByteArray) {
        require(bytes.size in 1..(16 * 1024 * 1024))
        FileChannel.open(path, CREATE_NEW, WRITE, NOFOLLOW_LINKS).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        force(path.parent)
    }
}
