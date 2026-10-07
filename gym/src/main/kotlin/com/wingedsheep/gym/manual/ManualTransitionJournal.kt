package com.wingedsheep.gym.manual

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest

/**
 * Trusted-side transition storage only. No initializer, pilot, official claim or engine is invoked.
 * The caller must source-bind the identity, canonical action and complete state/event envelopes.
 * The directory is create-only; interrupted or stopped attempts cannot be reopened for writing.
 * Result persistence precedes returning to the caller. A failed callback/write consumes the writer.
 * Storage requires a trusted local filesystem with atomic creation and working file/directory force.
 */
internal class ManualTransitionJournal private constructor(private val directory: Path) {
    private var next = 0L
    private var usable = true

    @Synchronized
    fun <T> transition(action: ByteArray, apply: () -> T, encode: (T) -> ByteArray): T {
        check(usable) { "Transition writer consumed" }
        val detached = checked(action)
        check(next < Long.MAX_VALUE)
        // Poison before any I/O/callback, including a reentrant call from trusted code.
        usable = false
        writeNew(directory.resolve("$next.intent"), detached)
        val result = apply()
        writeNew(directory.resolve("$next.result"), checked(encode(result)))
        next++
        usable = true
        return result
    }

    @Synchronized
    fun stop(rawClassification: String) {
        check(usable) { "Transition writer consumed" }
        require(rawClassification.matches(Regex("[A-Z][A-Z0-9_]{0,79}")))
        usable = false
        writeNew(directory.resolve("stop"), "$next\n$rawClassification\n".toByteArray(Charsets.UTF_8))
    }

    companion object {
        private const val LIMIT = 16 * 1024 * 1024
        /** This is transition storage inside an already source-admitted attempt, not allocation. */
        fun create(directory: Path, exactIdentity: ByteArray, initializedEnvelope: ByteArray): ManualTransitionJournal {
            val identity = checked(exactIdentity)
            val initial = checked(initializedEnvelope)
            require(directory.isAbsolute && directory.normalize() == directory)
            require(directory.parent.toRealPath() == directory.parent)
            Files.createDirectory(directory)
            forceDirectory(directory.parent)
            writeNew(directory.resolve("identity"), identity)
            writeNew(directory.resolve("initial"), initial)
            return ManualTransitionJournal(directory)
        }

        /**
         * Read-only structural inspection. Missing result remains an interrupted intent, never an
         * instruction to execute it. STOP completeness is not semantic/engine replay acceptance.
         * Exact identity and initial envelope are supplied independently by the trusted verifier.
         */
        fun inspect(directory: Path, exactIdentity: ByteArray, initializedEnvelope: ByteArray): ManualJournalInspection {
            require(directory.isAbsolute && directory.normalize() == directory && directory.toRealPath() == directory)
            require(Files.isDirectory(directory, NOFOLLOW_LINKS))
            val names = Files.list(directory).use { stream -> stream.map { it.fileName.toString() }.toArray().map { it as String }.toSet() }
            require("identity" in names && "initial" in names) { "Incomplete initialization storage" }
            require(read(directory.resolve("identity")).contentEquals(checked(exactIdentity))) { "Identity drift" }
            require(read(directory.resolve("initial")).contentEquals(checked(initializedEnvelope))) { "Initial envelope drift" }
            val expected = mutableSetOf("identity", "initial")
            val pairs = mutableListOf<ManualJournalTransition>()
            var index = 0L
            var interrupted = false
            while ("$index.intent" in names) {
                expected.add("$index.intent")
                val action = read(directory.resolve("$index.intent"))
                if ("$index.result" !in names) { interrupted = true; break }
                expected.add("$index.result")
                pairs.add(ManualJournalTransition(action, read(directory.resolve("$index.result"))))
                index++
            }
            var stop: String? = null
            if ("stop" in names) {
                require(!interrupted) { "STOP after incomplete transition" }
                expected.add("stop")
                val raw = read(directory.resolve("stop")).toString(Charsets.UTF_8)
                val fields = raw.split('\n')
                require(fields.size == 3 && fields[0] == index.toString() && fields[2].isEmpty())
                require(fields[1].matches(Regex("[A-Z][A-Z0-9_]{0,79}")))
                stop = fields[1]
            }
            require(names == expected) { "Unexpected, orphaned or noncontiguous journal entry" }
            return ManualJournalInspection(pairs, interrupted, stop)
        }

        private fun checked(bytes: ByteArray): ByteArray {
            require(bytes.isNotEmpty() && bytes.size <= LIMIT)
            return bytes.copyOf()
        }
        private fun read(path: Path): ByteArray {
            require(Files.isRegularFile(path, NOFOLLOW_LINKS) && Files.size(path) in 1..(LIMIT + 100).toLong())
            val framed = Files.readAllBytes(path)
            val newline = framed.indexOf(10.toByte())
            require(newline in 1..100) { "Truncated frame" }
            val payload = framed.copyOfRange(newline + 1, framed.size)
            val header = "${payload.size}:${sha256(payload)}"
            require(framed.copyOfRange(0, newline).toString(Charsets.US_ASCII) == header) { "Corrupt frame" }
            return checked(payload)
        }
        private fun writeNew(path: Path, bytes: ByteArray) {
            FileChannel.open(path, CREATE_NEW, WRITE, NOFOLLOW_LINKS).use { channel ->
                val header = "${bytes.size}:${sha256(bytes)}\n".toByteArray(Charsets.US_ASCII)
                val buffer = ByteBuffer.wrap(header + bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            forceDirectory(path.parent)
        }
        private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        private fun forceDirectory(path: Path) = FileChannel.open(path, READ).use { it.force(true) }
    }
}

internal data class ManualJournalTransition(val action: ByteArray, val resultEnvelope: ByteArray)
internal data class ManualJournalInspection(
    val transitions: List<ManualJournalTransition>,
    val interruptedIntent: Boolean,
    val rawStopClassification: String?,
)
