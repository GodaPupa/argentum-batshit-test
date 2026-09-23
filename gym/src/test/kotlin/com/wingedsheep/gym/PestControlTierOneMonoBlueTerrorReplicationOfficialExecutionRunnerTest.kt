package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.CREATE_NEW
import java.nio.file.StandardOpenOption.READ
import java.nio.file.StandardOpenOption.WRITE
import kotlin.time.Duration.Companion.hours

private const val REPLICATION_TERROR_EXECUTE_MODE = "EXECUTE"
private const val REPLICATION_TERROR_EXECUTION_ACK =
    "AUTOMATIC_ONE_SHOT_PRIMARY_REPLICATION_EXECUTION"

class PestControlTierOneMonoBlueTerrorReplicationOfficialExecutionRunnerTest : FunSpec({
    val enabled =
        System.getenv("PEST_TERROR_REPLICATION_OFFICIAL_MODE") ==
            REPLICATION_TERROR_EXECUTE_MODE

    test("execute frozen Mono-Blue Terror twelve-game replication exactly once").config(
        enabled = enabled,
        timeout = 12.hours,
    ) {
        System.getenv("PEST_TERROR_REPLICATION_EXECUTION_ACK") shouldBe
            REPLICATION_TERROR_EXECUTION_ACK

        val authorization =
            PestControlTierOneMonoBlueTerrorReplicationExecutionAuthorization.inspect()
        require(authorization.green && authorization.executionAuthorized) {
            "replication execution authorization is not green"
        }

        val inputDir = Path.of(
            System.getenv("PEST_TERROR_REPLICATION_INPUT_DIR")
                ?: error("PEST_TERROR_REPLICATION_INPUT_DIR required")
        )
        val input =
            PestControlTierOneMonoBlueTerrorReplicationExecutionInputLoader.loadValidated(inputDir)

        val executionCommit =
            System.getenv("PEST_TERROR_REPLICATION_EXECUTION_COMMIT")
                ?: error("PEST_TERROR_REPLICATION_EXECUTION_COMMIT required")
        require(
            executionCommit.matches(Regex("[0-9a-f]{40}")) &&
                executionCommit != "0".repeat(40)
        )

        val output = Path.of(
            System.getenv("PEST_TERROR_REPLICATION_OUTPUT_DIR")
                ?: error("PEST_TERROR_REPLICATION_OUTPUT_DIR required")
        )
        require(Files.isDirectory(output)) { "replication output root must already exist" }

        val evidenceRoot = output.resolve("durable-evidence")
        Files.createDirectory(evidenceRoot)
        forceReplicationOutputDirectory(evidenceRoot.parent)

        val journal = MonoBlueTerrorReplicationDurableAttempts.createNew(
            evidenceRoot = evidenceRoot,
            assignments = input.assignments,
            executionSourceCommit = executionCommit,
        )

        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        val coordinator =
            PestControlTierOneMonoBlueTerrorReplicationExecutionCoordinator(
                input = input,
                persistAttemptBeforeInitialization = { attempt ->
                    val assignment = input.assignments[attempt.gameNumber - 1]
                    require(attempt.seed == assignment.seed)
                    journal.recordAttempt(attempt.gameNumber)
                },
                persistInitializationEntry = { assignment ->
                    journal.recordInitializationEntry(assignment.gameNumber)
                },
                persistCompletedGame = { assignment, raw ->
                    journal.recordResult(assignment.gameNumber, raw)
                },
            )

        val outcome = coordinator.execute { assignment ->
            val events = journal.events()
            require(
                events.any {
                    it.gameNumber == assignment.gameNumber &&
                        it.type == MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED
                }
            ) { "durable replication attempt evidence missing" }
            require(
                events.any {
                    it.gameNumber == assignment.gameNumber &&
                        it.type == MonoBlueTerrorCoordinatorEventType.INITIALIZATION_ENTERED
                }
            ) { "durable replication initialization-entry evidence missing" }

            val initialized =
                PestControlTierOneMonoBlueTerrorReplicationAuthorizedInitializer.initialize(
                    registry = registry,
                    assignment = assignment,
                    vectorIdentity = input.vectorIdentity,
                    executionCommit = executionCommit,
                    durableAttemptRecorded = true,
                )

            PestControlTierOneMonoBlueTerrorProductionDriver.encode(
                PestControlTierOneMonoBlueTerrorProductionDriver.drive(
                    registry,
                    initialized,
                )
            )
        }

        if (outcome.disposition == MonoBlueTerrorCoordinatorDisposition.REJECTED) {
            val failedGame =
                outcome.attempts.lastOrNull()?.gameNumber
                    ?: input.assignments.first().gameNumber
            runCatching {
                journal.reject(failedGame, "PRIMARY_REPLICATION_EXECUTION_FAILURE")
            }
        }

        val summary = buildString {
            appendLine("protocol_id=$PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID")
            appendLine("block_id=$PEST_MONO_BLUE_TERROR_REPLICATION_BLOCK_ID")
            appendLine("execution_commit=$executionCommit")
            appendLine("freeze_artifact_id=$PEST_MONO_BLUE_TERROR_REPLICATION_FREEZE_ARTIFACT_ID")
            appendLine(
                "freeze_archive_sha256=$PEST_MONO_BLUE_TERROR_REPLICATION_FREEZE_ARCHIVE_SHA256"
            )
            appendLine("vector_sha256=$PEST_MONO_BLUE_TERROR_REPLICATION_VECTOR_SHA256")
            appendLine("assignment_csv_sha256=$PEST_MONO_BLUE_TERROR_REPLICATION_ASSIGNMENTS_SHA256")
            appendLine("freeze_manifest_sha256=$PEST_MONO_BLUE_TERROR_REPLICATION_MANIFEST_SHA256")
            appendLine("disposition=${outcome.disposition}")
            appendLine(
                "attempted_games=${outcome.attempts.map { it.gameNumber }.joinToString(",")}"
            )
            appendLine("initialized_games=${outcome.initializedGames.joinToString(",")}")
            appendLine("recorded_games=${outcome.recordedGames.joinToString(",")}")
            appendLine("failure=${outcome.failure ?: ""}")
            appendLine("rerolls=0")
            appendLine("replacements=0")
            appendLine("seed_regeneration=0")
        }.toByteArray(Charsets.UTF_8)

        val index =
            PestControlTierOneMonoBlueTerrorReplicationArtifactContract.buildIndex(
                vectorIdentity = input.vectorIdentity,
                frozenSeeds = input.seeds,
                outcome = outcome,
                summary = summary,
            )

        writeReplicationOutput(output.resolve("summary.txt"), summary)
        writeReplicationOutput(output.resolve("artifact-index.json"), index)

        val eventText = journal.events().joinToString("\n", postfix = "\n") {
            "${it.gameNumber}|${it.type.name}"
        }.toByteArray(Charsets.UTF_8)
        writeReplicationOutput(output.resolve("coordinator-events.txt"), eventText)

        outcome.disposition shouldBe MonoBlueTerrorCoordinatorDisposition.VALIDATED
        outcome.attempts.map { it.gameNumber } shouldBe (1..12).toList()
        outcome.initializedGames shouldBe (1..12).toList()
        outcome.recordedGames shouldBe (1..12).toList()
        journal.events().map { it.gameNumber } shouldBe
            (1..12).flatMap { game -> listOf(game, game, game) }
    }
})

private fun writeReplicationOutput(path: Path, bytes: ByteArray) {
    FileChannel.open(path, CREATE_NEW, WRITE).use { channel ->
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }
    forceReplicationOutputDirectory(path.parent)
}

private fun forceReplicationOutputDirectory(path: Path) {
    FileChannel.open(path, READ).use { it.force(true) }
}
