package com.wingedsheep.gym.sphinx

import java.nio.file.Files
import java.nio.file.Path

/** Standalone author tests: literal strings and temp files, no game engine/pilots/seeds. */
object SphinxWriteAheadJournalSyntheticTest {
    private var count = 0
    private fun test(name: String, body: () -> Unit) { body(); count++; println("PASS $name") }
    private fun fails(body: () -> Unit) { var failed=false;try { body() } catch (_: Exception) { failed=true };check(failed) }
    private fun identity(id: String = "synthetic") = SphinxRunnerFixtureIdentity(id, "a".repeat(40),
        "1".repeat(64), "2".repeat(64), "3".repeat(64), "4".repeat(64), "5".repeat(64))
    private fun fixture(body: (Path, SphinxRunnerFixtureIdentity) -> Unit) {
        val root=Files.createTempDirectory("sphinx-write-ahead-synthetic-").toRealPath()
        try { body(root, identity()) } finally { Files.walk(root).use { it.sorted(Comparator.reverseOrder()).forEach(Files::delete) } }
    }

    @JvmStatic fun main(args: Array<String>) {
        test("claim_and_intent_durable_before_initializer") { fixture { root,id ->
            SphinxStageEWriteAheadJournal.createFixture(root,id).use { journal ->
                journal.initialize {
                    check(Files.readAllBytes(journal.directory.resolve("claim.txt")).contentEquals(id.claimBytes()))
                    check(SphinxStageEWriteAheadJournal.inspect(journal.directory,id).phase=="INITIALIZING")
                    "literal initial state"
                }
            }
        } }
        test("action_intent_durable_before_submitter") { fixture { root,id ->
            SphinxStageEWriteAheadJournal.createFixture(root,id).use { j ->
                j.initialize { "state" };j.submit("literal action") {
                    check(SphinxStageEWriteAheadJournal.inspect(j.directory,id).phase=="SUBMITTING");"literal result"
                };j.finish("CAPABILITY_STOP")
                val p=SphinxStageEWriteAheadJournal.inspect(j.directory,id)
                check(p.structurallyComplete && !p.hasUnfinishedIntent && p.entries.size==5)
            }
        } }
        test("duplicate_identity_never_reopens") { fixture { root,id ->
            SphinxStageEWriteAheadJournal.createFixture(root,id).close()
            fails { SphinxStageEWriteAheadJournal.createFixture(root,id) }
        } }
        test("initializer_exception_preserves_unfinished_intent") { fixture { root,id ->
            SphinxStageEWriteAheadJournal.createFixture(root,id).use { j ->
                fails { j.initialize { error("synthetic initializer failure") } }
                check(SphinxStageEWriteAheadJournal.inspect(j.directory,id).hasUnfinishedIntent)
                fails { j.initialize { error("must never run") } }
            }
        } }
        test("submission_exception_is_not_retried") { fixture { root,id ->
            var callbacks=0
            SphinxStageEWriteAheadJournal.createFixture(root,id).use { j ->
                j.initialize { "state" }
                fails { j.submit("action") { callbacks++;error("synthetic") } }
                fails { j.submit("retry") { callbacks++;"result" } }
                fails { j.finish("FAULT") }
                check(callbacks==1 && SphinxStageEWriteAheadJournal.inspect(j.directory,id).phase=="SUBMITTING")
            }
        } }
        test("submit_before_initialization_refused") { fixture { root,id ->
            SphinxStageEWriteAheadJournal.createFixture(root,id).use { j -> fails { j.submit("action") { error("never") } } }
        } }
        test("second_initializer_refused") { fixture { root,id ->
            var n=0;SphinxStageEWriteAheadJournal.createFixture(root,id).use { j ->
                j.initialize { n++;"state" };fails { j.initialize { n++;"state" } };check(n==1)
            }
        } }
        test("post_stop_submission_refused") { fixture { root,id ->
            SphinxStageEWriteAheadJournal.createFixture(root,id).use { j ->
                j.initialize { "state" };j.finish("CAPABILITY_STOP");fails { j.submit("action") { error("never") } }
            }
        } }
        test("closed_writer_refused") { fixture { root,id ->
            val j=SphinxStageEWriteAheadJournal.createFixture(root,id);j.close();fails { j.initialize { error("never") } }
        } }
        test("claim_pin_drift_refused") { fixture { root,id ->
            SphinxStageEWriteAheadJournal.createFixture(root,id).use { j ->
                fails { SphinxStageEWriteAheadJournal.inspect(j.directory,id.copy(ownPilotSha256="9".repeat(64))) }
            }
        } }
        test("journal_byte_drift_refused_before_callback") { fixture { root,id ->
            var n=0;SphinxStageEWriteAheadJournal.createFixture(root,id).use { j ->
                j.initialize { "state" };Files.writeString(j.directory.resolve("journal.txt"),"corrupt\n")
                fails { j.submit("action") { n++;"result" } };check(n==0)
            }
        } }
        test("torn_tail_refused") { fixture { root,id ->
            SphinxStageEWriteAheadJournal.createFixture(root,id).use { j ->
                j.initialize { "state" };val p=j.directory.resolve("journal.txt");val b=Files.readAllBytes(p)
                Files.write(p,b.copyOf(b.size-1));fails { SphinxStageEWriteAheadJournal.inspect(j.directory,id) }
            }
        } }
        test("read_only_inspection_preserves_bytes") { fixture { root,id ->
            SphinxStageEWriteAheadJournal.createFixture(root,id).use { j ->
                j.initialize { "state\nwith Unicode λ" };val p=j.directory.resolve("journal.txt");val b=Files.readAllBytes(p)
                SphinxStageEWriteAheadJournal.inspect(j.directory,id);check(Files.readAllBytes(p).contentEquals(b))
            }
        } }
        test("path_traversal_identity_refused") { fixture { root,id ->
            fails { SphinxStageEWriteAheadJournal.createFixture(root,id.copy(fixtureId="../elsewhere")) }
        } }
        test("root_alias_refused") { fixture { root,id ->
            val a=root.resolve("alias");Files.createSymbolicLink(a,root)
            fails { SphinxStageEWriteAheadJournal.createFixture(a,id) }
        } }
        test("claim_symlink_refused") { fixture { root,id ->
            SphinxStageEWriteAheadJournal.createFixture(root,id).use { j ->
                val p=j.directory.resolve("claim.txt");val t=root.resolve("other");Files.move(p,t);Files.createSymbolicLink(p,t)
                fails { SphinxStageEWriteAheadJournal.inspect(j.directory,id) }
            }
        } }
        test("unknown_stop_classification_refused") { fixture { root,id ->
            SphinxStageEWriteAheadJournal.createFixture(root,id).use { j ->
                j.initialize { "state" };fails { j.finish("QUALIFIED_WIN") }
            }
        } }
        test("blank_action_stops_before_submitter") { fixture { root,id ->
            var n=0;SphinxStageEWriteAheadJournal.createFixture(root,id).use { j ->
                j.initialize { "state" };fails { j.submit(" ") { n++;"result" } };check(n==0)
                fails { j.submit("replacement") { n++;"result" } };check(n==0)
            }
        } }
        println("$count/$count synthetic cases passed; engine/pilot/official operations=0")
    }
}
