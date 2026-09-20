package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.PestControlV2QualificationExecutionPreflight
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
        val result = PestControlV2QualificationExecutionPreflight.inspect(
            vector = Files.readAllBytes(input.resolve("ordered-seeds.txt")),
            csv = Files.readAllBytes(input.resolve("assignments.csv")),
            manifest = Files.readAllBytes(input.resolve("freeze-manifest.json")),
            shardAllocation = Files.readAllBytes(input.resolve("shard-allocation.json")),
        )
        result.errors shouldBe emptyList()
        result.seeds.size shouldBe 50
        result.assignments.size shouldBe 50
        writeForced(
            output.resolve("validation-summary.txt"),
            "status=VALIDATED_UNEXECUTED\nseed_count=50\nshards=25,25\nrunner_state=DISABLED\noutcome_exposure=0/50\n".toByteArray(),
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
