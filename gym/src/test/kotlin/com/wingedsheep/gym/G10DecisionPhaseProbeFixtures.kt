package com.wingedsheep.gym

/** Standalone, synthetic, engine-free tests. No G10 replay, fixture seed or gameplay. */
object G10DecisionPhaseProbeFixtures {
    @JvmStatic fun main(args: Array<String>) {
        val events = mutableListOf<G10DecisionPhaseProbe.Boundary>()
        val legal = listOf("pass", "cast")
        var listReference: List<String>? = null
        var enumerations = 0
        var strategies = 0
        var tick = 1_000L
        val chosen = G10DecisionPhaseProbe.choose(
            enumerate = { enumerations++; legal },
            chooseFrom = { options -> strategies++; listReference = options; options[1] },
            emit = events::add,
            clock = { tick.also { tick += 10 } },
        )
        check(chosen == "cast" && enumerations == 1 && strategies == 1 && listReference === legal)
        check(events.map { it.kind to it.phase } == listOf(
            "CALL_START" to "LEGAL_ACTION_ENUMERATION",
            "CALL_END" to "LEGAL_ACTION_ENUMERATION",
            "CALL_START" to "STRATEGY_CHOOSE_FROM",
            "CALL_END" to "STRATEGY_CHOOSE_FROM",
        ))
        check(events.filter { it.kind == "CALL_END" }.all { it.elapsedNanos == 10L })
        println("PASS two-phase same-reference same-action")

        val enumFault = IllegalStateException("synthetic-enumeration-error")
        events.clear()
        var choseAfterFailure = false
        val caught = runCatching {
            G10DecisionPhaseProbe.choose<String, String>(
                enumerate = { throw enumFault },
                chooseFrom = { choseAfterFailure = true; "impossible" },
                emit = events::add,
            )
        }.exceptionOrNull()
        check(caught === enumFault && !choseAfterFailure)
        check(events.map { it.kind } == listOf("CALL_START", "CALL_ERROR"))
        check(events.last().errorType == IllegalStateException::class.java.name)
        println("PASS enumeration-error original-exception no-fallback")

        val chooseFault = UnsupportedOperationException("synthetic-choice-error")
        events.clear()
        enumerations = 0
        val caughtChoose = runCatching {
            G10DecisionPhaseProbe.choose(
                enumerate = { enumerations++; legal },
                chooseFrom = { _: List<String> -> throw chooseFault },
                emit = events::add,
            )
        }.exceptionOrNull()
        check(caughtChoose === chooseFault && enumerations == 1)
        check(events.map { it.kind } == listOf("CALL_START", "CALL_END", "CALL_START", "CALL_ERROR"))
        println("PASS choice-error preserved without retry")

        events.clear()
        tick = 0L
        val sameChoice = G10DecisionPhaseProbe.choose(
            enumerate = { legal },
            chooseFrom = { it.first() },
            emit = events::add,
            clock = { tick.also { tick += if (tick == 0L) 500_000_000_000L else 1L } },
        )
        check(sameChoice == "pass" && events[1].elapsedNanos == 500_000_000_000L)
        check(events[2].phase == "STRATEGY_CHOOSE_FROM")
        println("PASS synthetic very-slow-enumeration-boundary")
        println("EXCLUDED_G10_DECISION_PHASE_FIXTURES 4/4 PASS; NO_ENGINE_NO_GAME")
    }
}
