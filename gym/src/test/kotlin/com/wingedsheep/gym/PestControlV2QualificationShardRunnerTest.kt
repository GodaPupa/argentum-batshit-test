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
import java.nio.file.StandardOpenOption
import kotlin.time.Duration.Companion.hours

private const val QUALIFICATION_EXECUTION_ACK = "EXECUTE_FROZEN_PEST_V2_QUALIFICATION_50_EXACTLY_ONCE"

class PestControlV2QualificationShardRunnerTest : FunSpec({
    val mode = System.getenv("PEST_V2_QUALIFICATION_RUNNER_MODE")
    test("validate or run one exact qualification shard through the fail-closed coordinator").config(
        enabled = mode != null,
        timeout = 5.hours,
    ) {
        require(mode == "VALIDATE_ONLY" || mode == "EXECUTE")
        val input = Path.of(System.getenv("PEST_V2_QUALIFICATION_INPUT_DIR") ?: error("input directory required"))
        val output = Path.of(System.getenv("PEST_V2_QUALIFICATION_OUTPUT_DIR") ?: error("output directory required"))
        val executionCommit = System.getenv("PEST_V2_QUALIFICATION_HARNESS_COMMIT")
            ?: error("harness commit required")
        val executionTree = System.getenv("PEST_V2_QUALIFICATION_HARNESS_TREE")
            ?: error("harness tree required")
        val vector = Files.readAllBytes(input.resolve("ordered-seeds.txt"))
        val csv = Files.readAllBytes(input.resolve("assignments.csv"))
        val manifest = Files.readAllBytes(input.resolve("freeze-manifest.json"))
        val shardAllocation = Files.readAllBytes(input.resolve("shard-allocation.json"))
        val plan = PestControlV2QualificationCoordinator.buildSealedPlan(
            vector, csv, manifest, shardAllocation, executionCommit, executionTree,
        )

        if (mode == "VALIDATE_ONLY") {
            val validationErrors = plan.shards.flatMap { shard ->
                PestControlV2QualificationShardRunnerGuard.activationErrors(
                    plan = plan,
                    shardId = shard.shardId,
                    explicitAuthorization = true,
                    isUnitTestProcess = false,
                    attemptNumber = 1,
                    outputAlreadyExists = false,
                    checkedOutCommit = executionCommit,
                    checkedOutTree = executionTree,
                )
            }
            validationErrors shouldBe emptyList()
            plan.shards.forEach { shard ->
                PestControlV2QualificationShardRunnerGuard.activationErrors(
                    plan = plan,
                    shardId = shard.shardId,
                    explicitAuthorization = false,
                    isUnitTestProcess = true,
                    attemptNumber = 2,
                    outputAlreadyExists = true,
                    checkedOutCommit = "0".repeat(40),
                    checkedOutTree = "0".repeat(40),
                ).let { errors ->
                    check(errors.contains("explicit execution acknowledgement is missing"))
                    check(errors.contains("unit tests cannot activate the runner"))
                    check(errors.contains("shard retry is forbidden"))
                    check(errors.contains("shard output already exists"))
                    check(errors.contains("checked-out commit mismatch"))
                    check(errors.contains("checked-out tree mismatch"))
                }
            }
            plan.assignments.forEach { assignment ->
                val provenance = PestControlV2QualificationGameAdapter.provenance(assignment, executionCommit)
                check(provenance.gameNumber == assignment.gameNumber && provenance.seedDecimal == assignment.seedDecimal)
            }
            writeNewForced(
                output.resolve("runner-validation-summary.txt"),
                ("status=VALIDATED_UNEXECUTED\nseed_count=50\nshards=25,25\n" +
                    "harness_commit=$executionCommit\nharness_tree=$executionTree\n" +
                    "runner_state=${PestControlV2QualificationShardRunnerGuard.CONFIGURED_STATE}\n" +
                    "outcome_exposure=0/50\n").toByteArray(),
            )
        } else {
            System.getenv("PEST_V2_QUALIFICATION_EXECUTION_ACK") shouldBe QUALIFICATION_EXECUTION_ACK
            val shardId = System.getenv("PEST_V2_QUALIFICATION_SHARD_ID") ?: error("shard ID required")
            val shard = plan.shards.singleOrNull { it.shardId == shardId } ?: error("unknown shard ID")
            val guardErrors = PestControlV2QualificationShardRunnerGuard.activationErrors(
                plan = plan,
                shardId = shardId,
                explicitAuthorization = true,
                isUnitTestProcess = false,
                attemptNumber = System.getenv("GITHUB_RUN_ATTEMPT")?.toIntOrNull() ?: 0,
                outputAlreadyExists = Files.exists(output),
                checkedOutCommit = executionCommit,
                checkedOutTree = executionTree,
            )
            check(guardErrors.isEmpty()) { "qualification runner rejected activation: ${guardErrors.joinToString()}" }

            Files.createDirectories(output)
            val assignments = PestControlMatchupSharding.assignmentsFor(plan, shard)
            val attempts = mutableListOf<AttemptedSeed>()
            val games = mutableListOf<MatchupRawGame>()
            val runtimes = mutableListOf<GameRuntime>()
            val log = AppendOnlyBlockExecutionLog().apply {
                append(BlockRunnerState.AUTHORIZED, "qualification shard authorization")
            }
            val registry = CardRegistry().apply {
                register(PredefinedTokens.allTokens)
                MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
            }
            val shardStarted = System.nanoTime()
            var failure: Throwable? = null
            try {
                assignments.forEach { assignment ->
                    val attempted = AttemptedSeed(assignment.gameNumber, assignment.seedDecimal, assignment.seedHex)
                    appendAttemptForced(output.resolve("attempted-seeds.csv"), attempted, attempts.isEmpty())
                    attempts += attempted
                    log.append(
                        BlockRunnerState.STARTED,
                        "seed marked attempted before game initialization",
                        assignment.gameNumber,
                        assignment.seedDecimal,
                    )
                    val gameStarted = System.nanoTime()
                    val session = PestControlV2QualificationGameAdapter.initialize(registry, assignment, executionCommit)
                    val game = PestControlPreboardProductionDriver.drive(registry, session)
                    check(game.terminal?.gameOver == true && game.protocolDefect == null)
                    check(game.priorityActions.none { !it.accepted || it.fallbackUsed })
                    val bundle = PestControlMatchupArtifactCodec.build(game)
                    check(PestControlMatchupArtifactCodec.verify(bundle).isEmpty())
                    val gameDir = output.resolve("games/game-${assignment.gameNumber.toString().padStart(2, '0')}")
                    writeNewForced(gameDir.resolve("raw.json"), bundle.rawJson)
                    writeNewForced(gameDir.resolve("raw.json.gz"), bundle.compressed)
                    writeNewForced(gameDir.resolve("report.md"), bundle.report)
                    writeNewForced(gameDir.resolve("manifest.json"), bundle.manifest)
                    games += game
                    runtimes += GameRuntime(
                        assignment.gameNumber,
                        assignment.seedHex,
                        ((System.nanoTime() - gameStarted) / 1_000_000L).coerceAtLeast(1L),
                    )
                }
                log.append(BlockRunnerState.COMPLETED, "qualification shard completion")
            } catch (error: Throwable) {
                failure = error
                log.append(BlockRunnerState.REJECTED, "qualification shard failure: ${error.message}")
            }
            val completion = if (failure == null) ShardCompletion.COMPLETED else ShardCompletion.FAILED
            val runtimeMillis = maxOf(
                ((System.nanoTime() - shardStarted) / 1_000_000L).coerceAtLeast(1L),
                runtimes.sumOf { it.runtimeMillis },
            )
            val raw = MatchupShardRaw(
                identity = plan.identity,
                shard = shard,
                completion = completion,
                failureReason = failure?.message,
                assignments = assignments,
                attemptedSeeds = attempts,
                games = games,
                gameRuntimes = runtimes,
                shardRuntimeMillis = runtimeMillis,
                executionLog = log.entries,
            )
            val shardBundle = PestControlMatchupSharding.buildShard(raw)
            writeNewForced(output.resolve("shard-raw.json"), shardBundle.rawJson)
            writeNewForced(output.resolve("shard-raw.json.gz"), shardBundle.compressed)
            writeNewForced(output.resolve("shard-manifest.json"), shardBundle.manifest)
            writeNewForced(output.resolve("shard-audit-input.json"), shardBundle.auditInput)
            writeNewForced(
                output.resolve("execution-status.txt"),
                "completion=$completion\nattempted=${attempts.size}\nrecorded=${games.size}\nfailure=${failure?.message ?: "none"}\n".toByteArray(),
            )
            if (failure == null) {
                check(PestControlMatchupSharding.verifyShard(plan, shard, shardBundle).second.isEmpty())
            }
            failure?.let { throw it }
        }
    }
})

private fun appendAttemptForced(path: Path, attempt: AttemptedSeed, includeHeader: Boolean) {
    Files.createDirectories(path.parent)
    FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND).use { channel ->
        val bytes = buildString {
            if (includeHeader) appendLine("game_number,seed_decimal,seed_hex")
            appendLine("${attempt.gameNumber},${attempt.seedDecimal},${attempt.seedHex}")
        }.toByteArray()
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }
}

private fun writeNewForced(path: Path, bytes: ByteArray) {
    Files.createDirectories(path.parent)
    FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }
}
