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
        result.expectedRows shouldBe 34_912_840L
        result.completedRows shouldBe 34_912_840L
        result.planSha256 shouldBe "bcc577b1596c7b37b7f14f0c70d68fefafcf569de3b74f561a0b8317e6e1cc71"
    }
})
