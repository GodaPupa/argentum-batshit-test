package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.MatchupShardArtifactBundle
import com.wingedsheep.gym.matchup.PestControlMatchupSharding
import com.wingedsheep.gym.matchup.PestControlV2QualificationCoordinator
import com.wingedsheep.gym.matchup.ShardCompletion
import com.wingedsheep.gym.matchup.ShardObservation
import com.wingedsheep.gym.matchup.ShardedBlockDisposition
import io.kotest.core.spec.style.FunSpec
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.time.Duration.Companion.minutes

class PestControlV2QualificationReconcilerTest : FunSpec({
    val enabled = System.getenv("PEST_V2_QUALIFICATION_RECONCILE") == "true"
    test("reconcile both exact qualification shards into one fail-closed block").config(
        enabled = enabled,
        timeout = 30.minutes,
    ) {
        val input = Path.of(System.getenv("PEST_V2_QUALIFICATION_INPUT_DIR") ?: error("input directory required"))
        val shards = Path.of(System.getenv("PEST_V2_QUALIFICATION_SHARDS_DIR") ?: error("shards directory required"))
        val output = Path.of(System.getenv("PEST_V2_QUALIFICATION_OUTPUT_DIR") ?: error("output directory required"))
        val executionCommit = System.getenv("PEST_V2_QUALIFICATION_HARNESS_COMMIT")
            ?: error("harness commit required")
        val executionTree = System.getenv("PEST_V2_QUALIFICATION_HARNESS_TREE")
            ?: error("harness tree required")
        val plan = PestControlV2QualificationCoordinator.buildSealedPlan(
            vector = Files.readAllBytes(input.resolve("ordered-seeds.txt")),
            csv = Files.readAllBytes(input.resolve("assignments.csv")),
            manifest = Files.readAllBytes(input.resolve("freeze-manifest.json")),
            shardAllocation = Files.readAllBytes(input.resolve("shard-allocation.json")),
            executionSourceCommit = executionCommit,
            executionSourceTree = executionTree,
        )
        val observations = plan.shards.map { shard ->
            val directory = shards.resolve(shard.shardId)
            val artifactFiles = listOf(
                directory.resolve("shard-raw.json"),
                directory.resolve("shard-raw.json.gz"),
                directory.resolve("shard-manifest.json"),
                directory.resolve("shard-audit-input.json"),
            )
            val artifact = if (artifactFiles.all { Files.isRegularFile(it) }) {
                MatchupShardArtifactBundle(
                    rawJson = Files.readAllBytes(artifactFiles[0]),
                    compressed = Files.readAllBytes(artifactFiles[1]),
                    manifest = Files.readAllBytes(artifactFiles[2]),
                    auditInput = Files.readAllBytes(artifactFiles[3]),
                )
            } else {
                null
            }
            val status = directory.resolve("execution-status.txt")
            val completion = if (Files.isRegularFile(status)) {
                Files.readAllLines(status).firstOrNull { it.startsWith("completion=") }
                    ?.substringAfter('=')?.let { ShardCompletion.valueOf(it) } ?: ShardCompletion.INCOMPLETE
            } else {
                ShardCompletion.INCOMPLETE
            }
            ShardObservation(shard.shardId, completion, artifact)
        }
        val aggregate = PestControlMatchupSharding.reconcile(plan, observations)
        reconcileWriteNew(output.resolve("aggregate-raw.json"), aggregate.rawJson)
        reconcileWriteNew(output.resolve("aggregate-raw.json.gz"), aggregate.compressed)
        reconcileWriteNew(output.resolve("aggregate-manifest.json"), aggregate.manifest)
        reconcileWriteNew(
            output.resolve("aggregate-status.txt"),
            ("disposition=${aggregate.reconciliation.disposition}\n" +
                "retire_complete_vector=${aggregate.reconciliation.retireCompleteVector}\n" +
                "attempted=${aggregate.reconciliation.attemptedSeeds.size}\n" +
                "recorded=${aggregate.reconciliation.games.size}\n" +
                "errors=${aggregate.reconciliation.errors.size}\n").toByteArray(),
        )
        check(PestControlMatchupSharding.verifyAggregate(aggregate).isEmpty())
        check(aggregate.reconciliation.disposition == ShardedBlockDisposition.PENDING_REVIEW) {
            "qualification vector rejected: ${aggregate.reconciliation.errors.joinToString()}"
        }
    }
})

private fun reconcileWriteNew(path: Path, bytes: ByteArray) {
    Files.createDirectories(path.parent)
    FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }
}
