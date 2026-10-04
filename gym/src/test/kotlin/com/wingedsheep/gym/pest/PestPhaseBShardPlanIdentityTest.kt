package com.wingedsheep.gym.pest

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PestPhaseBShardPlanIdentityTest : FunSpec({
    test("all 32 deterministic shard identities cover the frozen global plan") {
        val identities = PestPhaseBProductionShardRunner.allShardPlanIdentities()

        identities.size shouldBe 32
        identities.map { it.shardIndex } shouldBe (0 until 32).toList()
        identities.sumOf { it.expectedRows } shouldBe PestPhaseBShardGeometry.GLOBAL_ROWS
        identities.take(8).map { it.expectedRows }.distinct() shouldBe listOf(1_091_027L)
        identities.drop(8).map { it.expectedRows }.distinct() shouldBe listOf(1_091_026L)
        identities.all { it.shardCount == 32 } shouldBe true
        identities.all { it.planSha256.matches(Regex("[0-9a-f]{64}")) } shouldBe true
        identities.map { it.planSha256 }.distinct().size shouldBe 32

        identities.forEach { identity ->
            println(
                "PEST_PHASE_B_SHARD index=${identity.shardIndex} rows=${identity.expectedRows} " +
                    "sha256=${identity.planSha256}"
            )
        }
    }
})
