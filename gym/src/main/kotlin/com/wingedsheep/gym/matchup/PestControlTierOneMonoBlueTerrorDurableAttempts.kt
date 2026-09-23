package com.wingedsheep.gym.matchup

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE

/**
 * Durable bookkeeping only: no initializer, callbacks, runner, or execution authorization.
 * A canonical block directory is exclusively created under the supplied existing evidence root.
 * An existing claim is never reopened, overwritten, resumed, or deleted by this implementation.
 */
internal class MonoBlueTerrorDurableAttempts private constructor(
    private val directory: Path,
    private val assignments: List<MonoBlueTerrorSmokeAssignment>,
) {
    private enum class Stage { READY, ATTEMPT, INITIALIZATION_ENTRY, TERMINAL }
    private var stage = Stage.READY
    private var nextGame = 1
    private val ledger = mutableListOf<MonoBlueTerrorCoordinatorEvent>()

    @Synchronized
    fun events(): List<MonoBlueTerrorCoordinatorEvent> = ledger.toList()

    @Synchronized
    fun recordAttempt(game: Int) = transition {
        requireStage(game, Stage.READY)
        val assignment = assignments[game - 1]
        persist("game-$game.attempt", (
            "game=$game\nseed=${assignment.seed}\nseedHex=${assignment.seedHex}\n" +
                "pestSeat=${assignment.pestSeat.name}\nstartingDeck=${assignment.startingDeck.name}\n"
            ).toByteArray(Charsets.UTF_8))
        ledger += MonoBlueTerrorCoordinatorEvent(game, MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED)
        stage = Stage.ATTEMPT
    }

    /** Records entry intent only; it never calls an engine initializer. */
    @Synchronized
    fun recordInitializationEntry(game: Int) = transition {
        requireStage(game, Stage.ATTEMPT)
        persist("game-$game.initialization-entry", "game=$game\n".toByteArray(Charsets.UTF_8))
        ledger += MonoBlueTerrorCoordinatorEvent(game, MonoBlueTerrorCoordinatorEventType.INITIALIZATION_ENTERED)
        stage = Stage.INITIALIZATION_ENTRY
    }

    @Synchronized
    fun recordResult(game: Int, raw: ByteArray) = transition {
        requireStage(game, Stage.INITIALIZATION_ENTRY)
        require(raw.isNotEmpty()) { "empty result is not admissible" }
        val snapshot = raw.copyOf()
        persist("game-$game.raw", snapshot)
        persist("game-$game.record", (
            "game=$game\nrawSha256=${terrorFrozenDigest(snapshot)}\n"
            ).toByteArray(Charsets.UTF_8))
        ledger += MonoBlueTerrorCoordinatorEvent(game, MonoBlueTerrorCoordinatorEventType.RECORD_DURABLY_WRITTEN)
        nextGame++
        stage = if (nextGame > 4) Stage.TERMINAL else Stage.READY
    }

    @Synchronized
    fun reject(game: Int, reasonCode: String) = transition {
        check(stage != Stage.TERMINAL && game == nextGame && game in 1..4) { "invalid rejection position" }
        require(reasonCode.matches(Regex("[A-Z][A-Z0-9_]{0,127}"))) { "invalid reason code" }
        persist("rejected.txt", "game=$game\nreason=$reasonCode\n".toByteArray(Charsets.UTF_8))
        ledger += MonoBlueTerrorCoordinatorEvent(game, MonoBlueTerrorCoordinatorEventType.REJECTED)
        stage = Stage.TERMINAL
    }

    private fun requireStage(game: Int, expected: Stage) {
        check(stage == expected && game == nextGame && game in 1..4) { "invalid game or transition order" }
    }

    private inline fun transition(operation: () -> Unit) {
        try {
            operation()
        } catch (failure: Exception) {
            // Any write or ordering error permanently blocks this object. Disk evidence is retained.
            stage = Stage.TERMINAL
            throw failure
        }
    }

    private fun persist(name: String, bytes: ByteArray) = writeTerrorEvidence(directory.resolve(name), bytes)

    companion object {
        fun createNew(
            evidenceRoot: Path,
            assignments: List<MonoBlueTerrorSmokeAssignment>,
            executionSourceCommit: String,
        ): MonoBlueTerrorDurableAttempts {
            require(executionSourceCommit.matches(Regex("[0-9a-f]{40}")) &&
                executionSourceCommit != "0".repeat(40)) { "invalid execution source commit" }
            val snapshot = assignments.toList()
            require(snapshot.size == 4 && snapshot.map { it.gameNumber } == listOf(1, 2, 3, 4))
            require(snapshot.map { it.seed }.distinct().size == 4 && snapshot.none { it.seed == 0L })
            snapshot.forEachIndexed { index, row ->
                require(row.seedHex == terrorAssignmentSeedHex(row.seed))
                require(row.pestSeat == if (index < 2) PestSeat.SEAT_ZERO else PestSeat.SEAT_ONE)
                require(row.terrorSeat != row.pestSeat)
                require(row.startingDeck == if (index % 2 == 0) {
                    MonoBlueTerrorStartingDeck.PEST_CONTROL
                } else {
                    MonoBlueTerrorStartingDeck.MONO_BLUE_TERROR
                })
            }
            val parent = evidenceRoot.toRealPath()
            require(Files.isDirectory(parent)) { "evidence root must already exist" }
            val directory = parent.resolve(PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID)
            Files.createDirectory(directory) // Atomic claim; a second process cannot claim this block.
            forceTerrorDirectory(parent)
            val vectorHash = terrorFrozenDigest(snapshot.joinToString("\n", postfix = "\n") {
                it.seed.toString()
            }.toByteArray(Charsets.UTF_8))
            writeTerrorEvidence(directory.resolve("claim.txt"), (
                "schema=terror-durable-attempts-v1\nblockId=$PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID\n" +
                    "executionSourceCommit=$executionSourceCommit\nqualifiedRunner=$PEST_V2_QUALIFIED_RUNNER\n" +
                    "orderedVectorSha256=$vectorHash\nexpectedGames=4\n"
                ).toByteArray(Charsets.UTF_8))
            return MonoBlueTerrorDurableAttempts(directory, snapshot)
        }
    }
}

private fun writeTerrorEvidence(path: Path, bytes: ByteArray) {
    FileChannel.open(path, CREATE_NEW, WRITE).use { channel ->
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }
    forceTerrorDirectory(path.parent)
}

private fun forceTerrorDirectory(directory: Path) {
    FileChannel.open(directory, READ).use { it.force(true) }
}
