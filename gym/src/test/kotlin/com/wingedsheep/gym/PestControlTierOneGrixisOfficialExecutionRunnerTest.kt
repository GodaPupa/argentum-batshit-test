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

private const val OFFICIAL_GRIXIS_EXECUTE_MODE = "EXECUTE"

class PestControlTierOneGrixisOfficialExecutionRunnerTest : FunSpec({
    val enabled = System.getenv("PEST_GRIXIS_OFFICIAL_MODE") == OFFICIAL_GRIXIS_EXECUTE_MODE

    test("execute frozen Grixis four-game vector exactly once").config(
        enabled = enabled,
        timeout = 10.hours,
    ) {
        System.getenv("PEST_GRIXIS_OFFICIAL_EXECUTION_ACK") shouldBe
            PEST_GRIXIS_EXECUTION_INPUT_EXECUTE_ACK

        val input = PestControlTierOneGrixisOfficialExecutionInputLoader
            .loadForAuthorizedExecutionFromEnvironment()
        val executionCommit = System.getenv("PEST_GRIXIS_OFFICIAL_EXECUTION_COMMIT")
            ?: error("PEST_GRIXIS_OFFICIAL_EXECUTION_COMMIT required")
        require(executionCommit.matches(Regex("[0-9a-f]{40}")))

        val output = Path.of(
            System.getenv("PEST_GRIXIS_OFFICIAL_OUTPUT_DIR")
                ?: error("PEST_GRIXIS_OFFICIAL_OUTPUT_DIR required"),
        )
        Files.createDirectories(output)

        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        val coordinator = PestControlTierOneGrixisAuthorizedExecutionCoordinator(
            assignments = input.assignments,
            vectorIdentity = input.vectorIdentity,
            persistAttemptBeforeInitialization = { attempt ->
                val bytes = (
                    "game_number=${attempt.gameNumber}\n" +
                        "vector_sha256=${input.vectorIdentity.orderedVectorSha256}\n"
                    ).toByteArray()
                Files.write(
                    output.resolve("attempt-${attempt.gameNumber}.marker"),
                    bytes,
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
            val marker = output.resolve("attempt-${assignment.gameNumber}.marker")
            require(Files.isRegularFile(marker)) { "durable attempt marker missing" }
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
            appendLine("block_id=${PEST_GRIXIS_SMOKE_BLOCK_ID}")
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
            output.resolve("summary.txt"),
            summary,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        )
        Files.write(
            output.resolve("artifact-index.json"),
            outcome.artifactIndex(input.vectorIdentity, input.seeds, summary),
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        )

        outcome.disposition shouldBe GrixisCoordinatorDisposition.VALIDATED
        outcome.attempts.map { it.gameNumber } shouldBe listOf(1, 2, 3, 4)
        outcome.recordedGames shouldBe listOf(1, 2, 3, 4)
    }
})
