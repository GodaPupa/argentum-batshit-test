package com.wingedsheep.gym.pest

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.mtg.sets.MtgSetCatalog
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Path

/**
 * Single prospective Phase-B behavioral entrypoint. It has no defaults for accepted inputs,
 * evidence root, run identity, or source identity; the reviewed gate must supply all of them.
 */
class PestPhaseBProductionBankExecutionTest : FunSpec({
    test("execute exact frozen Pest plus Monster Phase-B bank once") {
        fun required(name: String): String =
            requireNotNull(System.getenv(name)?.takeIf { it.isNotBlank() }) { "Missing $name" }

        val base = Path.of(required("PEST_PHASE_B_ACCEPTED_BASE")).toAbsolutePath().normalize()
        val supplement = Path.of(required("PEST_PHASE_B_ACCEPTED_SUPPLEMENT")).toAbsolutePath().normalize()
        val evidence = Path.of(required("PEST_PHASE_B_EVIDENCE_ROOT")).toAbsolutePath().normalize()
        val runId = required("PEST_PHASE_B_RUN_ID")
        val source = required("PEST_PHASE_B_SOURCE_COMMIT")

        val registry = CardRegistry()
        MtgSetCatalog.all.forEach { registry.register(it.cards) }

        val result = PestPhaseBProductionBankRunner.run(
            registry = registry,
            acceptedBase = base,
            acceptedSupplement = supplement,
            evidenceRoot = evidence,
            runId = runId,
            sourceCommit = source,
        )
        result.expectedRows shouldBe 30_328_476L
        result.completedRows shouldBe 30_328_476L
        result.planSha256 shouldBe "c2e15aaa4fcdd08ce2dd9a014c303983d9ffa884887df721a8d2fdfce0a1d064"
    }
})
