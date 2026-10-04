package com.wingedsheep.gym.pest

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.assertions.throwables.shouldThrow

class PestPhaseBShardGeometryTest : FunSpec({
    test("32-shard row counts are exact and sum to the frozen bank") {
        val counts = (0 until PestPhaseBShardGeometry.SHARD_COUNT)
            .map(PestPhaseBShardGeometry::expectedRows)
        counts.take(8).distinct() shouldBe listOf(1_091_027L)
        counts.drop(8).distinct() shouldBe listOf(1_091_026L)
        counts.sum() shouldBe 34_912_840L
    }

    test("global ordinals map to exactly one shard by modulo 32") {
        listOf(
            0L to 0,
            1L to 1,
            31L to 31,
            32L to 0,
            33L to 1,
            34_912_839L to 7,
        ).forEach { (ordinal, expected) ->
            PestPhaseBShardGeometry.shardFor(ordinal) shouldBe expected
            (0 until PestPhaseBShardGeometry.SHARD_COUNT)
                .count { PestPhaseBShardGeometry.accepts(ordinal, it) } shouldBe 1
        }
    }

    test("out-of-range ordinals and shard indices fail closed") {
        shouldThrow<IllegalArgumentException> { PestPhaseBShardGeometry.shardFor(-1) }
        shouldThrow<IllegalArgumentException> {
            PestPhaseBShardGeometry.shardFor(PestPhaseBShardGeometry.GLOBAL_ROWS)
        }
        shouldThrow<IllegalArgumentException> { PestPhaseBShardGeometry.expectedRows(-1) }
        shouldThrow<IllegalArgumentException> {
            PestPhaseBShardGeometry.expectedRows(PestPhaseBShardGeometry.SHARD_COUNT)
        }
    }
})
