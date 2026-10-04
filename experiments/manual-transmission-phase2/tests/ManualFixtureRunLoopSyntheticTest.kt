package com.wingedsheep.gym.manual

/** Local standalone loop tests only. No game engine, decks, seeds, claims or outcomes. */
object ManualFixtureRunLoopSyntheticTest {
    private var cases=0
    private fun test(name:String, body:()->Unit) { body();cases++;println("PASS $name") }
    private fun fails(body:()->Unit) { var caught=false;try { body() } catch (_:Exception) { caught=true };check(caught) }
    private fun loop(steps:Int=2, millis:Long=10, clock:()->Long={0L}) =
        ManualPhaseTwoFixtureRunLoop<String>(ManualFixtureStepBudget(steps,millis),clock)

    @JvmStatic fun main(args:Array<String>) {
        test("exact_step_cap_no_extra_submission") {
            var steps=0;var finish=0;var reason=""
            val result=loop().runOnce({false},{steps++;false},{kind,_ -> reason=kind},{finish++;"trace"})
            check(steps==2 && finish==1 && reason=="RESOURCE_CAP" && result=="trace")
        }
        test("terminal_on_last_step_precedes_cap") {
            var steps=0;var stops=0
            loop().runOnce({steps==2},{steps++;false},{_,_ -> stops++},{"trace"})
            check(steps==2 && stops==0)
        }
        test("already_terminal_does_not_call_step") {
            var steps=0;loop().runOnce({true},{steps++;false},{_,_ -> error("never")},{"trace"});check(steps==0)
        }
        test("explicit_engine_stop_not_retried") {
            var steps=0;var stops=0
            loop().runOnce({false},{steps++;true},{_,_ -> stops++},{"invalid trace"});check(steps==1 && stops==0)
        }
        test("timeout_before_first_step") {
            var clock=0L;var steps=0;var kind=""
            loop(millis=1,clock={val t=clock;clock+=1_000_000;t}).runOnce({false},{steps++;false},{k,_ ->kind=k},{"trace"})
            check(steps==0 && kind=="TIMEOUT")
        }
        test("timeout_after_one_step") {
            var ticks=0;var steps=0;var kind=""
            loop(millis=1,clock={if(ticks++<2)0L else 1_000_000L}).runOnce({false},{steps++;false},{k,_ -> kind=k},{"trace"})
            check(steps==1 && kind=="TIMEOUT")
        }
        test("step_exception_propagates_without_finish_or_retry") {
            val l=loop();var steps=0;var finished=0
            fails { l.runOnce({false},{steps++;error("synthetic")},{_,_ -> error("never")},{finished++;"trace"}) }
            fails { l.runOnce({false},{steps++;true},{_,_ -> Unit},{finished++;"trace"}) }
            check(steps==1 && finished==0)
        }
        test("terminal_probe_exception_consumes_loop") {
            val l=loop();var steps=0
            fails { l.runOnce({error("synthetic")},{steps++;false},{_,_ ->Unit},{"trace"}) }
            fails { l.runOnce({true},{steps++;false},{_,_ ->Unit},{"trace"}) };check(steps==0)
        }
        test("stop_failure_does_not_fabricate_finish") {
            val l=loop(steps=1);var finish=0
            fails { l.runOnce({false},{false},{_,_ ->error("synthetic")},{finish++;"trace"}) };check(finish==0)
        }
        test("finish_failure_cannot_be_retried") {
            val l=loop();var finish=0
            fails { l.runOnce({true},{false},{_,_ ->Unit},{finish++;error("synthetic")}) }
            fails { l.runOnce({true},{false},{_,_ ->Unit},{finish++;"trace"}) };check(finish==1)
        }
        test("successful_loop_is_single_use") {
            val l=loop();l.runOnce({true},{false},{_,_ ->Unit},{"trace"})
            fails { l.runOnce({true},{false},{_,_ ->Unit},{"trace"}) }
        }
        test("clock_regression_stops_before_submission") {
            var t=1L;var steps=0
            fails { loop(clock={t--}).runOnce({false},{steps++;false},{_,_ ->Unit},{"trace"}) };check(steps==0)
        }
        test("zero_step_budget_rejected") { fails { ManualFixtureStepBudget(0,1) } }
        test("negative_step_budget_rejected") { fails { ManualFixtureStepBudget(-1,1) } }
        test("zero_time_budget_rejected") { fails { ManualFixtureStepBudget(1,0) } }
        test("overflow_time_budget_rejected") { fails { ManualFixtureStepBudget(1,Long.MAX_VALUE) } }
        println("$cases/$cases synthetic cases passed; engine/claim/official operations=0")
    }
}
