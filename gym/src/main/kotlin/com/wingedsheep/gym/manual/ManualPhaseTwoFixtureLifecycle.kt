package com.wingedsheep.gym.manual

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.security.MessageDigest

/** Explicit four-seat SYNTHETIC fixture identity; never an official Phase-2 allocation. */
internal data class ManualFixtureIdentity(
    val fixtureId: String,
    val sourceCommit: String,
    val gear: String,
    val deckSha256: List<String>,
    val pilotSha256: List<String>,
    val runtimeSha256: String,
) {
    fun bytes(): ByteArray {
        require(fixtureId.matches(Regex("manual-fixture-[A-Za-z0-9._-]{1,80}")))
        require(sourceCommit.matches(Regex("[0-9a-f]{40}")))
        require(gear in setOf("CRUISE", "SPORT", "RACE"))
        require(deckSha256.size == 4 && pilotSha256.size == 4)
        require((deckSha256 + pilotSha256 + runtimeSha256).all { it.matches(Regex("[0-9a-f]{64}")) })
        return (listOf("MANUAL_SYNTHETIC_FIXTURE_V1", fixtureId, sourceCommit, gear) +
            deckSha256.toList() + pilotSha256.toList() + runtimeSha256)
            .joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)
    }
}

/**
 * Trusted fixture initialization ownership. Reservation and initialization intent are durable
 * before the factory is called. Existing/partial directories are never reopened or deleted.
 * This is NOT an official claim, per-action journal, engine replay, or verification that the
 * supplied identity pins describe the factory; that source/admission check remains external.
 */
internal object ManualPhaseTwoFixtureLifecycle {
    fun <S, R> runNew(
        root: Path,
        identity: ManualFixtureIdentity,
        trustedInitialize: () -> S,
        trustedRun: (S) -> R,
        trustedEncodeResult: (R) -> ByteArray,
    ): R {
        val claim = identity.bytes() // Detach caller-owned lists before any callback.
        require(root.isAbsolute && root.normalize() == root && root.toRealPath() == root)
        require(Files.isDirectory(root, NOFOLLOW_LINKS))
        val directory = root.resolve(identity.fixtureId)
        Files.createDirectory(directory)
        forceDirectory(root)
        try {
            writeNew(directory.resolve("fixture-identity.txt"), claim)
            writeNew(directory.resolve("initialization-intent.txt"), "INITIALIZE_ONCE\n".toByteArray())
            val state = trustedInitialize()
            writeNew(directory.resolve("initialized.txt"), "TRUSTED_FACTORY_RETURNED\n".toByteArray())
            writeNew(directory.resolve("run-intent.txt"), "RUN_ONCE\n".toByteArray())
            val result = trustedRun(state)
            val bytes = trustedEncodeResult(result).copyOf()
            require(bytes.isNotEmpty() && bytes.size <= 16 * 1024 * 1024) { "Invalid fixture result envelope" }
            writeNew(directory.resolve("result.bin"), bytes)
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            writeNew(directory.resolve("complete.txt"), (hash + "\n").toByteArray())
            return result
        } catch (failure: Throwable) {
            // Preserve the original failure even if no storage remains for the failure marker.
            try {
                writeNew(directory.resolve("fault.txt"), (failure.javaClass.name + "\n").toByteArray())
            } catch (recordingFailure: Throwable) { failure.addSuppressed(recordingFailure) }
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
}
