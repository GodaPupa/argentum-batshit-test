package com.wingedsheep.engine.support

import io.kotest.core.extensions.Extension
import io.kotest.core.extensions.TestCaseExtension
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestType
import io.kotest.engine.test.TestResult
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * Shared test-suite safety net against runaway tests, installed by every module's `ProjectConfig`.
 *
 * Background: an in-flight card or feature can introduce an engine infinite loop (the engine's
 * own SBA checker already converts unbreakable loops to a draw — see [com.wingedsheep.engine
 * .mechanics.StateBasedActionChecker] — but a brand-new effect/trigger can still spin in a loop
 * that predates any cap). With no test timeout, the hung test pins a CPU core and keeps the whole
 * Gradle test JVM alive *forever*: we had worker JVMs stuck for 13+ hours, burning a core each.
 *
 * This guard bounds every test two ways:
 *
 *  1. [defaultTestTimeout] — a Kotest per-test *soft* timeout. Fails a hung test cleanly when the
 *     loop is cooperative (suspends / checks cancellation). A test that legitimately needs longer
 *     overrides it with `.config(timeout = …)`.
 *  2. [HardTimeoutWatchdog] — a daemon watchdog that thread-dumps and **halts the JVM** if a single
 *     leaf test outruns a hard cap. This is the only thing that can bound a tight CPU loop, which a
 *     coroutine timeout cannot interrupt. A 13-hour hang becomes a sub-minute failure whose stderr
 *     dump points straight at the looping frame.
 *
 * Both relax under `-Dbenchmark=true`: benchmarks intentionally run long (and are disabled unless
 * that flag is set), so the soft timeout opens up and the hard watchdog is not installed.
 *
 * Tunable via system properties: `-DtestTimeoutSeconds=…`, `-DtestHardTimeoutSeconds=…`.
 */
object TestHangGuard {
    private val benchmarkMode = System.getProperty("benchmark") == "true"

    /** Kotest default per-test timeout. Override per test with `.config(timeout = …)` when needed. */
    val defaultTestTimeout: Duration =
        if (benchmarkMode) 24.hours
        else (System.getProperty("testTimeoutSeconds")?.toLongOrNull() ?: 120L).seconds

    private val hardCap: Duration =
        (System.getProperty("testHardTimeoutSeconds")?.toLongOrNull() ?: 300L).seconds

    /** Extensions to install in each module's `ProjectConfig`. Empty in benchmark mode. */
    fun extensions(): List<Extension> =
        if (benchmarkMode) emptyList() else listOf(HardTimeoutWatchdog(hardCap))
}

private class HardTimeoutWatchdog(private val hardCap: Duration) : TestCaseExtension {

    override suspend fun intercept(
        testCase: TestCase,
        execute: suspend (TestCase) -> TestResult
    ): TestResult {
        // Only arm for leaf tests — a container legitimately spans the runtime of all its children.
        if (testCase.type != TestType.Test) return execute(testCase)

        val handle = scheduler.schedule(
            { dumpAndHalt(testCase) },
            hardCap.inWholeMilliseconds,
            TimeUnit.MILLISECONDS
        )
        try {
            return execute(testCase)
        } finally {
            handle.cancel(false)
        }
    }

    private fun dumpAndHalt(testCase: TestCase) {
        val name = "${testCase.spec::class.simpleName} > ${testCase.name.name}"
        System.err.println(
            "\n================ TEST HANG WATCHDOG ================\n" +
                "Test '$name' exceeded the hard cap of $hardCap without finishing.\n" +
                "This almost always means an infinite loop in engine/card code reached by this test.\n" +
                "Dumping all thread stacks, then halting the JVM so it can't hang CI/local for hours.\n" +
                "(Tune with -DtestHardTimeoutSeconds=… or run benchmarks with -Dbenchmark=true.)\n"
        )
        Thread.getAllStackTraces()
            .toSortedMap(compareBy { it.name })
            .forEach { (thread, frames) ->
                System.err.println("\"${thread.name}\" ${thread.state}")
                frames.forEach { System.err.println("\tat $it") }
                System.err.println()
            }
        System.err.flush()
        // halt(), not exit(): the JVM is in an undefined (looping) state, so skip shutdown hooks
        // and finalizers and terminate immediately.
        Runtime.getRuntime().halt(13)
    }

    private companion object {
        val scheduler = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "test-hang-watchdog").apply { isDaemon = true }
        }
    }
}
