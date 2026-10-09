package com.wingedsheep.gym

import java.nio.file.Files
import java.nio.file.Path

/** Eight excluded, engine-free checks shared by standalone local and focused Kotest execution. */
object PlayFirstTimingFixtures {
    private fun path(name: String): Path {
        val configured = System.getenv("PLAY_FIRST_TIMING_FIXTURE_ROOT")
        val root = if (configured == null) Files.createTempDirectory("play-first-timing-$name-")
            else Files.createDirectory(Path.of(configured).resolve(name))
        return root.resolve("timing.jsonl")
    }
    private val identity = mapOf("caseId" to "EXCLUDED_TIMING_FIXTURE", "source" to "synthetic")
    val cases: List<Pair<String, () -> Unit>> = listOf(
        "start_is_visible_before_body" to {
            val p = path("before-body")
            PlayFirstTimingJournal(p, identity).use { journal ->
                journal.measure("CHOOSE_ACTION") {
                    val rows = Files.readAllLines(p)
                    check(rows.size == 1 && rows[0].contains("CALL_START"))
                }
            }
            check(Files.readAllLines(p).size == 2)
        },
        "one_call_same_thread_same_reference" to {
            val p = path("same-reference"); val value = Any(); var calls = 0
            val thread = Thread.currentThread()
            PlayFirstTimingJournal(p, identity).use { journal ->
                val result = journal.measure("RESPOND_DECISION") {
                    calls++; check(Thread.currentThread() === thread); value
                }
                check(result === value && calls == 1)
            }
        },
        "original_exception_rethrown_without_retry" to {
            val p = path("error"); val expected = IllegalStateException("fixture failure"); var calls = 0
            PlayFirstTimingJournal(p, identity).use { journal ->
                val actual = runCatching { journal.measure("SUBMIT") { calls++; throw expected } }.exceptionOrNull()
                check(actual === expected && calls == 1)
            }
            val text = Files.readString(p)
            check(text.contains("CALL_ERROR") && !text.contains("CALL_END"))
        },
        "duration_is_monotonic_and_context_is_retained" to {
            val p = path("duration"); var tick = 100L
            PlayFirstTimingJournal(p, identity, nanoClock = { tick }, wallClock = { "EXCLUDED" }).use { journal ->
                journal.measure("CHOOSE_ACTION", mapOf("nextSequence" to "18", "actor" to "fixture-seat")) { tick = 145 }
            }
            val text = Files.readString(p)
            check(text.contains("\"elapsedNanos\":45") && text.contains("\"nextSequence\":\"18\""))
            check(text.contains("EXCLUDED_TIMING_FIXTURE"))
        },
        "duplicate_path_refused_without_truncation" to {
            val p = path("duplicate"); Files.writeString(p, "original")
            check(runCatching { PlayFirstTimingJournal(p, identity) }.isFailure)
            check(Files.readString(p) == "original")
        },
        "invalid_phase_never_calls_body" to {
            val p = path("invalid-phase"); var called = false
            PlayFirstTimingJournal(p, identity).use { journal ->
                check(runCatching { journal.measure("bad phase") { called = true } }.isFailure)
            }
            check(!called && Files.size(p) == 0L)
        },
        "closed_journal_never_calls_body" to {
            val p = path("closed"); var called = false
            val journal = PlayFirstTimingJournal(p, identity); journal.close()
            check(runCatching { journal.measure("CHOOSE_ACTION") { called = true } }.isFailure)
            check(!called)
        },
        "escaping_and_nested_spans_remain_distinct" to {
            val p = path("escaping")
            PlayFirstTimingJournal(p, mapOf("caseId" to "quote\"\n\\\t\u0001")).use { journal ->
                journal.measure("OUTER") { journal.measure("INNER") { Unit } }
            }
            val rows = Files.readAllLines(p)
            check(rows.size == 4 && rows[0].contains("\\u0001") && rows[0].contains("\\n"))
            check(rows[0].contains("\"span\":1") && rows[1].contains("\"span\":2"))
            check(rows[2].contains("\"span\":2") && rows[3].contains("\"span\":1"))
        },
    )
    @JvmStatic fun main(args: Array<String>) {
        cases.forEach { (name, verify) -> verify(); println("PASS $name") }
        println("EXCLUDED_TIMING_QUALIFICATION ${cases.size}/${cases.size}; NO_ENGINE_OR_GAME")
    }
}
