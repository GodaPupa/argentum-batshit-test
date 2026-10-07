package com.wingedsheep.gym.izzet

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest

/** Declared synthetic identity only; pin provenance and opponent identity are external blockers. */
internal data class IzzetSyntheticAttemptIdentity(val attemptId: String, val pins: IzzetSourcePins) {
    fun bytes(): ByteArray {
        require(attemptId.matches(Regex("izzet-synthetic-[A-Za-z0-9._-]{1,80}")))
        return buildJsonObject {
            put("schema", "IZZET_SYNTHETIC_ATTEMPT_V1")
            put("attemptId", attemptId)
            put("sourceCommit", pins.sourceCommit)
            put("deckSha256", pins.deckSha256)
            put("policySha256", pins.policySha256)
            put("runtimeSha256", pins.runtimeSha256)
        }.toString().toByteArray(Charsets.UTF_8)
    }
}

/**
 * Create-only pre-initialization barrier for excluded synthetic attempts on a trusted local FS.
 * Identity and intent are forced before the initializer is called. Initialization evidence is
 * forced before the result is returned. Any partial or failed directory permanently consumes
 * that identity; no reopening/retry/recovery API exists. No engine, seed, policy or game loop.
 * This primitive does not authenticate pins or qualify codecs, journals, replay or collectors.
 */
internal object IzzetSyntheticAttempt {
    fun <T> initializeOnce(root: Path, identity: IzzetSyntheticAttemptIdentity,
                           initialize: () -> T, encodeInitial: (T) -> ByteArray): T {
        val identityBytes = identity.bytes()
        require(root.isAbsolute && root.normalize() == root && root.toRealPath() == root)
        require(Files.isDirectory(root, NOFOLLOW_LINKS))
        val directory = root.resolve(identity.attemptId)
        Files.createDirectory(directory)
        forceDirectory(root)
        try {
            writeNew(directory.resolve("identity.json"), identityBytes)
            writeNew(directory.resolve("initialization-intent"), "INITIALIZE_ONCE\n".toByteArray())
            val initialized = initialize()
            val bytes = encodeInitial(initialized).copyOf()
            require(bytes.size in 1..(16 * 1024 * 1024)) { "Invalid initial envelope" }
            writeNew(directory.resolve("initial.bin"), bytes)
            writeNew(directory.resolve("initialized.sha256"), (sha256(bytes) + "\n").toByteArray())
            return initialized
        } catch (failure: Throwable) {
            try { writeNew(directory.resolve("fault"), (failure.javaClass.name + "\n").toByteArray()) }
            catch (recordingFailure: Throwable) { failure.addSuppressed(recordingFailure) }
            throw failure
        }
    }

    private fun writeNew(path: Path, bytes: ByteArray) {
        FileChannel.open(path, CREATE_NEW, WRITE, NOFOLLOW_LINKS).use { channel ->
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
            channel.force(true)
        }
        forceDirectory(path.parent)
    }
    private fun forceDirectory(path: Path) = FileChannel.open(path, READ).use { it.force(true) }
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
}
