package com.wingedsheep.gym.sphinx

import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CodingErrorAction
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest
import java.util.Base64

/** Fixed synthetic-runner identity. No Stage-E allocation/seed or official admission is accepted. */
internal data class SphinxRunnerFixtureIdentity(
    val fixtureId: String,
    val sourceCommit: String,
    val ownDeckSha256: String,
    val ownPilotSha256: String,
    val opponentDeckSha256: String,
    val opponentPilotSha256: String,
    val runtimeSha256: String,
) {
    fun claimBytes(): ByteArray {
        require(fixtureId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")))
        require(sourceCommit.matches(Regex("[0-9a-f]{40}")))
        val pins = listOf(ownDeckSha256, ownPilotSha256, opponentDeckSha256, opponentPilotSha256, runtimeSha256)
        require(pins.all { it.matches(Regex("[0-9a-f]{64}")) })
        return (listOf("SPHINX_RUNNER_FIXTURE_ONLY_V1", fixtureId, sourceCommit) + pins)
            .joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)
    }
}

/**
 * Prospective trusted collector component, deliberately fixture-only.
 * Claim and every intent are forced before invoking an initialization/submission callback.
 * Callbacks must be trusted runner code, never pilot callbacks. Payloads are opaque serialized
 * initialization/action/result bytes. This class does NOT validate engine semantics, pilot
 * information boundaries, qualification, source provenance, or grant Stage-E execution authority.
 * The accepted Ponder transition store and replay implementation are unchanged.
 */
internal class SphinxStageEWriteAheadJournal private constructor(
    val directory: Path,
    private val identity: SphinxRunnerFixtureIdentity,
    private val channel: FileChannel,
) : Closeable {
    internal data class Entry(val kind: String, val payload: String)
    internal data class Inspection(val entries: List<Entry>, val chainSha256: String, val phase: String) {
        val hasUnfinishedIntent: Boolean get() = phase == "INITIALIZING" || phase == "SUBMITTING"
        val structurallyComplete: Boolean get() = phase == "STOPPED"
    }

    private var count = 0
    private var previous = digest(identity.claimBytes())
    private var phase = "CLAIMED"
    private var poisoned = false
    private var closed = false

    @Synchronized
    fun initialize(trustedInitializer: () -> String) = mutate {
        check(phase == "CLAIMED") { "Initializer may run only once" }
        append("INIT_INTENT", "initialize-once")
        append("INITIALIZED", trustedInitializer())
    }

    @Synchronized
    fun submit(actionPayload: String, trustedSubmitter: () -> String) = mutate {
        check(phase == "READY") { "No submission before initialization or after an unfinished intent/stop" }
        append("ACTION_INTENT", actionPayload)
        append("RESULT", trustedSubmitter())
    }

    @Synchronized
    fun finish(reason: String) = mutate {
        require(reason in setOf("CAPABILITY_STOP", "ENGINE_TERMINAL", "RESOURCE_STOP", "FAULT"))
        check(phase == "READY") { "Cannot close over an unfinished intent" }
        append("STOP", reason)
    }

    private fun mutate(operation: () -> Unit) {
        check(!closed && !poisoned) { "Closed or failed attempt cannot be retried" }
        try { operation() } catch (failure: Throwable) { poisoned = true; throw failure }
    }

    private fun append(kind: String, payload: String) {
        check(!closed && !poisoned)
        require(payload.isNotBlank())
        val raw = payload.toByteArray(Charsets.UTF_8)
        require(raw.size <= MAX_PAYLOAD_BYTES && strictUtf8(raw) == payload)
        val next = transition(phase, kind)
        val before = inspect(directory, identity)
        require(before.entries.size == count && before.chainSha256 == previous && before.phase == phase) {
            "Journal changed outside the owning writer"
        }
        val encoded = Base64.getEncoder().encodeToString(raw)
        val unsigned = "$count|$previous|$kind|$encoded"
        val hash = digest(unsigned.toByteArray(Charsets.UTF_8))
        val bytes = "$unsigned|$hash\n".toByteArray(Charsets.UTF_8)
        require(channel.size() + bytes.size <= MAX_FILE_BYTES) { "Synthetic fixture storage bound" }
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
        count++
        previous = hash
        phase = next
    }

    @Synchronized
    override fun close() {
        if (!closed) { closed = true; channel.close() }
    }

    companion object {
        // Bounds belong to this synthetic collector fixture, not the official Stage-E game caps.
        private const val MAX_PAYLOAD_BYTES = 1024 * 1024
        private const val MAX_FILE_BYTES = 16L * 1024 * 1024

        fun createFixture(root: Path, identity: SphinxRunnerFixtureIdentity): SphinxStageEWriteAheadJournal {
            val claim = identity.claimBytes()
            require(root.isAbsolute && root.normalize() == root && root.toRealPath() == root)
            require(Files.isDirectory(root, NOFOLLOW_LINKS))
            val directory = root.resolve(identity.fixtureId)
            Files.createDirectory(directory) // Existing/partial fixture identity remains consumed.
            forceDirectory(root)
            writeNew(directory.resolve("claim.txt"), claim)
            val channel = FileChannel.open(directory.resolve("journal.txt"), CREATE_NEW, WRITE)
            try {
                channel.force(true)
                forceDirectory(directory)
                return SphinxStageEWriteAheadJournal(directory, identity, channel)
            } catch (failure: Throwable) {
                channel.close()
                throw failure // Never delete a reservation or partial evidence.
            }
        }

        /** Structural read-only inspection; NOT engine replay or acceptance of an outcome. */
        fun inspect(directory: Path, expected: SphinxRunnerFixtureIdentity): Inspection {
            require(directory.isAbsolute && directory.normalize() == directory && directory.toRealPath() == directory)
            val claimPath = directory.resolve("claim.txt")
            val journalPath = directory.resolve("journal.txt")
            require(directory.fileName.toString() == expected.fixtureId)
            require(Files.isRegularFile(claimPath, NOFOLLOW_LINKS) && Files.isRegularFile(journalPath, NOFOLLOW_LINKS))
            require(Files.size(claimPath) <= 4096 && Files.size(journalPath) <= MAX_FILE_BYTES)
            val claim = Files.readAllBytes(claimPath)
            require(claim.contentEquals(expected.claimBytes())) { "Claim/source/deck/pilot/opponent/runtime binding drift" }
            val bytes = Files.readAllBytes(journalPath)
            require(bytes.isEmpty() || bytes.last() == '\n'.code.toByte()) { "Torn journal tail" }
            val text = strictUtf8(bytes)
            val rows = if (text.isEmpty()) emptyList() else text.dropLast(1).split('\n')
            var previous = digest(claim)
            var phase = "CLAIMED"
            val entries = rows.mapIndexed { index, row ->
                val fields = row.split('|')
                require(fields.size == 5 && fields[0] == index.toString() && fields[1] == previous)
                require(fields[4].matches(Regex("[0-9a-f]{64}")))
                val unsigned = fields.take(4).joinToString("|")
                require(digest(unsigned.toByteArray(Charsets.UTF_8)) == fields[4]) { "Hash chain drift" }
                val payloadBytes = Base64.getDecoder().decode(fields[3])
                require(payloadBytes.size <= MAX_PAYLOAD_BYTES && Base64.getEncoder().encodeToString(payloadBytes) == fields[3])
                val payload = strictUtf8(payloadBytes)
                require(payload.isNotBlank())
                if (fields[2] == "INIT_INTENT") require(payload == "initialize-once")
                if (fields[2] == "STOP") require(payload in setOf("CAPABILITY_STOP", "ENGINE_TERMINAL", "RESOURCE_STOP", "FAULT"))
                phase = transition(phase, fields[2])
                previous = fields[4]
                Entry(fields[2], payload)
            }
            return Inspection(entries.toList(), previous, phase)
        }

        private fun transition(phase: String, kind: String): String = when (phase to kind) {
            "CLAIMED" to "INIT_INTENT" -> "INITIALIZING"
            "INITIALIZING" to "INITIALIZED" -> "READY"
            "READY" to "ACTION_INTENT" -> "SUBMITTING"
            "SUBMITTING" to "RESULT" -> "READY"
            "READY" to "STOP" -> "STOPPED"
            else -> error("Invalid journal order: $phase -> $kind")
        }

        private fun strictUtf8(bytes: ByteArray): String = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()

        private fun digest(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }

        private fun forceDirectory(path: Path) = FileChannel.open(path, READ).use { it.force(true) }
        private fun writeNew(path: Path, bytes: ByteArray) {
            FileChannel.open(path, CREATE_NEW, WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            forceDirectory(path.parent)
        }
    }
}
