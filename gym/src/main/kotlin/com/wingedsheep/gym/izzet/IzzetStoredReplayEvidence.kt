package com.wingedsheep.gym.izzet

import com.wingedsheep.engine.registry.CardRegistry
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest
import kotlinx.serialization.json.*

/**
 * Izzet-specific excluded collector storage. Adapted from ManualStoredReplayEvidence blob
 * 1511167696eee8497952dde4997ad94b3374f564; this new composition requires its own review.
 * Trusted local namespace only: no authenticated provenance, rollback resistance or admission.
 * Failed reservations remain consumed. No resume, repair or writer-reopen API.
 */
internal object IzzetStoredReplayEvidence {
    fun publish(root: Path, attempt: Path, identity: IzzetSyntheticAttemptIdentity,
                initialPin: String, intentPin: String, resultPin: String,
                evidence: ByteArray, evidencePin: String, registry: CardRegistry): Path {
        require(root.isAbsolute && root.normalize() == root && root.toRealPath() == root)
        require(attempt.isAbsolute && attempt.normalize() == attempt && attempt.toRealPath() == attempt)
        val destination = root.resolve(identity.attemptId)
        require(destination.parent == root && !destination.startsWith(attempt) && !attempt.startsWith(destination))
        Files.createDirectory(destination)
        force(root)
        write(destination.resolve("request.json"), request(identity, initialPin, intentPin, resultPin, evidencePin))
        val detached = evidence.copyOf()
        require(hash(detached) == evidencePin)
        IzzetReplayEvidence.verify(detached, attempt, identity, initialPin, intentPin, resultPin, registry)
        write(destination.resolve("evidence.json"), detached)
        write(destination.resolve("complete.sha256"), (evidencePin + "\n").toByteArray())
        return destination
    }

    fun verify(destination: Path, attempt: Path, identity: IzzetSyntheticAttemptIdentity,
               initialPin: String, intentPin: String, resultPin: String, evidencePin: String, registry: CardRegistry) {
        require(destination.isAbsolute && destination.normalize() == destination && destination.toRealPath() == destination)
        require(destination.fileName.toString() == identity.attemptId)
        fun snapshot() = Files.list(destination).use { paths -> paths.toList().associate { p ->
            require(Files.isRegularFile(p, NOFOLLOW_LINKS) && Files.size(p) in 1..(16L * 1024 * 1024))
            p.fileName.toString() to Files.readAllBytes(p).toList()
        } }
        val original = snapshot()
        try {
            require(original.keys == setOf("request.json", "evidence.json", "complete.sha256"))
            require(original.getValue("request.json").toByteArray().contentEquals(
                request(identity, initialPin, intentPin, resultPin, evidencePin)))
            val bytes = original.getValue("evidence.json").toByteArray()
            require(hash(bytes) == evidencePin)
            require(original.getValue("complete.sha256").toByteArray().contentEquals((evidencePin + "\n").toByteArray()))
            IzzetReplayEvidence.verify(bytes, attempt, identity, initialPin, intentPin, resultPin, registry)
        } finally { require(snapshot() == original) { "Stored collector evidence changed" } }
    }

    private fun request(identity: IzzetSyntheticAttemptIdentity, initial: String, intent: String,
                        result: String, evidence: String): ByteArray {
        require(listOf(initial, intent, result, evidence).all { it.matches(Regex("[0-9a-f]{64}")) })
        return buildJsonObject {
            put("schema", "izzet-excluded-collector-storage-v1")
            put("identitySha256", hash(identity.bytes()))
            put("initialStateSha256", initial); put("originalIntentSha256", intent)
            put("originalResultSha256", result); put("evidenceSha256", evidence)
            put("executionAuthorized", false); put("authenticatedProvenance", false)
        }.toString().toByteArray(Charsets.UTF_8)
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it.toInt() and 255) }
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
