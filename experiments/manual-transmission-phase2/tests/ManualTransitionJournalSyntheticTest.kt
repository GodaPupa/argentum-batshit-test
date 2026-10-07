package com.wingedsheep.gym.manual

import java.nio.file.Files
import java.nio.file.Path

/** Literal byte fixtures only: no engine, deck, pilot, seed or official input. */
object ManualTransitionJournalSyntheticTest {
    @JvmStatic fun main(args: Array<String>) {
        var passed = 0
        fun test(name: String, body: (Path) -> Unit) {
            val root = Files.createTempDirectory("manual-transition-test-").toRealPath()
            body(root.resolve("journal"))
            println("PASS $name")
            passed++
        }
        val identity = "fixture-identity".toByteArray()
        val initial = "state:0;events:[]".toByteArray()
        fun create(p: Path) = ManualTransitionJournal.create(p, identity, initial)
        fun inspect(p: Path) = ManualTransitionJournal.inspect(p, identity, initial)
        fun rejects(body: () -> Unit) { check(runCatching(body).isFailure) }
        test("durable-before-callback-and-return") { p ->
            val j = create(p)
            val result = j.transition("increment".toByteArray(), {
                check(Files.readAllBytes(p.resolve("0.intent")).takeLast(9).toByteArray().contentEquals("increment".toByteArray()))
                1
            }, { "state:$it;events:[increment]".toByteArray() })
            check(result == 1 && Files.exists(p.resolve("0.result")))
            j.stop("FIXTURE_STOP")
            val view = inspect(p)
            check(view.transitions.size == 1 && !view.interruptedIntent && view.rawStopClassification == "FIXTURE_STOP")
            check(view.transitions.single().resultEnvelope.contentEquals("state:1;events:[increment]".toByteArray()))
        }
        test("callback-failure-preserves-intent-consumes-writer") { p ->
            val j = create(p); var calls = 0
            rejects { j.transition("a".toByteArray(), { calls++; error("original") }, { byteArrayOf(1) }) }
            check(calls == 1 && inspect(p).interruptedIntent)
            rejects { j.transition(byteArrayOf(2), { calls++ }, { byteArrayOf(3) }) }
            rejects { j.stop("STOP") }; check(calls == 1)
        }
        test("encoder-failure-preserves-intent") { p ->
            val j = create(p)
            rejects { j.transition(byteArrayOf(1), { 7 }, { error("encode") }) }
            check(inspect(p).interruptedIntent)
        }
        test("duplicate-directory-never-reopens") { p ->
            create(p); rejects { create(p) }; check(inspect(p).transitions.isEmpty())
        }
        test("reentrant-transition-refused") { p ->
            val j = create(p)
            j.transition(byteArrayOf(1), {
                rejects { j.transition(byteArrayOf(2), { 1 }, { byteArrayOf(3) }) }; 1
            }, { byteArrayOf(4) })
            check(inspect(p).transitions.size == 1)
        }
        test("missing-stop-is-incomplete") { p ->
            create(p).transition(byteArrayOf(1), { 1 }, { byteArrayOf(2) })
            check(inspect(p).rawStopClassification == null)
        }
        test("identity-and-initial-drift-rejected") { p ->
            create(p)
            rejects { ManualTransitionJournal.inspect(p, byteArrayOf(1), initial) }
            rejects { ManualTransitionJournal.inspect(p, identity, byteArrayOf(1)) }
        }
        test("orphan-and-gap-rejected") { p ->
            create(p); Files.write(p.resolve("1.result"), byteArrayOf(1)); rejects { inspect(p) }
        }
        test("stop-consumes-writer") { p ->
            val j = create(p); j.stop("TIMEOUT")
            rejects { j.stop("SECOND") }
            rejects { j.transition(byteArrayOf(1), { 1 }, { byteArrayOf(2) }) }
            check(inspect(p).rawStopClassification == "TIMEOUT")
        }
        test("partial-result-write-never-retried") { p ->
            val j = create(p); Files.write(p.resolve("0.result"), byteArrayOf(99))
            rejects { j.transition(byteArrayOf(1), { 1 }, { byteArrayOf(2) }) }
            rejects { j.stop("STOP") }
            check(Files.readAllBytes(p.resolve("0.result")).contentEquals(byteArrayOf(99)))
            rejects { inspect(p) }
        }
        check(passed == 10)
        println("RESULT $passed/10")
    }
}
