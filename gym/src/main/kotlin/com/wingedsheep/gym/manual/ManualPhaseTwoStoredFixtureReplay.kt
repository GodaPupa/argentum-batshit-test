package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.registry.CardRegistry
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.security.MessageDigest

/**
 * Connects the reviewed fixture lifecycle's result.bin to the existing real-engine replay.
 * Restores a recorded initial state, never invokes an initializer, factory or pilot. This is
 * not source/runtime authentication, per-action crash recovery, or official Phase-2 admission.
 * No writer, CLI, replacement claim or replay acceptance receipt is exposed.
 */
internal object ManualPhaseTwoStoredFixtureReplay {
    private val names = setOf("fixture-identity.txt", "initialization-intent.txt", "initialized.txt",
        "run-intent.txt", "result.bin", "complete.txt")

    fun encode(trace: PhaseTwoEngineTrace): ByteArray = PhaseTwoTelemetryAdapter.JSON
        .encodeToString(PhaseTwoEngineTrace.serializer(), trace).toByteArray(Charsets.UTF_8)

    /** New trusted integration entry; the reviewed lifecycle still owns the pre-factory barrier. */
    fun runNewAndReplay(
        root: Path,
        expected: ManualFixtureIdentity,
        registry: CardRegistry,
        trustedFactory: () -> ManualPhaseTwoFixtureRunner,
    ): PhaseTwoEngineTrace {
        val actual = ManualPhaseTwoFixtureRunner.runNewFixture(root, expected, trustedFactory, ::encode)
        return verify(root.resolve(expected.fixtureId), expected, registry).also {
            require(it == actual) { "Stored replay differs from the just-recorded trace" }
        }
    }

    fun verify(directory: Path, expected: ManualFixtureIdentity, registry: CardRegistry): PhaseTwoEngineTrace {
        val identity = expected.bytes() // Detach mutable identity lists before any replay work.
        val source = expected.sourceCommit
        require(directory.isAbsolute && directory.normalize() == directory && directory.toRealPath() == directory)
        require(Files.isDirectory(directory, NOFOLLOW_LINKS) && directory.fileName.toString() == expected.fixtureId)
        fun snapshot(): Map<String, ByteArray> {
            require(directory.toRealPath() == directory && Files.isDirectory(directory, NOFOLLOW_LINKS))
            val actual = Files.list(directory).use { paths -> paths.map { it.fileName.toString() }.toList().toSet() }
            require(actual == names) { "Incomplete, failed or unexpected fixture material" }
            return names.associateWith { name ->
                val path = directory.resolve(name)
                require(Files.isRegularFile(path, NOFOLLOW_LINKS))
                val limit = if (name == "result.bin") 16L * 1024 * 1024 else 4096L
                require(Files.size(path) in 1..limit)
                Files.readAllBytes(path)
            }
        }
        val before = snapshot()
        require(before.getValue("fixture-identity.txt").contentEquals(identity))
        require(before.getValue("initialization-intent.txt").contentEquals("INITIALIZE_ONCE\n".toByteArray()))
        require(before.getValue("initialized.txt").contentEquals("TRUSTED_FACTORY_RETURNED\n".toByteArray()))
        require(before.getValue("run-intent.txt").contentEquals("RUN_ONCE\n".toByteArray()))
        val bytes = before.getValue("result.bin")
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        require(before.getValue("complete.txt").contentEquals((digest + "\n").toByteArray()))
        try {
            val trace = PhaseTwoTelemetryAdapter.JSON.decodeFromString(
                PhaseTwoEngineTrace.serializer(), bytes.toString(Charsets.UTF_8))
            require(encode(trace).contentEquals(bytes)) { "Noncanonical, corrupt or unknown trace fields" }
            require(trace.engineSourceSha == source && !trace.executionAuthorizedByThisComponent)
            require(trace.playerIds.size == 4 && trace.playerIds.distinct().size == 4 && trace.manualSeat in 0..3)
            return PhaseTwoTelemetryAdapter.replay(trace, ActionProcessor(registry)).also {
                require(encode(it).contentEquals(bytes)) { "Replayed trace differs" }
            }
        } finally {
            val after = snapshot()
            require(names.all { before.getValue(it).contentEquals(after.getValue(it)) }) {
                "Original fixture material changed during replay"
            }
        }
    }
}
