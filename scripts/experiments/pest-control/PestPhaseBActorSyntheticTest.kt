package com.wingedsheep.gym.pest

/** Invented own-hand facts only; no accepted truth, raw-controller or finite bank is executed. */
object PestPhaseBActorSyntheticTest {
    @JvmStatic fun main(args:Array<String>) {
        var passed=0
        fun test(name:String,block:()->Unit){block();passed++;println("PASS $name")}
        fun land(id:String)=PestLondonCardFacts(id,"Forest",true,0,colorsProduced=setOf('G'))
        fun spell(id:String,cost:Int=1,truth:Boolean?=true)=PestLondonCardFacts(id,"Spell$cost",false,cost,colorsRequired=setOf('G'),deterministicDevelopmentPayable=truth)
        fun hand()=listOf(land("l1"),land("l2"),spell("s1"),spell("s2",2),spell("s3",4),spell("s4",4),spell("s5",6))
        fun vector(h:List<PestLondonCardFacts> = hand(),mull:Int=0,bottom:Int=0):PestLondonPredicateVector {
            val deck=h.groupingBy{it.name}.eachCount().toMutableMap();deck["Forest"]=deck.getOrDefault("Forest",0)+60-h.size
            return PestMonsterLondonPredicateVectorExtractor.extract(deck,h,mull,bottom)
        }
        fun rejects(block:()->Unit){var failed=false;try{block()}catch(_:Exception){failed=true};check(failed)}
        test("two lands with accepted true early certificate keep") {check(PestPhaseBKeepBottomActor.keep(vector()).keep==true)}
        test("all exact early certificates false mulligan") {val h=hand().map{if(!it.isLand)it.copy(deterministicDevelopmentPayable=false) else it};check(PestPhaseBKeepBottomActor.keep(vector(h)).keep==false)}
        test("required unknown M5 stays unknown") {val h=hand().map{if(!it.isLand)it.copy(deterministicDevelopmentPayable=null) else it};val r=PestPhaseBKeepBottomActor.keep(vector(h));check(r.keep==null&&r.reason=="FAIL_CLOSED_UNKNOWN_M5")}
        test("unknown M5 is not defaulted even when another gate rejects") {val v=vector().copy(colorFunctional=false,developmentFunctional=null);check(PestPhaseBKeepBottomActor.keep(v).keep==null)}
        test("forced keep requires no invented truth") {check(PestPhaseBKeepBottomActor.keep(vector().copy(forcedKeep=true,developmentFunctional=null)).keep==true)}
        test("empty observed early set needs no truth query") {val h=List(7){spell("s$it",6,null)};val r=PestPhaseBKeepBottomActor.keep(vector(h));check(r.keep==false&&r.reason=="M3_EMPTY_NO_M5_ATOM_REQUIRED")}
        test("physical one-land hand without acquisition rejected") {check(PestPhaseBKeepBottomActor.keep(vector().copy(physicalLandCount=1)).keep==false)}
        test("qualified virtual second land permits existing minimum") {check(PestPhaseBKeepBottomActor.keep(vector().copy(physicalLandCount=1,guaranteedSecondLandAccess=true)).keep==true)}
        test("six effective lands rejected") {check(PestPhaseBKeepBottomActor.keep(vector().copy(physicalLandCount=6)).keep==false)}
        test("known color failure rejects") {check(PestPhaseBKeepBottomActor.keep(vector().copy(colorFunctional=false)).keep==false)}
        test("zero bottom is exactly empty") {check(PestPhaseBKeepBottomActor.bottom(vector(),hand().map{it.id},0).orderedIds==emptyList<String>())}
        test("expensive first and equal-CMC physical tie retained") {val h=hand();check(PestPhaseBKeepBottomActor.bottom(vector(h,bottom=2),h.map{it.id},2).orderedIds==listOf("s5","s3"))}
        test("same-name cards keep distinct physical IDs") {val h=hand().map{if(it.id=="s5")spell("s5",4) else it};check(PestPhaseBKeepBottomActor.bottom(vector(h,bottom=2),h.map{it.id},2).orderedIds==listOf("s3","s4"))}
        test("excess lands precede spells in physical order") {val h=listOf(land("l1"),land("l2"),land("l3"),land("l4"),land("l5"),spell("s1",6),spell("s2",6));check(PestPhaseBKeepBottomActor.bottom(vector(h,bottom=2),h.map{it.id},2).orderedIds==listOf("l3","l4"))}
        test("protected acquisition and sole land retained") {val ent=PestLondonCardFacts("ent","Generous Ent",false,6,typedCyclingTargets=setOf("Forest"),typedCyclingPayableBySoleLand=true);val h=listOf(land("l1"),ent,spell("s1",1),spell("s2",2),spell("s3",4),spell("s4",4),spell("s5",6));val v=vector(h,bottom=2);check(v.protectedVisibleIds==setOf("l1","ent"));check(PestPhaseBKeepBottomActor.bottom(v,h.map{it.id},2).orderedIds==listOf("s5","s3"))}
        test("fallback does not duplicate a previously chosen ID") {val h=hand();val v=vector(h,bottom=2).copy(expensiveSpellIds=listOf("s5"));check(PestPhaseBKeepBottomActor.bottom(v,h.map{it.id},2).orderedIds==listOf("s5","l1"))}
        test("incomplete candidates unqualified not silently padded") {val h=hand();val v=vector(h,bottom=2).copy(expensiveSpellIds=emptyList(),fallbackVisibleIdsInPhysicalOrder=emptyList());check(PestPhaseBKeepBottomActor.bottom(v,h.map{it.id},2).orderedIds==null)}
        test("stale or hidden physical ID rejected") {val h=hand();rejects{PestPhaseBKeepBottomActor.bottom(vector(h,bottom=2).copy(expensiveSpellIds=listOf("opponent-hidden")),h.map{it.id},2)}}
        test("protected candidate injection rejected") {val h=hand();rejects{PestPhaseBKeepBottomActor.bottom(vector(h,bottom=2).copy(protectedVisibleIds=setOf("s5")),h.map{it.id},2)}}
        test("duplicate physical hand or rank rejected") {val h=hand();rejects{PestPhaseBKeepBottomActor.bottom(vector(h,bottom=2),List(7){"same"},2)};rejects{PestPhaseBKeepBottomActor.bottom(vector(h,bottom=2).copy(expensiveSpellIds=listOf("s5","s5")),h.map{it.id},2)}}
        test("land-spell rank overlap rejected") {val h=hand();rejects{PestPhaseBKeepBottomActor.bottom(vector(h,bottom=2).copy(excessLandIdsInPhysicalOrder=listOf("s5")),h.map{it.id},2)}}
        test("stale bottom count and invalid virtual-land structure rejected") {val h=hand();rejects{PestPhaseBKeepBottomActor.bottom(vector(h),h.map{it.id},2)};rejects{PestPhaseBKeepBottomActor.keep(vector().copy(guaranteedSecondLandAccess=true))}}
        println("TOTAL $passed passed; synthetic own facts only; no raw oracle or accepted bank invoked")
    }
}
