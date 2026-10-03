package com.wingedsheep.gym.manual

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Standalone inert filesystem tests. No engine, real pilot or official input is loaded. */
object ManualFixtureLifecycleSyntheticTest {
    @JvmStatic fun main(args: Array<String>) {
        var passed = 0
        fun test(name: String, block: () -> Unit) { block(); passed++; println("PASS $name") }
        fun root(): Path = Files.createTempDirectory("manual-lifecycle-synthetic-").toRealPath()
        fun identity(id: String = "manual-fixture-case") = ManualFixtureIdentity(id, "a".repeat(40), "CRUISE",
            List(4) { "b".repeat(64) }, List(4) { "c".repeat(64) }, "d".repeat(64))
        fun rejects(block: () -> Unit) { var failed = false; try { block() } catch (_: Exception) { failed = true }; check(failed) }
        fun run(r: Path, i: ManualFixtureIdentity = identity()): String =
            ManualPhaseTwoFixtureLifecycle.runNew(r, i, { "inert-state" }, { "$it-result" }, { it.toByteArray() })
        test("reservation and intent precede initializer; run intent precedes runner") {
            val r = root(); val i = identity(); val d = r.resolve(i.fixtureId)
            val value = ManualPhaseTwoFixtureLifecycle.runNew(r, i, {
                check(Files.readAllBytes(d.resolve("fixture-identity.txt")).contentEquals(i.bytes()))
                check(Files.readString(d.resolve("initialization-intent.txt")) == "INITIALIZE_ONCE\n")
                check(!Files.exists(d.resolve("initialized.txt"))); "state"
            }, {
                check(Files.exists(d.resolve("initialized.txt")) && Files.exists(d.resolve("run-intent.txt")))
                check(it == "state"); "result"
            }, { it.toByteArray() })
            check(value == "result")
            val hash = MessageDigest.getInstance("SHA-256").digest("result".toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }
            check(Files.readString(d.resolve("complete.txt")) == "$hash\n")
        }
        test("duplicate identity rejects before callback") {
            val r=root();run(r);var calls=0
            rejects { ManualPhaseTwoFixtureLifecycle.runNew(r,identity(),{ calls++;0 },{it},{byteArrayOf(1)}) };check(calls==0)
        }
        test("initializer failure is permanently consumed and retained") {
            val r=root();var calls=0
            repeat(2) { rejects { ManualPhaseTwoFixtureLifecycle.runNew<Int,Int>(r,identity(),{calls++;error("fixed")},{it},{byteArrayOf(1)}) } }
            check(calls==1);val d=r.resolve(identity().fixtureId)
            check(Files.exists(d.resolve("fault.txt")) && !Files.exists(d.resolve("initialized.txt")))
        }
        test("runner failure cannot retry initializer or runner") {
            val r=root();var init=0;var runs=0
            repeat(2) { rejects { ManualPhaseTwoFixtureLifecycle.runNew<Int,Int>(r,identity(),{init++;1},{runs++;error("fixed")},{byteArrayOf(1)}) } }
            check(init==1 && runs==1);check(!Files.exists(r.resolve(identity().fixtureId).resolve("complete.txt")))
        }
        test("encoding failure retains run and has no completion") {
            val r=root();rejects { ManualPhaseTwoFixtureLifecycle.runNew(r,identity(),{1},{it},{error("encode")}) }
            val d=r.resolve(identity().fixtureId);check(Files.exists(d.resolve("run-intent.txt")) && !Files.exists(d.resolve("complete.txt")))
        }
        test("empty result refused without completion") { val r=root();rejects { ManualPhaseTwoFixtureLifecycle.runNew(r,identity(),{1},{it},{byteArrayOf()}) };check(!Files.exists(r.resolve(identity().fixtureId).resolve("complete.txt"))) }
        test("oversize result refused") { val r=root();rejects { ManualPhaseTwoFixtureLifecycle.runNew(r,identity(),{1},{it},{ByteArray(16*1024*1024+1)}) } }
        test("preexisting destination file preserved") { val r=root();val p=r.resolve(identity().fixtureId);Files.writeString(p,"old");rejects {run(r)};check(Files.readString(p)=="old") }
        test("preexisting partial directory preserved") { val r=root();val p=Files.createDirectory(r.resolve(identity().fixtureId));Files.writeString(p.resolve("partial"),"old");rejects {run(r)};check(Files.readString(p.resolve("partial"))=="old") }
        test("destination symlink rejected") { val r=root();val p=r.resolve(identity().fixtureId);val t=Files.createDirectory(r.resolve("target"));Files.createSymbolicLink(p,t);rejects {run(r)};check(Files.list(t).use {it.count()}==0L) }
        test("root symlink rejected") { val r=root();val alias=r.resolveSibling(r.fileName.toString()+"-alias");Files.createSymbolicLink(alias,r);rejects {run(alias)} }
        test("four seats are mandatory") { val r=root();rejects {run(r,identity().copy(deckSha256=listOf("b".repeat(64))))};check(Files.list(r).use{it.count()}==0L) }
        test("gear source and runtime pins mandatory") { val r=root();for(i in listOf(identity().copy(gear="TURBO"),identity().copy(sourceCommit="main"),identity().copy(runtimeSha256=""))) rejects {run(r,i)} }
        test("official-looking and path-traversal identities rejected") { val r=root();for(id in listOf("R1-row-1","../manual-fixture-x","manual-fixture-a/b")) rejects {run(r,identity(id))} }
        test("caller list mutation does not rewrite reserved identity") { val r=root();val decks=MutableList(4){"b".repeat(64)};val i=identity().copy(deckSha256=decks);val before=i.bytes();ManualPhaseTwoFixtureLifecycle.runNew(r,i,{decks[0]="e".repeat(64);1},{it},{byteArrayOf(1)});check(Files.readAllBytes(r.resolve(i.fixtureId).resolve("fixture-identity.txt")).contentEquals(before)) }
        test("existing result cannot be overwritten") { val r=root();val i=identity();rejects { ManualPhaseTwoFixtureLifecycle.runNew(r,i,{1},{Files.writeString(r.resolve(i.fixtureId).resolve("result.bin"),"outside");it},{byteArrayOf(1)}) };check(Files.readString(r.resolve(i.fixtureId).resolve("result.bin"))=="outside") }
        test("failure marker error is suppressed not substituted") { val r=root();val i=identity();val original=IllegalStateException("original");try {ManualPhaseTwoFixtureLifecycle.runNew<Int,Int>(r,i,{Files.writeString(r.resolve(i.fixtureId).resolve("fault.txt"),"prior");throw original},{it},{byteArrayOf(1)});error("missing failure")}catch(e:IllegalStateException){check(e===original && e.suppressed.isNotEmpty())} }
        test("independent fixture IDs do not share results") {val r=root();run(r,identity("manual-fixture-a"));run(r,identity("manual-fixture-b"));check(Files.list(r).use{it.count()}==2L)}
        println("TOTAL $passed passed; zero engine invocations; synthetic reservations only")
    }
}
