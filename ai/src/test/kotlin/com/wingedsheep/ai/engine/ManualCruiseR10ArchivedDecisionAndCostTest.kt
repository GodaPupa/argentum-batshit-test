package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.LandPlayContext
import com.wingedsheep.ai.engine.advisor.modules.ManualCruiseR9ExecutableFetchAdvisor
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * CRUISE R10: source-pinned, non-blinded ORIGINAL R1/R6 traces, not new games.
 * The six previously-unused R9 scoring checkpoints are chosen by the
 * PREDECLARED first-scored-fetch-in-untuned-arm rule (not after R10 scores).
 *
 * Source snapshots are read by exact replay from original immutable archives;
 * event and life vectors must match before either AI profile is inspected.
 */
class ManualCruiseR10ArchivedDecisionAndCostTest : FunSpec({
    val root=Path.of(requireNotNull(System.getenv("MANUAL_R10_ROOT")))
    val archiveRoot=Path.of(requireNotNull(System.getenv("MANUAL_R10_ARCHIVES")))
    val output=Path.of(requireNotNull(System.getenv("MANUAL_R10_OUTPUT")))
    Files.createDirectories(output)
    val protocol=Json.parseToJsonElement(Files.readString(root.resolve(
        "experiments/manual-transmission/research/cruise-sport-race-20261021/CRUISE_R10_HOLDOUT_PROTOCOL.json"
    ))).jsonObject
    fun JsonObject.str(k:String)=requireNotNull(this[k]).jsonPrimitive.content
    fun JsonObject.num(k:String)=requireNotNull(this[k]).jsonPrimitive.int
    check(protocol.str("schema")=="ARGENTUM_MANUAL_CRUISE_R10_PREDECLARED_OUT_OF_FIXTURE_DECISION_AND_COST_V1")
    check(protocol["status"]!!.jsonPrimitive.content ==
        "REGISTERED_BEFORE_R10_TEST_OR_BENCHMARK_EXECUTION")
    val m=protocol["qualification"]!!.jsonObject
    check(m["precision_game_budget"]!!.jsonObject.num("new_games")==0)
    check(m["precision_game_budget"]!!.jsonObject.num("reserved")==2)
    val cases=protocol["cases"]!!.jsonArray.map{it.jsonObject}
    check(cases.size==6 && cases.map{it.str("id")}.distinct().size==6)
    check(cases.none{it.str("seed")=="202610202141"})
    val bench=protocol["benchmark"]!!.jsonObject
    check(bench.num("timed_trials_per_profile")==5)
    check(bench.num("warmup_per_profile")==1)

    val json=Json {
        serializersModule=engineSerializersModule
        allowStructuredMapKeys=true
    }
    val registry=CardRegistry().apply {
        MtgSetCatalog.all.forEach{set -> register(set.cards);register(set.basicLands)}
    }
    val processor=ActionProcessor(registry)
    val simulator=GameSimulator(registry)
    val r5=ManualTransmissionR5Candidate.profile
    val r9=ManualTransmissionCruiseR9Candidate.profile
    check(r5.id=="manual-transmission-r5-survival-opt-in")
    check(r9.id=="manual-transmission-cruise-r9-executable-fetch-opt-in")
    check(r9.advisorModules.dropLast(1)==r5.advisorModules)
    check(AiProfile.CURRENT.advisorModules.isEmpty())

    fun digest(p:Path)=MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(p)).joinToString(""){"%02x".format(it)}
    fun cardName(s:GameState,id:EntityId)=s.getEntity(id)?.get<CardComponent>()?.name
    data class Original(val state:GameState,val action:GameAction,val scenario:JsonObject)
    val originalStates=mutableMapOf<String,Original>()
    val originBenchmark=protocol["origin_counterexample"]!!.jsonObject

    fun original(spec:JsonObject):Original = originalStates.getOrPut(spec.str("id")) {
        val cohort=spec.str("cohort")
        val arm=spec.str("arm")
        val seed=spec.str("seed")
        val target=spec.num("index")
        check(target>1 && target<4000)
        val file=archiveRoot.resolve(cohort+"-"+arm+"-"+seed+"-original.zip")
        check(Files.isRegularFile(file) && digest(file)==spec.str("zip_sha256")){
            "Original artifact missing/changed "+spec.str("id")
        }
        val outer=if(cohort=="R1")"precision-r1-stage1-output/" else "precision-r6-prospective-output/"
        val pre=outer+arm+"-seed-"+seed+"/"
        ZipFile(file.toFile()).use{zip ->
            fun read(s:String)=zip.getInputStream(requireNotNull(zip.getEntry(s)){
                "Original ZIP entry absent: "+s
            }).bufferedReader().readText()
            check(read(outer+"source-sha.txt").trim()==spec.str("source_sha"))
            val metadata=Json.parseToJsonElement(read(pre+"initialization.json")).jsonObject
            val summary=Json.parseToJsonElement(read(pre+"summary.json")).jsonObject
            check(metadata.str("openingPolicy")==
              "Four seats keep seed-derived initial seven, no smoother or mulligans")
            check(summary.str("status")=="COMPLETE" && summary["gameOver"]!!.jsonPrimitive.boolean)
            check(summary.str("profile")==r5.id)
            check(summary.num("accepted")==summary.num("submitted"))
            // Source-version reconciliation: this exact archived R6-A-2143
            // trace diverges when replayed with newer R9 rules at action 716.
            // It MUST be recovered by all-3016-exact-event replay under
            // ORIGINAL R6 code and transferred with a separate provenance
            // manifest and snapshot SHA, never silently skipped.
            val viaOriginalR6 = spec.str("id")=="R10-R6-A-2143"
            var state=if(viaOriginalR6){
                val p=Path.of(requireNotNull(System.getenv("MANUAL_R10_OLD_SOURCE_CHECKPOINT"))){
                    "Original-R6 checkpoint required; newer R9 event stream drifted at #716"
                }
                val meta=Path.of(requireNotNull(System.getenv("MANUAL_R10_OLD_SOURCE_PROVENANCE")))
                val witness=Json.parseToJsonElement(Files.readString(meta)).jsonObject
                check(witness.str("schema")=="ARGENTUM_R10_ORIGINAL_R6_SOURCE_PROVEN_CHECKPOINT_V1")
                check(witness.str("originalR6CommitSha")==
                    "53a5162eb3659310b1878894a90ae58c244a6745")
                check(witness.str("originalZipSha256")==spec.str("zip_sha256"))
                check(witness.num("originalActionBefore")==target)
                check(witness.num("exactOriginalAcceptedActionsReplayed")==target-1 &&
                    witness.num("fullEventAndLifeComparisons")==target-1)
                check(witness.num("newFullGames")==0)
                check(digest(p)==witness.str("snapshotSha256")){
                    "Bridge snapshot did not match independently exact-R6-source provenance"
                }
                json.decodeFromString<GameState>(Files.readString(p))
            } else json.decodeFromString<GameState>(read(pre+"initial-state.json"))
            val records=read(pre+"actions.jsonl").lineSequence().filter(String::isNotBlank)
                .map{json.parseToJsonElement(it).jsonObject}.toList()
            check(records.size==summary.num("accepted")*2)
            if(!viaOriginalR6) for(index in 1 until target) {
                val submit=records[(index-1)*2]
                val result=records[(index-1)*2+1]
                check(submit.str("record")=="submit" && result.str("record")=="result")
                check(submit.num("index")==index && result.num("index")==index)
                val action=json.decodeFromString<GameAction>(submit.str("action"))
                val processed=processor.process(state,action).result
                check(processed.error==null){
                    "Original source event rejected at "+spec.str("id")+" index "+index+": "+processed.error
                }
                check(processed.events.map{it.toString()}==
                    result["events"]!!.jsonArray.map{it.jsonPrimitive.content}){
                    "Original exact event stream diverged in "+spec.str("id")+" at "+index
                }
                check(state.turnOrder.map{processed.state.lifeTotal(it)}==
                    result["life"]!!.jsonArray.map{it.jsonPrimitive.int}){
                    "Original life stream diverged in "+spec.str("id")+" at "+index
                }
                state=processed.state
            }
            val selected=records[(target-1)*2]
            check(selected.str("record")=="submit" && selected.num("index")==target)
            val action=json.decodeFromString<GameAction>(selected.str("action"))
            check(selected.str("actor")==spec.str("seat") && state.turnNumber==selected.num("turn"))
            check(state.pendingDecision==null && state.priorityPlayerId==EntityId(spec.str("seat")))
            val summaryAction=selected["sourceName"]?.jsonPrimitive?.contentOrNull
            if(spec["original_choice"]!=null && spec["original_choice"] !is JsonNull &&
                spec.str("original_choice")!="Pass priority"
            ) {
                check(summaryAction==spec.str("original_choice")){
                    "Original source action changed: "+spec.str("id")
                }
            }
            // Full original selected action must itself remain real-processor legal.
            check(processor.process(state,action).result.error==null)
            Original(state,action,spec)
        }
    }

    fun event(state:GameState, id:String, baseline:GameAction, candidate:GameAction,
        boardLands:Int, override:Double?) {
        fun title(action:GameAction)=when(action){
            is PlayLand->"Play "+cardName(state,action.cardId)
            is CastSpell->"Cast "+cardName(state,action.cardId)
            is ActivateAbility->"Activate "+cardName(state,action.sourceId)
            else->action::class.simpleName?:"Unknown"
        }
        val doc=buildJsonObject{
            put("id",id)
            put("baselineR5",title(baseline))
            put("candidateR9",title(candidate))
            put("freshR5EqualsR9",baseline==candidate)
            put("originalSourceBoardLands",boardLands)
            put("additionalR9FetchScore",override?.let(::JsonPrimitive)?:JsonNull)
            put("historicalSourceProvenance",
                if(id=="R10-R6-A-2143") "EXACT_ORIGINAL_R6_SOURCE_TRANSFERRED_SNAPSHOT"
                else "EXACT_EVENT_LIFE_REPLAY_ON_QUALIFIED_R9_SOURCE")
            put("newerR9EventReplayThroughCheckpointValidated",id!="R10-R6-A-2143")
            put("fullGamesCreated",0)
            put("profileOrDeckPromoted",false)
        }
        Files.writeString(output.resolve(id+".json"),doc.toString()+"\n")
        println("CRUISE_R10_HISTORICAL_STATE="+doc)
    }

    for(spec in cases) {
        val id=spec.str("id")
        test(id+": original source snapshot, >1 lands and R5/R9 stable") {
            val original=original(spec)
            val state=original.state
            val seat=EntityId(spec.str("seat"))
            val landCount=state.controlledBattlefield(seat)
                .count{state.projectedState.hasType(it,"LAND")}
            check(landCount>=spec.num("expected_min_board_lands")){
                "Preregistered late-game negative unexpectedly has fewer than two lands: "+id
            }
            val legal=simulator.getLegalActions(state,seat)
            val fetch=legal.single{
                it.affordable && (it.action as? PlayLand)?.cardId?.let{k->
                    cardName(state,k)==spec.str("fetch")
                }==true
            }
            val override=ManualCruiseR9ExecutableFetchAdvisor.evaluateLandPlay(
                LandPlayContext(state,seat,fetch,-1.0,0.0,simulator)
            )
            check(override==null){"R9 erroneously credited a late-stage fetch: "+id}
            val baseline=AIPlayer.create(registry,seat,r5).chooseAction(state)
            val candidate=AIPlayer.create(registry,seat,r9).chooseAction(state)
            check(candidate==baseline){"R9 modified non-target archived decision at "+id}
            check(processor.process(state,candidate).result.error==null){
                "R10 chosen action rejected by real engine at "+id
            }
            event(state,id,baseline,candidate,landCount,override)
        }
    }

    test("R10-PERF-ONE-STATE: paired fresh R5/R9 decision wall time, no new game") {
        val perfInputs=bench["cases"]!!.jsonArray.map{it.jsonPrimitive.content}
        check(perfInputs==listOf("R10-R9-TUNING-SEED-POSITIVE","R10-R1-A-2111",
                               "R10-R1-C-2111","R10-R6-A-2143"))
        val docs=mutableListOf<JsonElement>()
        for(caseId in perfInputs){
            val fixture=if(caseId=="R10-R9-TUNING-SEED-POSITIVE"){
                val r=originBenchmark
                buildJsonObject{
                    put("id",caseId)
                    put("cohort","R6")
                    put("arm",r.str("arm"))
                    put("seed",r.str("seed"))
                    put("seat",r.str("seat"))
                    put("index",r.num("index"))
                    put("source_sha","53a5162eb3659310b1878894a90ae58c244a6745")
                    put("zip_sha256",r.str("archive_sha256"))
                }
            }else{
                cases.single{it.str("id")==caseId}
            }
            val state=original(fixture).state
            val seat=EntityId(fixture.str("seat"))
            // Fresh AIPlayer per decision; construction is excluded from measurement,
            // and no AI position-memory accumulates between repeated samples.
            fun once(profile:AiProfile):Pair<GameAction,Double>{
                val bot=AIPlayer.create(registry,seat,profile)
                val start=System.nanoTime()
                val answer=bot.chooseAction(state)
                val ms=(System.nanoTime()-start)/1_000_000.0
                check(processor.process(state,answer).result.error==null)
                return answer to ms
            }
            repeat(bench.num("warmup_per_profile")){
                once(r5);once(r9)
            }
            val r5Times=mutableListOf<Double>()
            val r9Times=mutableListOf<Double>()
            val sampledBaseline=mutableListOf<GameAction>()
            val sampledCandidate=mutableListOf<GameAction>()
            repeat(bench.num("timed_trials_per_profile")){i->
                if(i%2==0){
                    val a=once(r5);val b=once(r9)
                    r5Times+=a.second;r9Times+=b.second
                    sampledBaseline+=a.first;sampledCandidate+=b.first
                }else{
                    val b=once(r9);val a=once(r5)
                    r5Times+=a.second;r9Times+=b.second
                    sampledBaseline+=a.first;sampledCandidate+=b.first
                }
            }
            check(sampledBaseline.distinct().size==1 && sampledCandidate.distinct().size==1) {
                "Nondeterministic source-state profile decision in "+caseId
            }
            if(caseId=="R10-R9-TUNING-SEED-POSITIVE"){
                check((sampledBaseline.first() as? PlayLand)?.cardId?.let{
                    cardName(state,it)=="Opal Palace"}==true)
                check((sampledCandidate.first() as? PlayLand)?.cardId?.let{
                    cardName(state,it)=="Misty Rainforest"}==true)
            }else{
                check(sampledBaseline.first()==sampledCandidate.first()) {
                    "R10 benchmark changed a non-target action: "+caseId
                }
            }
            val baselineMedian=r5Times.sorted()[r5Times.size/2]
            val candidateMedian=r9Times.sorted()[r9Times.size/2]
            val doc=buildJsonObject{
                put("case",caseId)
                put("trialsEach",r5Times.size)
                put("r5Ms",JsonArray(r5Times.map(::JsonPrimitive)))
                put("r9Ms",JsonArray(r9Times.map(::JsonPrimitive)))
                put("r5MedianMs",baselineMedian)
                put("r9MedianMs",candidateMedian)
                put("r9OverR5MedianRatio",candidateMedian/baselineMedian.coerceAtLeast(0.001))
                put("additionalR9MedianMs",candidateMedian-baselineMedian)
                put("additionalFullGames",0)
                put("ciRuntimeGeneralization","NOT_SUPPORTED_BY_ONE_CI_RUN")
            }
            docs+=doc
            println("CRUISE_R10_COST_PROFILE="+doc)
            // High bound avoids CI noise while catching a truly blocking policy
            // execution. The wall-time ratio is descriptive, not a win metric.
            if(caseId=="R10-R9-TUNING-SEED-POSITIVE"){
                check(candidateMedian<=1500.0){
                    "R9 early-game source decision exceeds preregistered 1500 ms median bound"
                }
            }
        }
        Files.writeString(output.resolve("R10-PERFORMANCE.json"),
            JsonArray(docs).toString()+"\n")
    }
})
