package com.wingedsheep.gym

/**
 * Excluded test-only timing seam for a future synthetic AI decision fixture.
 * It never creates a game, never reads a G10 case/seed, and never changes the
 * enumerated list or the returned choice. The caller owns durable persistence:
 * it must flush START *before* the measured call to distinguish a hung stage.
 * Neither this seam nor a nominal duration is an execution authority.
 */
object G10DecisionPhaseProbe {
    data class Boundary(
        val kind: String,
        val phase: String,
        val monotonicNanos: Long,
        val elapsedNanos: Long? = null,
        val errorType: String? = null,
    )

    fun <A, R> choose(
        enumerate: () -> List<A>,
        chooseFrom: (List<A>) -> R,
        emit: (Boundary) -> Unit,
        clock: () -> Long = System::nanoTime,
    ): R {
        fun <T> observe(phase: String, call: () -> T): T {
            val started = clock()
            emit(Boundary("CALL_START", phase, started))
            val result = try {
                call()
            } catch (original: Throwable) {
                val ended = clock()
                try {
                    emit(Boundary("CALL_ERROR", phase, ended, ended - started, original::class.java.name))
                } catch (sinkError: Throwable) {
                    original.addSuppressed(sinkError)
                }
                throw original
            }
            val ended = clock()
            emit(Boundary("CALL_END", phase, ended, ended - started))
            return result
        }

        val legal = observe("LEGAL_ACTION_ENUMERATION") { enumerate() }
        return observe("STRATEGY_CHOOSE_FROM") { chooseFrom(legal) }
    }
}
