package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.support.GameTestDriver
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Excluded runtime identity receiver; no claim, allocation or game initialization. */
class IndustrialWasteV2LoadedJvmIdentityTest : FunSpec({
    val types = listOf(IndustrialWasteV2AllocationRunner::class.java,
        IndustrialWasteV2FullHorizonRunner::class.java, GameTestDriver::class.java)
    test("loaded runtime reports actual class bytes and stable sorted identity") {
        val first = IndustrialWasteV2LoadedJvmIdentity.capture(types)
        val again = IndustrialWasteV2LoadedJvmIdentity.capture(types.reversed())
        first shouldBe again
        first.size shouldBe 3
        first.all { it.classSha256.matches(Regex("[0-9a-f]{64}")) } shouldBe true
        first.all { it.codeSource.isNotBlank() } shouldBe true
    }
    test("exact preclaim byte digest binding accepts the loaded JVM only") {
        val entries = IndustrialWasteV2LoadedJvmIdentity.capture(types)
        IndustrialWasteV2LoadedJvmIdentity.verify(
            entries.associate { it.binaryName to it.classSha256 }, types) shouldBe entries
    }
    test("changed digest or missing runtime class refuses preclaim binding") {
        val entries = IndustrialWasteV2LoadedJvmIdentity.capture(types)
        val expected = entries.associate { it.binaryName to it.classSha256 }
        shouldThrow<IllegalStateException> {
            IndustrialWasteV2LoadedJvmIdentity.verify(
                expected + (entries.first().binaryName to "0".repeat(64)), types)
        }
        shouldThrow<IllegalArgumentException> {
            IndustrialWasteV2LoadedJvmIdentity.verify(expected - entries.first().binaryName, types)
        }
    }
})
