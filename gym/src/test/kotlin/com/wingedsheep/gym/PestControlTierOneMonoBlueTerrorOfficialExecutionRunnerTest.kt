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

private const val OFFICIAL_TERROR_EXECUTE_MODE = "EXECUTE"

class PestControlTierOneMonoBlueTerrorOfficialExecutionRunnerTest : FunSpec({
    val enabled =
        System.getenv("PEST_TERROR_OFFICIAL_MODE") == OFFICIAL_TERROR_EXECUTE_MODE

    test("execute frozen Mono-Blue Terror four-game vector exactly once").config(
        enabled = enabled,
        timeout = 12.hours,
    ) {
        System.getenv("PEST_TERROR_OFFICIAL_EXECUTION_ACK") shouldBe
            PEST_MONO_BLUE_TERROR_EXECUTION_INPUT_EXECUTE_ACK

        val input =
            PestControlTierOneMonoBlueTerrorOfficialExecutionInputLoader
                .loadForAuthorizedExecutionFromEnvironment()

        val executionCommit =
            System.getenv("PEST_TERROR_OFFICIAL_EXECUTION_COMMIT")
                ?: error("PEST_TERROR_OFFICIAL_EXECUTION_COMMIT required")
        require(executionCommit.matches(Regex("[0-9a-f]{40}")))

        val output = Path.of(
            System.getenv("PEST_TERROR_OFFICIAL_OUTPUT_DIR")
                ?: error("PEST_TERROR_OFFICIAL_OUTPUT_DIR required")
        )
        require(Files.isDirectory(output)) { "official output root must already exist" }

        val evidenceRoot = output.resolve("durable-evidence")
        Files.createDirectory(evidenceRoot)
        forceDirectory(evidenceRoot.parent)

        val journal = MonoBlueTerrorDurableAttempts.createNew(
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
            PestControlTierOneMonoBlueTerrorAuthorizedExecutionCoordinator(
                assignments = input.assignments,
                vectorIdentity = input.vectorIdentity,
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
            ) { "durable attempt evidence missing" }
            require(
                events.any {
                    it.gameNumber == assignment.gameNumber &&
                        it.type == MonoBlueTerrorCoordinatorEventType.INITIALIZATION_ENTERED
                }
            ) { "durable initialization-entry evidence missing" }

            val initialized =
                PestControlTierOneMonoBlueTerrorAuthorizedInitializer.initialize(
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
                journal.reject(failedGame, "OFFICIAL_EXECUTION_FAILURE")
            }
        }

        val summary = buildString {
            appendLine("protocol_id=${PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID}")
            appendLine("block_id=${PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID}")
            appendLine("execution_commit=$executionCommit")
            appendLine("freeze_artifact_id=${PEST_MONO_BLUE_TERROR_FREEZE_ARTIFACT_ID}")
            appendLine("freeze_archive_sha256=${input.archiveSha256}")
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

        val index = outcome.artifactIndex(
            vectorIdentity = input.vectorIdentity,
            frozenSeeds = input.seeds,
            summary = summary,
        )

        writeDurable(output.resolve("summary.txt"), summary)
        writeDurable(output.resolve("artifact-index.json"), index)

        val eventText = journal.events().joinToString("\n", postfix = "\n") {
            "${it.gameNumber}|${it.type.name}"
        }.toByteArray(Charsets.UTF_8)
        writeDurable(output.resolve("coordinator-events.txt"), eventText)

        outcome.disposition shouldBe MonoBlueTerrorCoordinatorDisposition.VALIDATED
        outcome.attempts.map { it.gameNumber } shouldBe listOf(1, 2, 3, 4)
        outcome.initializedGames shouldBe listOf(1, 2, 3, 4)
        outcome.recordedGames shouldBe listOf(1, 2, 3, 4)
        journal.events().map { it.gameNumber } shouldBe
            listOf(1, 1, 1, 2, 2, 2, 3, 3, 3, 4, 4, 4)
    }
})

private fun writeDurable(path: Path, bytes: ByteArray) {
    FileChannel.open(path, CREATE_NEW, WRITE).use { channel ->
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }
    forceDirectory(path.parent)
}

private fun forceDirectory(path: Path) {
    FileChannel.open(path, READ).use { it.force(true) }
}
