package com.wingedsheep.gym.manual

/** Explicit synthetic-fixture budget, not an official Phase-2 protocol amendment. */
internal data class ManualFixtureStepBudget(val maxSteps: Int, val maxRuntimeMillis: Long) {
    init {
        require(maxSteps > 0)
        require(maxRuntimeMillis in 1..(Long.MAX_VALUE / 1_000_000))
    }
}

/**
 * One-use, engine-independent loop control for prospective trusted-runner fixtures.
 * No initializer, seed, allocation, journal, pilot, retry, or fallback is implemented here.
 * The caller owns the trusted boundaries and must supply an already initialized fixture.
 * A thrown callback permanently consumes this loop; no fabricated completion is returned.
 */
internal class ManualPhaseTwoFixtureRunLoop<T>(
    private val budget: ManualFixtureStepBudget,
    private val clockNanos: () -> Long = System::nanoTime,
) {
    private var consumed = false

    @Synchronized
    fun runOnce(
        terminal: () -> Boolean,
        step: () -> Boolean,
        stop: (String, String) -> Unit,
        finish: () -> T,
    ): T {
        check(!consumed) { "Fixture loop already consumed" }
        consumed = true
        val started = clockNanos()
        var attempts = 0
        while (true) {
            // A real terminal submission at the final permitted step wins over the cap.
            if (terminal()) return finish()
            val elapsed = clockNanos() - started
            check(elapsed >= 0) { "Fixture monotonic clock regressed" }
            if (attempts >= budget.maxSteps) {
                stop("RESOURCE_CAP", "FIXTURE_SUBMISSION_LIMIT:${budget.maxSteps}")
                return finish()
            }
            if (elapsed >= budget.maxRuntimeMillis * 1_000_000) {
                stop("TIMEOUT", "FIXTURE_WALL_LIMIT_MS:${budget.maxRuntimeMillis}")
                return finish()
            }
            attempts++
            // True means the owned telemetry boundary has stopped (e.g. engine rejection),
            // not that an action failed and should be retried.
            if (step()) return finish()
        }
    }
}
