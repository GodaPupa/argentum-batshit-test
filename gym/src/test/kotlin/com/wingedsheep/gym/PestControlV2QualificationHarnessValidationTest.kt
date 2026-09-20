package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

class PestControlV2QualificationHarnessValidationTest : FunSpec({
    test("validate exact frozen qualification inputs without initializing a game").config(
        enabled = System.getenv("PEST_V2_QUALIFICATION_VALIDATE_ONLY") == "true",
    ) {
        val input = Path.of(System.getenv("PEST_V2_QUALIFICATION_INPUT_DIR") ?: error("input directory required"))
        val output = Path.of(System.getenv("PEST_V2_QUALIFICATION_OUTPUT_DIR") ?: error("output directory required"))
        val vector = Files.readAllBytes(input.resolve("ordered-seeds.txt"))
        val csv = Files.readAllBytes(input.resolve("assignments.csv"))
        val manifest = Files.readAllBytes(input.resolve("freeze-manifest.json"))
        val shardAllocation = Files.readAllBytes(input.resolve("shard-allocation.json"))
        val result = PestControlV2QualificationExecutionPreflight.inspect(vector, csv, manifest, shardAllocation)
        result.errors shouldBe emptyList()
        result.seeds.size shouldBe 50
        result.assignments.size shouldBe 50
        val executionCommit = System.getenv("PEST_V2_QUALIFICATION_HARNESS_COMMIT")
            ?: error("harness commit required")
        val executionTree = System.getenv("PEST_V2_QUALIFICATION_HARNESS_TREE")
            ?: error("harness tree required")
        val plan = PestControlV2QualificationCoordinator.buildSealedPlan(
            vector, csv, manifest, shardAllocation, executionCommit, executionTree,
        )
        PestControlMatchupSharding.validatePlan(plan) shouldBe emptyList()
        plan.shards.map { it.firstGameNumber..it.lastGameNumber } shouldBe listOf(1..25, 26..50)
        PestControlV2QualificationShardRunnerGuard.activationErrors(
            plan = plan,
            shardId = plan.shards.first().shardId,
            explicitAuthorization = true,
            isUnitTestProcess = false,
            attemptNumber = 1,
            outputAlreadyExists = false,
            checkedOutCommit = executionCommit,
            checkedOutTree = executionTree,
        ) shouldBe emptyList()
        writeForced(
            output.resolve("validation-summary.txt"),
            ("status=VALIDATED_UNEXECUTED\nseed_count=50\nshards=25,25\n" +
                "harness_commit=$executionCommit\nharness_tree=$executionTree\n" +
                "runner_state=AUTHORIZED_UNEXECUTED\noutcome_exposure=0/50\n").toByteArray(),
        )
    }
})

private fun writeForced(path: Path, bytes: ByteArray) {
    Files.createDirectories(path.parent)
    FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { channel ->
        val buffer = ByteBuffer.wrap(bytes)
        while (buffer.hasRemaining()) channel.write(buffer)
        channel.force(true)
    }
}
