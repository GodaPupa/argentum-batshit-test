package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.support.GameTestDriver
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

/** Prospective loaded-byte manifest capture only; independent review and worker binding remain. */
class IndustrialWasteV2LoadedJvmManifestCaptureTest : FunSpec({
    test("capture actual loaded R1 runner class bytes for expected-manifest review") {
        val classes = listOf(
            IndustrialWasteV2AllocationRunner::class.java,
            IndustrialWasteV2FullHorizonRunner::class.java,
            IndustrialWasteV2R1OfficialExecutionTest::class.java,
            GameTestDriver::class.java,
        )
        val entries = IndustrialWasteV2LoadedJvmIdentity.capture(classes)
        entries.map { it.binaryName } shouldBe classes.map { it.name }.sorted()
        val output = Path.of("build/reports/industrial-loaded-jvm-manifest/loaded-classes.tsv")
        Files.createDirectories(output.parent)
        Files.writeString(output,
            (listOf("binary_name\tclass_sha256\tcode_source") +
                entries.map { "${it.binaryName}\t${it.classSha256}\t${it.codeSource}" })
                .joinToString("\n", postfix = "\n"))
        entries.size shouldBe 4
    }
})
