package com.wingedsheep.gym.pest

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path
import kotlin.time.Duration.Companion.hours

/**
 * Prospective single-shard entrypoint. A future reviewed aggregate gate must supply every input,
 * shard identity and evidence path. This test grants no execution authority by itself.
 */
class PestPhaseBProductionShardExecutionTest : FunSpec({
    test("execute exactly one frozen deterministic Phase-B shard").config(timeout = 3.hours, invocationTimeout = 3.hours) {
        fun required(name: String): String =
            requireNotNull(System.getenv(name)?.takeIf { it.isNotBlank() }) { "Missing $name" }

        val base = Path.of(required("PEST_PHASE_B_ACCEPTED_BASE")).toAbsolutePath().normalize()
        val supplement = Path.of(required("PEST_PHASE_B_ACCEPTED_SUPPLEMENT")).toAbsolutePath().normalize()
        val evidence = Path.of(required("PEST_PHASE_B_EVIDENCE_ROOT")).toAbsolutePath().normalize()
        val runId = required("PEST_PHASE_B_RUN_ID")
        val source = required("PEST_PHASE_B_SOURCE_COMMIT")
        val shardIndex = required("PEST_PHASE_B_SHARD_INDEX").toInt()
        val expectedShardSha = required("PEST_PHASE_B_EXPECTED_SHARD_PLAN_SHA256")

        val result = PestPhaseBProductionShardRunner.run(
            registry = PestPhaseBProductionRegistry.build(),
            acceptedBase = base,
            acceptedSupplement = supplement,
            evidenceRoot = evidence,
            runId = runId,
            sourceCommit = source,
            shardIndex = shardIndex,
        )

        result.globalPlanSha256 shouldBe PestPhaseBShardGeometry.GLOBAL_PLAN_SHA256
        result.shardIndex shouldBe shardIndex
        result.shardCount shouldBe PestPhaseBShardGeometry.SHARD_COUNT
        result.expectedRows shouldBe PestPhaseBShardGeometry.expectedRows(shardIndex)
        result.completedRows shouldBe result.expectedRows
        result.shardPlanSha256 shouldBe expectedShardSha
    }
})
