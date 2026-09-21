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

private const val CONTINUATION_EXECUTE_MODE = "EXECUTE_SALVAGE_11"

class PestControlTierOneGrixisSalvageContinuationOfficialExecutionRunnerTest : FunSpec({
    val enabled = System.getenv("PEST_GRIXIS_CONTINUATION_MODE") == CONTINUATION_EXECUTE_MODE

    test("execute untouched Grixis Games 2 through 12 exactly once").config(
        enabled = enabled,
        timeout = 24.hours,
    ) {
        val inputDir = Path.of(
            System.getenv("PEST_GRIXIS_CONTINUATION_INPUT_DIR")
                ?: error("PEST_GRIXIS_CONTINUATION_INPUT_DIR required"),
        )
        val input = PestControlTierOneGrixisReplicationExecutionInputLoader.loadForAuthorizedExecution(inputDir)
        val suffix = PestControlTierOneGrixisReplicationContinuationPlan.untouchedSuffix(input)
        suffix.map { it.gameNumber } shouldBe (2..12).toList()
        suffix.none { it.gameNumber == 1 } shouldBe true

        val executionCommit = System.getenv("PEST_GRIXIS_CONTINUATION_EXECUTION_COMMIT")
            ?: error("PEST_GRIXIS_CONTINUATION_EXECUTION_COMMIT required")
        require(executionCommit.matches(Regex("[0-9a-f]{40}")))

        val output = Path.of(
            System.getenv("PEST_GRIXIS_CONTINUATION_OUTPUT_DIR")
                ?: error("PEST_GRIXIS_CONTINUATION_OUTPUT_DIR required"),
        )
        Files.createDirectories(output)
        require(!Files.exists(output.resolve("attempt-1.marker")))
        require(!Files.exists(output.resolve("game-1.json")))

        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        val coordinator = PestControlTierOneGrixisReplicationContinuationCoordinator(
            input,
            persistAttemptBeforeInitialization = { attempt ->
                require(attempt.gameNumber in 2..12)
                Files.write(
                    output.resolve("attempt-${attempt.gameNumber}.marker"),
                    (
                        "game_number=${attempt.gameNumber}\n" +
                            "classification=${PEST_GRIXIS_CONTINUATION_CLASSIFICATION}\n" +
                            "vector_sha256=${input.vectorIdentity.orderedVectorSha256}\n"
                        ).toByteArray(),
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                )
            },
            persistCompletedGame = { assignment, raw ->
                require(assignment.gameNumber in 2..12)
                Files.write(
                    output.resolve("game-${assignment.gameNumber}.json"),
                    raw,
                    StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE,
                )
            },
        )

        val outcome = coordinator.execute { assignment ->
            require(assignment.gameNumber in 2..12)
            require(Files.isRegularFile(output.resolve("attempt-${assignment.gameNumber}.marker")))
            val initialized = PestControlTierOneGrixisReplicationAuthorizedInitializer.initialize(
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

        require(!Files.exists(output.resolve("attempt-1.marker")))
        require(!Files.exists(output.resolve("game-1.json")))

        val summary = buildString {
            appendLine("classification=${PEST_GRIXIS_CONTINUATION_CLASSIFICATION}")
            appendLine("protocol_id=${PEST_GRIXIS_PREBOARD_PROTOCOL_ID}")
            appendLine("block_id=${PEST_GRIXIS_REPLICATION_BLOCK_ID}")
            appendLine("excluded_consumed_game=1")
            appendLine("execution_commit=$executionCommit")
            appendLine("disposition=${outcome.disposition}")
            appendLine("attempted_games=${outcome.attempts.map { it.gameNumber }.joinToString(",")}")
            appendLine("recorded_games=${outcome.recordedGames.joinToString(",")}")
            appendLine("failure=${outcome.failure ?: ""}")
            appendLine("rerolls=0")
            appendLine("replacements=0")
            appendLine("seed_regeneration=0")
        }.toByteArray()
        Files.write(output.resolve("summary.txt"), summary, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)

        val rawHashes = outcome.perGameRaw.zip(outcome.recordedGames).joinToString("\n", postfix = "\n") { (raw, game) ->
            "$game,${sha256(raw)}"
        }.toByteArray()
        Files.write(
            output.resolve("raw-game-sha256.csv"),
            ("game_number,sha256\n".toByteArray() + rawHashes),
            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE,
        )

        outcome.disposition shouldBe GrixisCoordinatorDisposition.VALIDATED
        outcome.attempts.map { it.gameNumber } shouldBe (2..12).toList()
        outcome.recordedGames shouldBe (2..12).toList()
        outcome.perGameRaw.size shouldBe 11
    }
})
