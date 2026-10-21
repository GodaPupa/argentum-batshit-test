package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * R10 predeclared adversarial priority-window check, NOT actual opponent
 * behavior. A card from a real original opponent LIBRARY is synthetically
 * added to their HAND solely to test the legal response mechanism. All
 * prior 41 historical accepted actions and events replay exactly.
 *
 * This microfork cannot demonstrate any full-game advantage or predict
 * an opposing pilot's choice and creates ZERO new full Commander games.
 */
class ManualCruiseR10InterruptibleFetchTest : FunSpec({
    val root=Path.of(requireNotNull(System.getenv("MANUAL_R10_ROOT")))
    val archives=Path.of(requireNotNull(System.getenv("MANUAL_R10_ARCHIVES")))
    val output=Path.of(requireNotNull(System.getenv("MANUAL_R10_OUTPUT")))
    Files.createDirectories(output)
    val m=Json.parseToJsonElement(Files.readString(root.resolve(
        "experiments/manual-transmission/research/cruise-sport-race-20261021/CRUISE_R10_HOLDOUT_PROTOCOL.json"
    ))).jsonObject
    check(m["schema"]!!.jsonPrimitive.content==
        "ARGENTUM_MANUAL_CRUISE_R10_PREDECLARED_OUT_OF_FIXTURE_DECISION_AND_COST_V1")
    val r=m["adversarial_response"]!!.jsonObject
    check(r["id"]!!.jsonPrimitive.content=="R10-SYNTHETIC-PACT-RESPONSE")
    val budget=m["qualification"]!!.jsonObject["precision_game_budget"]!!.jsonObject
    check(budget["new_games"]!!.jsonPrimitive.int==0)
    val json=Json{serializersModule=engineSerializersModule;allowStructuredMapKeys=true}
    val registry=CardRegistry().apply{
        MtgSetCatalog.all.forEach{set->register(set.cards);register(set.basicLands)}
    }
    val processor=ActionProcessor(registry)
    val simulator=GameSimulator(registry)
    val actor=EntityId("manual-mirror-1")
    val adversary=EntityId("manual-mirror-2")
    fun cardName(state:GameState,id:EntityId)=state.getEntity(id)?.get<CardComponent>()?.name

    test("R10-SYNTHETIC-PACT-RESPONSE: legal opponent counter window after conditional R9 ramp") {
        val file=archives.resolve("R6-a-202610202141-original.zip")
        check(Files.isRegularFile(file))
        val sha=MessageDigest.getInstance("SHA-256")
            .digest(Files.readAllBytes(file)).joinToString(""){"%02x".format(it)}
        check(sha=="a4396d43fdd831f5f67f2a6b865630af9a90ff1420c2b127be6e6b2f294a6efe")
        var original:GameState
        ZipFile(file.toFile()).use{zip->
            val outer="precision-r6-prospective-output/"
            val pre=outer+"a-seed-202610202141/"
            fun read(name:String)=zip.getInputStream(requireNotNull(zip.getEntry(name)))
                .bufferedReader().readText()
            check(read(outer+"source-sha.txt").trim()=="53a5162eb3659310b1878894a90ae58c244a6745")
            original=json.decodeFromString<GameState>(read(pre+"initial-state.json"))
            val records=read(pre+"actions.jsonl").lineSequence().filter(String::isNotBlank)
                .map{json.parseToJsonElement(it).jsonObject}.toList()
            for(idx in 1..41){
                val submitted=records[(idx-1)*2]
                val result=records[(idx-1)*2+1]
                check(submitted["record"]!!.jsonPrimitive.content=="submit")
                check(result["record"]!!.jsonPrimitive.content=="result")
                val a=json.decodeFromString<GameAction>(submitted["action"].toString())
                val before=processor.process(original,a).result
                check(before.error==null)
                check(before.events.map{it.toString()}==
                    result["events"]!!.jsonArray.map{it.jsonPrimitive.content})
                check(original.turnOrder.map{before.state.lifeTotal(it)}==
                    result["life"]!!.jsonArray.map{it.jsonPrimitive.int})
                original=before.state
            }
        }
        check(original.activePlayerId==actor && original.priorityPlayerId==actor &&
            original.stack.isEmpty() && original.pendingDecision==null)
        check(original.getHand(adversary).none{cardName(original,it)=="Pact of Negation"})
        val pact=original.getLibrary(adversary).single{
            cardName(original,it)=="Pact of Negation"
        }
        // The opponent did NOT draw this in the recorded game. We create an
        // explicitly synthetic response capability; no hidden-hand inference.
        var state=original.removeFromZone(ZoneKey(adversary,Zone.LIBRARY),pact)
            .addToZone(ZoneKey(adversary,Zone.HAND),pact)
        fun submit(action:GameAction){
            val result=processor.process(state,action).result
            check(result.error==null){"Conditional R10 response action rejected: "+result.error}
            check(!result.state.gameOver){"R10 microfork entered a terminal game"}
            state=result.state
        }
        val misty=state.getHand(actor).single{cardName(state,it)=="Misty Rainforest"}
        val bird=state.getHand(actor).single{cardName(state,it)=="Birds of Paradise"}
        check(simulator.getLegalActions(state,actor).any{
            it.affordable && (it.action as? PlayLand)?.cardId==misty
        })
        submit(PlayLand(actor,misty))
        val fetch=simulator.getLegalActions(state,actor).filter{
            it.affordable && !it.requiresTargets &&
            (it.action as? ActivateAbility)?.sourceId==misty
        }.single().action as ActivateAbility
        submit(fetch)
        var passes=0
        while(state.pendingDecision==null && state.stack.isNotEmpty()){
            check(++passes<=8)
            submit(PassPriority(requireNotNull(state.priorityPlayerId)))
        }
        val decision=state.pendingDecision as? SelectCardsDecision
            ?:error("Actual fetch failed to offer optional searchable Forest")
        val forest=decision.options.filter{cardName(state,it)=="Forest"}
            .minByOrNull{it.value}?:error("No genuinely legal Forest")
        check(decision.minSelections<=1 && decision.maxSelections>=1)
        submit(SubmitDecision(actor,CardsSelectedResponse(decision.id,listOf(forest))))
        check(state.stack.isEmpty() && state.pendingDecision==null &&
            forest in state.getZone(ZoneKey(actor,Zone.BATTLEFIELD)) &&
            state.getEntity(forest)?.has<TappedComponent>()!=true)
        val creature=simulator.getLegalActions(state,actor).single{
            it.affordable && (it.action as? CastSpell)?.cardId==bird
        }.action as CastSpell
        submit(creature)
        check(state.stack.isNotEmpty() && bird in state.stack)
        // Conditional fork next: the real active pilot must pass priority
        // before opponent's counter opportunity. We DO NOT suppose the
        // opponent actually had or would cast Pact in the original game.
        var responsePasses=0
        while(state.priorityPlayerId!=adversary && state.pendingDecision==null &&
              bird in state.stack) {
            check(++responsePasses<=4)
            submit(PassPriority(requireNotNull(state.priorityPlayerId)))
        }
        check(state.priorityPlayerId==adversary && bird in state.stack) {
            "No actual opponent response window on the creature stack spell"
        }
        val counters=simulator.getLegalActions(state,adversary).filter{
            it.affordable && (it.action as? CastSpell)?.cardId==pact
        }
        check(counters.isNotEmpty()){
            "No free Pact spell available after synthetic own-library-to-hand insertion"
        }
        val counter=(counters.first().action as CastSpell).copy(
            targets=listOf(ChosenTarget.Spell(bird))
        )
        val reply=processor.process(state,counter).result
        check(reply.error==null){"Targeted actual Pact counterspell was illegally submitted: "+reply.error}
        check(reply.events.any{
            it is SpellCastEvent && it.cardName=="Pact of Negation" &&
                it.casterId==adversary
        }){"No genuine Pact cast event in conditional opponent response"}
        // Stop immediately after legal Pact cast. No automatic opponent
        // passes to imply that it resolved or that the player won a game.
        val result=buildJsonObject{
            put("id","R10-SYNTHETIC-PACT-RESPONSE")
            put("originalGameActionsReplayed",41)
            put("hypotheticalOpponentInjectedCard","Pact of Negation")
            put("hypotheticalOpponentActualSourceZone","LIBRARY")
            put("hypotheticalOpponentTarget","Birds of Paradise on stack")
            put("counterspellSourcePaymentAccepted",true)
            put("actualOppositionBehaviorObserved",false)
            put("opponentResponseWindowObserved",true)
            put("possibleOppCounterCastMechanism",true)
            put("opponentsGuaranteedToAllowBirds",false)
            put("manaDorkActuallyResolved",false)
            put("newFullGames",0)
            put("qualifiedCompetitiveEdge",false)
        }
        Files.writeString(output.resolve("R10-SYNTHETIC-PACT-RESPONSE.json"),
            result.toString()+"\n")
        println("CRUISE_R10_ADVERSARIAL_RESPONSE="+result)
    }
})
