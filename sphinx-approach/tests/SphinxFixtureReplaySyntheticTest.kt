package com.wingedsheep.gym.sphinx

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND

/** Literal counter envelopes only. Never runs an engine, pilot, official trace or old original. */
object SphinxFixtureReplaySyntheticTest {
    private data class Counter(val value: Int)
    private open class Codec : SphinxFixtureReplayCodec<Counter> {
        var restores=0;var actions=0
        override fun restoreRecorded(envelope:String):Counter {restores++;return Counter(envelope.removePrefix("counter:").toInt())}
        override fun canonicalEnvelope(state:Counter)="counter:${state.value}"
        override fun replayRecordedAction(state:Counter,action:String):Counter {actions++;return Counter(state.value+action.removePrefix("add:").toInt())}
    }
    @JvmStatic fun main(args:Array<String>) {
        var passed=0
        fun test(name:String,block:()->Unit){block();passed++;println("PASS $name")}
        fun id()=SphinxRunnerFixtureIdentity("replay-fixture","a".repeat(40),"b".repeat(64),"c".repeat(64),"d".repeat(64),"e".repeat(64),"f".repeat(64))
        fun fresh()=SphinxStageEWriteAheadJournal.createFixture(Files.createTempDirectory("sphinx-replay-synthetic-").toRealPath(),id())
        fun recorded(stop:String?="CAPABILITY_STOP"):Path {val j=fresh();val d=j.directory;j.use{it.initialize{"counter:0"};it.submit("add:1"){"counter:1"};it.submit("add:2"){"counter:3"};if(stop!=null)it.finish(stop)};return d}
        fun rejects(block:()->Unit){var failed=false;try{block()}catch(_:Exception){failed=true};check(failed)}
        test("complete sequence restores once and replays exact ordered actions") {val d=recorded();val c=Codec();val r=SphinxStageEFixtureReplay.verify(d,id(),c);check(r.verifiedActions==2&&r.completeRecordedSequence&&r.recordedStop=="CAPABILITY_STOP"&&c.restores==1&&c.actions==2)}
        test("read-only bytes and file inventory preserved") {val d=recorded();val names=Files.list(d).use{it.map{p->p.fileName.toString()}.sorted().toList()};val before=Files.readAllBytes(d.resolve("journal.txt"));SphinxStageEFixtureReplay.verify(d,id(),Codec());check(before.contentEquals(Files.readAllBytes(d.resolve("journal.txt"))));check(names==Files.list(d).use{it.map{p->p.fileName.toString()}.sorted().toList()})}
        test("claim-only prefix invokes no codec") {val j=fresh();val d=j.directory;j.close();val c=Codec();val r=SphinxStageEFixtureReplay.verify(d,id(),c);check(!r.completeRecordedSequence&&r.verifiedActions==0&&c.restores==0&&c.actions==0)}
        test("unfinished initialization never retried") {val j=fresh();val d=j.directory;rejects{j.initialize{error("fixed")}};j.close();val c=Codec();val r=SphinxStageEFixtureReplay.verify(d,id(),c);check(!r.completeRecordedSequence&&c.restores==0)}
        test("initialized prefix is not complete") {val j=fresh();val d=j.directory;j.use{it.initialize{"counter:0"}};val c=Codec();val r=SphinxStageEFixtureReplay.verify(d,id(),c);check(!r.completeRecordedSequence&&r.verifiedActions==0&&c.restores==1)}
        test("unfinished first action never submitted") {val j=fresh();val d=j.directory;j.initialize{"counter:0"};rejects{j.submit("add:99"){error("fixed")}};j.close();val c=Codec();val r=SphinxStageEFixtureReplay.verify(d,id(),c);check(r.verifiedActions==0&&c.actions==0&&!r.completeRecordedSequence)}
        test("completed prefix replayed but unmatched next action never submitted") {val j=fresh();val d=j.directory;j.initialize{"counter:0"};j.submit("add:1"){"counter:1"};rejects{j.submit("add:99"){error("fixed")}};j.close();val c=Codec();val r=SphinxStageEFixtureReplay.verify(d,id(),c);check(r.verifiedActions==1&&c.actions==1&&!r.completeRecordedSequence)}
        test("complete action pairs without STOP remain prefix only") {val d=recorded(null);val r=SphinxStageEFixtureReplay.verify(d,id(),Codec());check(r.verifiedActions==2&&!r.completeRecordedSequence&&r.recordedStop==null)}
        test("initial round-trip drift rejects") {val d=recorded();val c=object:Codec(){override fun restoreRecorded(envelope:String)=Counter(9)};rejects{SphinxStageEFixtureReplay.verify(d,id(),c)};check(c.actions==0)}
        test("wrong action result rejects without continuing") {val d=recorded();val c=object:Codec(){override fun replayRecordedAction(state:Counter,action:String):Counter{actions++;return Counter(99)}};rejects{SphinxStageEFixtureReplay.verify(d,id(),c)};check(c.actions==1)}
        test("codec failure propagates without changing original") {val d=recorded();val b=Files.readAllBytes(d.resolve("journal.txt"));val c=object:Codec(){override fun replayRecordedAction(state:Counter,action:String):Counter=error("fixed")};rejects{SphinxStageEFixtureReplay.verify(d,id(),c)};check(b.contentEquals(Files.readAllBytes(d.resolve("journal.txt"))))}
        test("claim pin mismatch rejects before codec") {val d=recorded();val c=Codec();rejects{SphinxStageEFixtureReplay.verify(d,id().copy(runtimeSha256="0".repeat(64)),c)};check(c.restores==0)}
        test("torn tail rejects before codec") {val d=recorded();Files.writeString(d.resolve("journal.txt"),"torn",APPEND);val c=Codec();rejects{SphinxStageEFixtureReplay.verify(d,id(),c)};check(c.restores==0)}
        test("callback evidence mutation detected even on otherwise matching result") {val d=recorded();val c=object:Codec(){override fun replayRecordedAction(state:Counter,action:String):Counter{Files.writeString(d.resolve("journal.txt"),"torn",APPEND);return super.replayRecordedAction(state,action)}};rejects{SphinxStageEFixtureReplay.verify(d,id(),c)}}
        test("canonical envelope whitespace is not normalized") {val d=recorded();val c=object:Codec(){override fun canonicalEnvelope(state:Counter)="counter:${state.value} "};rejects{SphinxStageEFixtureReplay.verify(d,id(),c)}}
        test("all recorded stop labels are preserved without winner inference") {for(s in listOf("FAULT","RESOURCE_STOP","ENGINE_TERMINAL","CAPABILITY_STOP")){val r=SphinxStageEFixtureReplay.verify(recorded(s),id(),Codec());check(r.recordedStop==s&&r.completeRecordedSequence)}}
        println("TOTAL $passed passed; literal envelopes only; zero engine or official-game invocations")
    }
}
