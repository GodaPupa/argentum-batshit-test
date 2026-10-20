package com.wingedsheep.ai.engine

import io.kotest.core.spec.style.FunSpec

/**
 * Explicitly separate, fail-closed Challenger B gate.
 * Not a green test until the real compiled catalog contains every card,
 * including Fierce Guardianship, and exact-slot initialization succeeds.
 */
class ManualPrecisionR1CandidateBPreflightTest : FunSpec({
    test("B all ten replacements compiled and Commander legal before gameplay") {
        val r = ManualPrecisionR1Inputs.registry()
        ManualPrecisionR1Inputs.verifyCompiledLegality("b", r)
        listOf(202610202041L, 202610202042L).forEach { seed ->
            ManualPrecisionR1Inputs.verifyMatchedSlots(seed, r, "b")
        }
    }
})
