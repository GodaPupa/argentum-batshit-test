package com.wingedsheep.ai.engine

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
 * Diagnostic only: single existing AiProfile flag, separate opt-in and original
 * archived 41-action state. No new gameplay, edits, future draws, opponent
 * response, implied candidate promotion or deck policy change.
 */
class ManualCruiseR8ExistingLandSequenceFlagTest : FunSpec({
    val root=Path.of(requireNotNull(System.getenv("MANUAL_R8_ROOT")))
    val archives=Path.of(requireNotNull(System.getenv("MANUAL_R8_ARCHIVES")))
    val output=Path.of(requireNotNull(System.getenv("MANUAL_R8_OUTPUT")))
    Files.createDirectories(output)
    val manifest=Json.parseToJsonElement(Files.readString(root.resolve(
      "experiments/manual-transmission/research/cruise-sport-race-20261021/CRUISE_R8_LANDSEQ_FLAG_MANIFEST.json"
    ))).jsonObject
    fun JsonObject.str(n:String)=requireNotNull(this[n]).jsonPrimitive.content
    fun JsonObject.num(n:String)=requireNotNull(this[n]).jsonPrimitive.int
    check(manifest.str("schema")=="ARGENTUM_CRUISE_R8_SINGLE_EXISTING_PROFILE_FLAG_TEST_V1")
    check(manifest["budget"]!!.jsonObject.num("new_games")==0)
    check(manifest["budget"]!!.jsonObject.num("reserved")==2)
    check(manifest.num("original_priority_action_index")==42)
    check(manifest["candidate_profile_only_change"]!!.jsonObject
      ["sequenceLandsByUsableMana"]!!.jsonPrimitive.boolean)
    val json=Json { serializersModule=engineSerializersModule; allowStructuredMapKeys=true }
    val registry=CardRegistry().apply {
        MtgSetCatalog.all.forEach {set->register(set.cards);register(set.basicLands)}
    }
    val processor=ActionProcessor(registry)
    val seat=EntityId("manual-mirror-1")
    val R5=ManualTransmissionR5Candidate.profile
    check(!R5.sequenceLandsByUsableMana)
    val candidate=R5.copy(
        id="manual-transmission-cruise-r8-landseq-flag-opt-in",
        sequenceLandsByUsableMana=true
    )
    check(candidate.advisorModules==R5.advisorModules)
    check(candidate.respectRequiredAttackDefenders==R5.respectRequiredAttackDefenders)
    check(AiProfile.CURRENT.advisorModules.isEmpty())
    fun cardName(state:GameState,id:EntityId)=state.getEntity(id)?.get<CardComponent>()?.name
    fun sha(file:Path)=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))
        .joinToString(""){"%02x".format(it)}
    for (item in manifest["fixtures"]!!.jsonArray) {
        val fixture=item.jsonObject
        val arm=fixture.str("arm")
        test("R8 preserved 41-action R5 original versus one existing land sequencing flag: $arm") {
            val file=archives.resolve("R6-$arm-202610202141-original.zip")
            check(Files.isRegularFile(file) && sha(file)==fixture.str("digest"))
            ZipFile(file.toFile()).use { zip ->
                val outer="precision-r6-prospective-output/"
                val pre=outer+arm+"-seed-202610202141/"
                fun read(p:String)=zip.getInputStream(
                    requireNotNull(zip.getEntry(p)){"Original archive entry missing $p"}
                ).bufferedReader().readText()
                check(read(outer+"source-sha.txt").trim()==fixture.str("source_sha"))
                val summary=Json.parseToJsonElement(read(pre+"summary.json")).jsonObject
                check(summary.str("status")=="COMPLETE" &&
                    summary.str("profile")=="manual-transmission-r5-survival-opt-in")
                check(summary["prefixOnly"]!!.jsonPrimitive.boolean==false)
                var state=json.decodeFromString<GameState>(read(pre+"initial-state.json"))
                val stream=read(pre+"actions.jsonl").lineSequence().filter(String::isNotBlank)
                    .map{Json.parseToJsonElement(it).jsonObject}.toList()
                check(stream.size==2*summary.num("accepted"))
                for (index in 1..41) {
                    val submit=stream[(index-1)*2]
                    val response=stream[(index-1)*2+1]
                    check(submit.str("record")=="submit" && response.str("record")=="result")
                    check(submit.num("index")==index && response.num("index")==index)
                    val action=json.decodeFromString<GameAction>(submit.str("action"))
                    val result=processor.process(state,action).result
                    check(result.error==null){"Original prefix $index not executable"}
                    check(result.events.map{it.toString()}==
                        response["events"]!!.jsonArray.map{it.jsonPrimitive.content}) {
                        "R8 original source events drift at $index"
                    }
                    check(state.turnOrder.map{result.state.lifeTotal(it)}==
                        response["life"]!!.jsonArray.map{it.jsonPrimitive.int}){
                        "R8 original source life drift at $index"
                    }
                    state=result.state
                }
                val targetOriginal=stream[82]
                val originalChosen=json.decodeFromString<GameAction>(targetOriginal.str("action"))
                check(targetOriginal.num("index")==42 && targetOriginal.str("actor")==seat.value)
                check(originalChosen is PlayLand &&
                    cardName(state,originalChosen.cardId)=="Opal Palace")
                check(state.activePlayerId==seat && state.priorityPlayerId==seat)
                val baseline=AIPlayer.create(registry,seat,R5).chooseAction(state)
                check(baseline==originalChosen) {
                    "Original R5 decision differs at frozen saved historical state; HOLD"
                }
                val alternative=AIPlayer.create(registry,seat,candidate).chooseAction(state)
                val actual=processor.process(state,alternative).result
                check(actual.error==null){"Existing-flag candidate made a rejected action: ${actual.error}"}
                val baselineName=when(baseline) {
                    is PlayLand->cardName(state,baseline.cardId)
                    is CastSpell->cardName(state,baseline.cardId)
                    else->null
                }
                val candidateName=when(alternative) {
                    is PlayLand->cardName(state,alternative.cardId)
                    is CastSpell->cardName(state,alternative.cardId)
                    else->null
                }
                val goal=if(arm=="a")"Misty Rainforest" else "Opal Palace"
                val out=buildJsonObject{
                    put("schema","ARGENTUM_CRUISE_R8_EXISTING_LANDSEQ_FLAG_SOURCE_DECISION_V1")
                    put("arm",arm)
                    put("seed","202610202141")
                    put("seat",seat.value)
                    put("historicalExactActionsReplayed",41)
                    put("originalSourceSHA",fixture.str("source_sha"))
                    put("originalZipSHA256",fixture.str("digest"))
                    put("baselineR5ProfileId",R5.id)
                    put("candidateOptInProfileId",candidate.id)
                    put("candidateOneFlagOnly","sequenceLandsByUsableMana=true")
                    put("originalR5ActualChoice",baselineName)
                    put("candidateActionType",alternative::class.simpleName ?: "unknown")
                    put("candidateActualChoice",candidateName)
                    put("candidateActionProcessorAccepted",true)
                    put("meetsPredeclaredSingleStateGoal",candidateName==goal)
                    put("productionProfileChanged",false)
                    put("newFullGames",0)
                    put("opponentResponsesUnderChangedActionObserved",false)
                    put("improvedWinRateProven",false)
                }
                Files.writeString(output.resolve("R8-$arm.json"),out.toString()+"\n")
                println("CRUISE_R8_ORIGINAL_SOURCE_FLAG="+out)
            }
        }
    }
})
