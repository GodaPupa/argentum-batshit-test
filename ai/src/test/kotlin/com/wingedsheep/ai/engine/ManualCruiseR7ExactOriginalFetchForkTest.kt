package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Two exact historical ORIGINAL full-game prefixes, then one tightly bounded
 * hypothetical fetch branch. The fork passes opponents only to witness *one*
 * fetch resolution. This is NOT a replay of what opponents would do, NOT a
 * competitive gameplay simulation, NOT a game win and NOT source/profile edit.
 * Zero new full games, zero redraws, no GameInitializer.
 */
class ManualCruiseR7ExactOriginalFetchForkTest : FunSpec({
    val root=Path.of(requireNotNull(System.getenv("MANUAL_R7_ROOT")))
    val archiveRoot=Path.of(requireNotNull(System.getenv("MANUAL_R7_ARCHIVES")))
    val output=Path.of(requireNotNull(System.getenv("MANUAL_R7_OUTPUT")))
    Files.createDirectories(output)
    val manifest=Json.parseToJsonElement(Files.readString(root.resolve(
        "experiments/manual-transmission/research/cruise-sport-race-20261021/CRUISE_R7_MICROFORK_MANIFEST.json"
    ))).jsonObject
    fun JsonObject.string(name:String) = requireNotNull(this[name]).jsonPrimitive.content
    fun JsonObject.int(name:String) = requireNotNull(this[name]).jsonPrimitive.int
    check(manifest.string("schema")=="ARGENTUM_CRUISE_R7_ORIGINAL_TURN_ONE_FETCH_MICROFORK_V1")
    check(manifest["predeclared_before_new_microfork_results"]!!.jsonPrimitive.boolean)
    check(manifest["budget"]!!.jsonObject.int("new_full_games")==0)
    check(manifest["budget"]!!.jsonObject.int("reserved")==2)
    check(manifest.int("historical_original_land_choice_action_index")==42)
    val json=Json { serializersModule=engineSerializersModule; allowStructuredMapKeys=true }
    val registry=CardRegistry().apply {
        MtgSetCatalog.all.forEach {set->register(set.cards);register(set.basicLands)}
    }
    val processor=ActionProcessor(registry)
    val simulator=GameSimulator(registry)
    val seat=EntityId("manual-mirror-1")
    fun sha(path:Path)=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
        .joinToString(""){"%02x".format(it)}
    fun name(state:GameState,id:EntityId)=
        requireNotNull(state.getEntity(id)?.get<CardComponent>()).name

    for(item in manifest["original_arms"]!!.jsonArray) {
        val arm=item.jsonObject
        val armId=arm.string("arm")
        test("R7: full original prior 41 events then bounded alternate ${armId} land/fetch/Birds legal-action fork") {
            val file=archiveRoot.resolve("R6-$armId-202610202141-original.zip")
            check(Files.isRegularFile(file) && sha(file)==arm.string("digest"))
            ZipFile(file.toFile()).use { zip ->
                val prefix="precision-r6-prospective-output/"
                val base=prefix+armId+"-seed-202610202141/"
                fun read(path:String)=zip.getInputStream(
                    requireNotNull(zip.getEntry(path)) { "Original member absent $path" }
                ).bufferedReader().readText()
                check(read(prefix+"source-sha.txt").trim()==arm.string("source_sha"))
                val gameSummary=Json.parseToJsonElement(read(base+"summary.json")).jsonObject
                check(gameSummary.string("status")=="COMPLETE")
                check(gameSummary.string("profile")=="manual-transmission-r5-survival-opt-in")
                check(gameSummary["prefixOnly"]!!.jsonPrimitive.boolean==false)
                var state=json.decodeFromString<GameState>(read(base+"initial-state.json"))
                val stream=read(base+"actions.jsonl").lineSequence().filter(String::isNotBlank)
                    .map{Json.parseToJsonElement(it).jsonObject}.toList()
                val submitted=stream.filter{it.string("record")=="submit"}
                val historical=stream.filter{it.string("record")=="result"}
                check(submitted.size==historical.size && submitted.size==gameSummary.int("accepted"))
                for(index in 1..41){
                    val original=submitted[index-1]
                    val expected=historical[index-1]
                    check(original.int("index")==index && expected.int("index")==index)
                    val action=json.decodeFromString<GameAction>(original.string("action"))
                    val processed=processor.process(state,action).result
                    check(processed.error==null){"Historical prefix action $index rejected: ${processed.error}"}
                    val actualEvents=processed.events.map{it.toString()}
                    val expectedEvents=expected["events"]!!.jsonArray.map{it.jsonPrimitive.content}
                    check(actualEvents==expectedEvents){"Original R6 prefix events drift at action $index; HOLD"}
                    val actualLife=state.turnOrder.map{processed.state.lifeTotal(it)}
                    val expectedLife=expected["life"]!!.jsonArray.map{it.jsonPrimitive.int}
                    check(actualLife==expectedLife){"Original R6 prefix life drift $index; HOLD"}
                    state=processed.state
                }
                val index42=submitted[41]
                val originalAction=json.decodeFromString<GameAction>(index42.string("action"))
                check(index42.int("index")==42 && index42.string("actor")==seat.value)
                check(originalAction is PlayLand && name(state,originalAction.cardId)=="Opal Palace")
                check(state.activePlayerId==seat && state.priorityPlayerId==seat)
                check(state.phase.isMainPhase && state.pendingDecision==null)
                check(state.stack.isEmpty())
                val beforeTurn=state.turnNumber
                val lifeBefore=state.lifeTotal(seat)
                val sourceLandId=state.getHand(seat).single{
                    name(state,it)==arm.string("alternative_first_land")
                }
                val forest=state.getLibrary(seat)
                    .filter{name(state,it)=="Forest"}
                    .sortedBy{it.value}.firstOrNull()
                    ?:error("No historical searchable Forest in the original seat library")
                val birds=state.getHand(seat).single{name(state,it)=="Birds of Paradise"}
                val realLandOffer=simulator.getLegalActions(state,seat).single{
                    it.affordable && (it.action as? PlayLand)?.cardId==sourceLandId
                }
                val play=(realLandOffer.action as PlayLand)
                var resolved=processor.process(state,play).result
                check(resolved.error==null){"Alternative source land not actually legal"}
                var fork=resolved.state
                check(sourceLandId in fork.getZone(ZoneKey(seat,Zone.BATTLEFIELD)))
                val activationOptions=simulator.getLegalActions(fork,seat).filter{
                    it.affordable && !it.requiresTargets &&
                      (it.action as? ActivateAbility)?.sourceId==sourceLandId
                }
                check(activationOptions.size==1){
                    "Expected exactly one affordable legal original fetch activation, got ${activationOptions.size}"
                }
                val activation=activationOptions.single().action as ActivateAbility
                resolved=processor.process(fork,activation).result
                check(resolved.error==null){"Actual fetch activation rejected: ${resolved.error}"}
                fork=resolved.state
                val lifeAfterActivation=fork.lifeTotal(seat)
                check(lifeAfterActivation==lifeBefore-
                    (if(armId=="a") 1 else 0)){"Fetch activation actual life payment mismatch"}
                check(sourceLandId in fork.getZone(ZoneKey(seat,Zone.GRAVEYARD))){
                    "Fetch activation failed to sacrifice the source land"
                }
                val hypotheticalPasses=mutableListOf<String>()
                while(fork.pendingDecision==null && fork.stack.isNotEmpty()){
                    check(hypotheticalPasses.size<8){"Fetch has unresolved stack with excessive priority passes"}
                    val actor=requireNotNull(fork.priorityPlayerId)
                    val passed=processor.process(fork,PassPriority(actor)).result
                    check(passed.error==null){"Legality-only priority pass was rejected"}
                    hypotheticalPasses+=actor.value
                    fork=passed.state
                }
                val search=fork.pendingDecision as? SelectCardsDecision
                    ?:error("Original fetch effect failed to request a select-cards search")
                check(search.playerId==seat && forest in search.options){
                    "Original archived Forest not among actual fetch's legal targets"
                }
                val selection=processor.process(fork,
                    SubmitDecision(seat,CardsSelectedResponse(search.id,listOf(forest)))).result
                check(selection.error==null){"Actual original-library Forest choice rejected"}
                fork=selection.state
                check(fork.pendingDecision==null && fork.stack.isEmpty()) {
                    "Unresolved decision/stack after original-library basic Forest fetch"
                }
                check(forest in fork.getZone(ZoneKey(seat,Zone.BATTLEFIELD))) {
                    "Actual engine did not place selected Forest on battlefield"
                }
                val tapped=fork.getEntity(forest)?.has<TappedComponent>()==true
                val expectedTapped=arm["expected_forest_enters_tapped"]!!.jsonPrimitive.boolean
                check(tapped==expectedTapped){"Original fetch tapped-entry requirement drift"}
                check(fork.activePlayerId==seat && fork.turnNumber==beforeTurn) {
                    "Legality microfork advanced game turn; HOLD"
                }
                check(fork.priorityPlayerId==seat){
                    "Resulting priority not with original active player"
                }
                val birdsOptions=simulator.getLegalActions(fork,seat).filter{
                    it.affordable && (it.action as? CastSpell)?.cardId==birds
                }
                val shouldHaveBirds=arm["expected_same_turn_birds_affordable"]!!.jsonPrimitive.boolean
                check(birdsOptions.isNotEmpty()==shouldHaveBirds){
                    "Actual Birds affordability disagreed with preregistered fetch comparison"
                }
                var birdsAccepted=false
                if(shouldHaveBirds){
                    val selected=birdsOptions.single().action as CastSpell
                    val cast=processor.process(fork,selected).result
                    check(cast.error==null){"Green-funded Birds cast rejected: ${cast.error}"}
                    check(cast.events.any{
                        it.toString().contains(
                            "SpellCastEvent(") && it.toString().contains("cardName=Birds of Paradise")
                    }){"No actual Birds of Paradise spell cast event"}
                    birdsAccepted=true
                    // STOP on spell cast to stack: no opponent responses or game outcome.
                }
                val out=buildJsonObject{
                    put("schema","ARGENTUM_CRUISE_R7_REAL_ENGINE_FETCH_MICROFORK_RESULT_V1")
                    put("cohort","R6")
                    put("seed","202610202141")
                    put("arm",armId)
                    put("seat",seat.value)
                    put("historicalOriginalGameSourceSHA",arm.string("source_sha"))
                    put("originalArchiveSHA256",arm.string("digest"))
                    put("historicalFullGameActionsReplayed",41)
                    put("historicalChosenActionAt42","Play Opal Palace")
                    put("hypotheticalAlternateLand",arm.string("alternative_first_land"))
                    put("hypotheticalOpponentPriorityPasses",JsonArray(hypotheticalPasses.map(::JsonPrimitive)))
                    put("hypotheticalOpponentBehaviorAssumption","ALL_PASS_FOR_BOUNDED_MECHANISM_ONLY")
                    put("independentlyRealActionProcessorAccepted",true)
                    put("realOriginalLibraryForestTargetOffered",true)
                    put("forestActuallyFetchedTapped",tapped)
                    put("birdsImmediatelyAffordable",birdsOptions.isNotEmpty())
                    put("birdsCastActuallyProcessorAccepted",birdsAccepted)
                    put("noAdditionalGameTurn",true)
                    put("newFullGames",0)
                    put("opponentResponseOutcomeObserved",false)
                    put("winRateBenefitOrStrategicSuperiorityProven",false)
                    put("productionChanges",false)
                    put("conclusion","PAIRED_FETCH_MANA_MECHANISM_ONLY_NO_COMPETITIVE_CLAIM")
                }
                Files.writeString(output.resolve("R7-$armId.json"),out.toString()+"\n")
                println("CRUISE_R7_ACTUAL_ENGINE_FORK="+out)
            }
        }
    }
})
