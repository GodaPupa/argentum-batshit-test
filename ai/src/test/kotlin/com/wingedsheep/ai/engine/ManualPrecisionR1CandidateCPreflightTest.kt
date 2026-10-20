package com.wingedsheep.ai.engine

import io.kotest.core.spec.style.FunSpec

/** No gameplay. Optional nine-slot C source and real four-seat initializer preflight. */
class ManualPrecisionR1CandidateCPreflightTest : FunSpec({
    test("C preserves the baseline card positions with exactly nine substitutions") {
        ManualPrecisionR1Inputs.verifySlotDiffs()
        val registry = ManualPrecisionR1Inputs.registry()
        ManualPrecisionR1Inputs.verifyCompiledLegality("c", registry)
        listOf(202610202041L, 202610202042L).forEach { seed ->
            ManualPrecisionR1Inputs.verifyMatchedSlots(seed, registry, "c")
        }
    }
})
