package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.time.Duration.Companion.hours

private const val REPLICATION_EXECUTE_MODE = "EXECUTE"

class PestControlTierOneGrixisReplicationOfficialExecutionRunnerTest : FunSpec({
    val enabled = System.getenv("PEST_GRIXIS_REPLICATION_MODE") == REPLICATION_EXECUTE_MODE

    test("execute frozen Grixis twelve-game replication vector exactly once").config(
        enabled = enabled,
        timeout = 24.hours,
    ) {
        val inputDir = Path.of(
            System.getenv("PEST_GRIXIS_REPLICATION_INPUT_DIR")
                ?: error("PEST_GRIXIS_REPLICATION_INPUT_DIR required"),
        )
        val input = PestControlTierOneGrixisReplicationExecutionInputLoader
            .loadForAuthorizedExecution(inputDir)
        val executionCommit = System.getenv("PEST_GRIXIS_REPLICATION_EXECUTION_COMMIT")
            ?: error("PEST_GRIXIS_REPLICATION_EXECUTION_COMMIT required")
        require(executionCommit.matches(Regex("[0-9a-f]{40}")))

        val output = Path.of(
            System.getenv("PEST_GRIXIS_REPLICATION_OUTPUT_DIR")
                ?: error("PEST_GRIXIS_REPLICATION_OUTPUT_DIR required"),
        )
        Files.createDirectories(output)

        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        val coordinator = PestControlTierOneGrixisReplicationExecutionCoordinator(
            input,
            persistAttemptBeforeInitialization = { attempt ->
                Files.write(
                    output.resolve("attempt-${attempt.gameNumber}.marker"),
                    (
                        "game_number=${attempt.gameNumber}\n" +
                            "vector_sha256=${input.vectorIdentity.orderedVectorSha256}\n"
                        ).toByteArray(),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                )
            },
            persistCompletedGame = { assignment, raw ->
                Files.write(
                    output.resolve("game-${assignment.gameNumber}.json"),
                    raw,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                )
            },
        )

        val outcome = coordinator.execute { assignment ->
            require(Files.isRegularFile(output.resolve("attempt-${assignment.gameNumber}.marker")))
            val initialized = PestControlTierOneGrixisAuthorizedInitializer.initialize(
                registry = registry,
                assignment = assignment,
                vectorIdentity = input.vectorIdentity,
                executionCommit = executionCommit,
                durableAttemptRecorded = true,
            )
            PestControlTierOneGrixisProductionDriver.encode(
                PestControlTierOneGrixisProductionDriver.drive(registry, initialized)
            )
        }

        val summary = buildString {
            appendLine("protocol_id=${PEST_GRIXIS_PREBOARD_PROTOCOL_ID}")
            appendLine("block_id=${PEST_GRIXIS_REPLICATION_BLOCK_ID}")
            appendLine("execution_commit=$executionCommit")
            appendLine("disposition=${outcome.disposition}")
            appendLine("attempted_games=${outcome.attempts.map { it.gameNumber }.joinToString(",")}")
            appendLine("recorded_games=${outcome.recordedGames.joinToString(",")}")
            appendLine("failure=${outcome.failure ?: ""}")
            appendLine("rerolls=0")
            appendLine("replacements=0")
            appendLine("seed_regeneration=0")
        }.toByteArray()

        Files.write(
            output.resolve("summary.txt"), summary,
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
        )

        val rawHashes = outcome.perGameRaw.mapIndexed { index, raw ->
            "${index + 1},${sha256(raw)}"
        }.joinToString("\n", postfix = "\n").toByteArray()
        Files.write(
            output.resolve("raw-game-sha256.csv"),
            ("game_number,sha256\n".toByteArray() + rawHashes),
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
        )

        outcome.disposition shouldBe GrixisCoordinatorDisposition.VALIDATED
        outcome.attempts.map { it.gameNumber } shouldBe (1..12).toList()
        outcome.recordedGames shouldBe (1..12).toList()
        outcome.perGameRaw.size shouldBe 12
    }
})
