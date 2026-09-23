package com.wingedsheep.gym.matchup

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE

internal class MonoBlueTerrorReplicationDurableAttempts private constructor(
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
        persist(
            "game-$game.attempt",
            (
                "game=$game\nseed=${assignment.seed}\nseedHex=${assignment.seedHex}\n" +
                    "pestSeat=${assignment.pestSeat.name}\nstartingDeck=${assignment.startingDeck.name}\n"
                ).toByteArray(Charsets.UTF_8),
        )
        ledger += MonoBlueTerrorCoordinatorEvent(
            game,
            MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED,
        )
        stage = Stage.ATTEMPT
    }

    @Synchronized
    fun recordInitializationEntry(game: Int) = transition {
        requireStage(game, Stage.ATTEMPT)
        persist(
            "game-$game.initialization-entry",
            "game=$game\n".toByteArray(Charsets.UTF_8),
        )
        ledger += MonoBlueTerrorCoordinatorEvent(
            game,
            MonoBlueTerrorCoordinatorEventType.INITIALIZATION_ENTERED,
        )
        stage = Stage.INITIALIZATION_ENTRY
    }

    @Synchronized
    fun recordResult(game: Int, raw: ByteArray) = transition {
        requireStage(game, Stage.INITIALIZATION_ENTRY)
        require(raw.isNotEmpty()) { "empty replication result is not admissible" }
        val snapshot = raw.copyOf()
        persist("game-$game.raw", snapshot)
        persist(
            "game-$game.record",
            "game=$game\nrawSha256=${terrorFrozenDigest(snapshot)}\n"
                .toByteArray(Charsets.UTF_8),
        )
        ledger += MonoBlueTerrorCoordinatorEvent(
            game,
            MonoBlueTerrorCoordinatorEventType.RECORD_DURABLY_WRITTEN,
        )
        nextGame++
        stage =
            if (nextGame > PEST_MONO_BLUE_TERROR_REPLICATION_GAMES) Stage.TERMINAL
            else Stage.READY
    }

    @Synchronized
    fun reject(game: Int, reasonCode: String) = transition {
        check(
            stage != Stage.TERMINAL &&
                game == nextGame &&
                game in 1..PEST_MONO_BLUE_TERROR_REPLICATION_GAMES
        ) { "invalid replication rejection position" }
        require(reasonCode.matches(Regex("[A-Z][A-Z0-9_]{0,127}"))) {
            "invalid replication rejection code"
        }
        persist(
            "rejected.txt",
            "game=$game\nreason=$reasonCode\n".toByteArray(Charsets.UTF_8),
        )
        ledger += MonoBlueTerrorCoordinatorEvent(
            game,
            MonoBlueTerrorCoordinatorEventType.REJECTED,
        )
        stage = Stage.TERMINAL
    }

    private fun requireStage(game: Int, expected: Stage) {
        check(
            stage == expected &&
                game == nextGame &&
                game in 1..PEST_MONO_BLUE_TERROR_REPLICATION_GAMES
        ) { "invalid replication game or transition order" }
    }

    private inline fun transition(operation: () -> Unit) {
        try {
            operation()
        } catch (failure: Exception) {
            stage = Stage.TERMINAL
            throw failure
        }
    }

    private fun persist(name: String, bytes: ByteArray) =
        writeReplicationEvidence(directory.resolve(name), bytes)

    companion object {
        fun createNew(
            evidenceRoot: Path,
            assignments: List<MonoBlueTerrorSmokeAssignment>,
            executionSourceCommit: String,
        ): MonoBlueTerrorReplicationDurableAttempts {
            require(
                executionSourceCommit.matches(Regex("[0-9a-f]{40}")) &&
                    executionSourceCommit != "0".repeat(40)
            ) { "invalid replication execution source commit" }

            val snapshot = assignments.toList()
            require(snapshot.size == PEST_MONO_BLUE_TERROR_REPLICATION_GAMES)
            require(
                snapshot.map { it.gameNumber } ==
                    (1..PEST_MONO_BLUE_TERROR_REPLICATION_GAMES).toList()
            )
            require(snapshot.map { it.seed }.distinct().size == snapshot.size)
            require(snapshot.none { it.seed == 0L })

            val cells =
                PestControlTierOneMonoBlueTerrorReplicationExecutionInputLoader.replicationCells()
            snapshot.zip(cells).forEach { (row, cell) ->
                require(row.seedHex == terrorAssignmentSeedHex(row.seed))
                require(row.gameNumber == cell.gameNumber)
                require(row.pestSeat == cell.pestSeat)
                require(row.terrorSeat != row.pestSeat)
                require(row.startingDeck == cell.startingDeck)
            }

            val parent = evidenceRoot.toRealPath()
            require(Files.isDirectory(parent)) { "replication evidence root must already exist" }
            val directory = parent.resolve(PEST_MONO_BLUE_TERROR_REPLICATION_BLOCK_ID)
            Files.createDirectory(directory)
            forceReplicationDirectory(parent)

            val vectorHash = terrorFrozenDigest(
                snapshot.joinToString("\n", postfix = "\n") { it.seed.toString() }
                    .toByteArray(Charsets.UTF_8)
            )
            require(vectorHash == PEST_MONO_BLUE_TERROR_REPLICATION_VECTOR_SHA256)
            writeReplicationEvidence(
                directory.resolve("claim.txt"),
                (
                    "schema=terror-replication-durable-attempts-v1\n" +
                        "blockId=$PEST_MONO_BLUE_TERROR_REPLICATION_BLOCK_ID\n" +
                        "executionSourceCommit=$executionSourceCommit\n" +
                        "qualifiedRunner=$PEST_V2_QUALIFIED_RUNNER\n" +
                        "orderedVectorSha256=$vectorHash\n" +
                        "expectedGames=$PEST_MONO_BLUE_TERROR_REPLICATION_GAMES\n"
                    ).toByteArray(Charsets.UTF_8),
            )
            return MonoBlueTerrorReplicationDurableAttempts(directory, snapshot)
        }
    }
}

private fun writeReplicationEvidence(path: Path, bytes: ByteArray) {
    FileChannel.open(path, CREATE_NEW, WRITE).use { channel ->
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }
    forceReplicationDirectory(path.parent)
}

private fun forceReplicationDirectory(directory: Path) {
    FileChannel.open(directory, READ).use { it.force(true) }
}
