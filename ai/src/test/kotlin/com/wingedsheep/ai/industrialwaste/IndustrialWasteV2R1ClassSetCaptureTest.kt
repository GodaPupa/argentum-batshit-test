package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.support.GameTestDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

/** Prospective expanded class-set capture; still no prepared-worker or claim authority. */
class IndustrialWasteV2R1ClassSetCaptureTest : FunSpec({
    test("capture loaded bytes of the declared R1 primary Kotlin class set") {
        val classes = listOf(
            IndustrialWasteV2AllocationRunner::class.java,
            IndustrialWasteV2FullHorizonRunner::class.java,
            IndustrialWasteV2R1OfficialExecutionTest::class.java,
            IndustrialWasteV2CheckpointMana::class.java,
            IndustrialWasteV2PaymentIntent::class.java,
            IndustrialWasteV2PublicActionPolicy::class.java,
            IndustrialWasteV2QuietCheckpointReplayTest::class.java,
            GameTestDriver::class.java,
        )
        val entries = IndustrialWasteV2LoadedJvmIdentity.capture(classes)
        entries.map { it.binaryName } shouldBe classes.map { it.name }.sorted()
        val output = Path.of("build/reports/industrial-r1-class-set/loaded-classes.tsv")
        Files.createDirectories(output.parent)
        Files.writeString(output,
            (listOf("binary_name\tclass_sha256\tcode_source") +
                entries.map { "${it.binaryName}\t${it.classSha256}\t${it.codeSource}" })
                .joinToString("\n", postfix = "\n"))
        entries.size shouldBe 8
    }
})
