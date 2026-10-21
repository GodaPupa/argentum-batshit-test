package com.wingedsheep.ai.engine

import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.engine.core.engineSerializersModule
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
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
 * One frozen original two-land pair from CRUISE R4/R5. This proves controller
 * source-identity sensitivity only; synthetic swaps are NOT legal fetch play,
 * not opponent response, not a new game, not an improved win-rate.
 * Original opponent pass/fetch witness is verified from finished gameplay.
 */
class ManualCruiseR6FetchOpeningMechanismTest : FunSpec({
    val root=Path.of(requireNotNull(System.getenv("MANUAL_R6_FETCH_ROOT")))
    val archives=Path.of(requireNotNull(System.getenv("MANUAL_R6_FETCH_ARCHIVES")))
    val output=Path.of(requireNotNull(System.getenv("MANUAL_R6_FETCH_OUTPUT")))
    Files.createDirectories(output)
    val manifestPath=root.resolve(
        "experiments/manual-transmission/research/cruise-sport-race-20261021/CRUISE_R6_FETCH_MECHANISM_MANIFEST.json")
    val m=Json.parseToJsonElement(Files.readString(manifestPath)).jsonObject
    check(m["schema"]!!.jsonPrimitive.content=="ARGENTUM_CRUISE_R6_PAIRWISE_FETCH_MULLIGAN_MECHANISM_V1")
    check(m["budget"]!!.jsonObject["new_games"]!!.jsonPrimitive.int==0)
    check(m["budget"]!!.jsonObject["reserved"]!!.jsonPrimitive.int==2)
    check(m["fixtures"]!!.jsonArray.map { it.jsonObject["id"]!!.jsonPrimitive.content } ==
      listOf("R6-CONTROL","R6-CHALLENGER-A","R6-FOREST-SWAP","R6-BREEDING-SWAP"))

    fun JsonObject.str(name:String)=requireNotNull(this[name]).jsonPrimitive.content
    fun sha(file:Path)=MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))
        .joinToString("") { "%02x".format(it) }
    val json=Json { serializersModule=engineSerializersModule; allowStructuredMapKeys=true }
    val registry=CardRegistry().apply {
        MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
    }
    val seat=EntityId("manual-mirror-1")
    val expectedCards=listOf(
        "Purphoros, God of the Forge", "Opal Palace","Birds of Paradise",
        "Garruk's Uprising","Utopia Sprawl", "Misty Rainforest","Up the Beanstalk"
    )
    val states=linkedMapOf<String,GameState>()
    val sourceDigest=linkedMapOf<String,String>()
    val archivedWitness=mutableListOf<JsonElement>()
    for (spec in m["original_reference_archives"]!!.jsonArray) {
        val item=spec.jsonObject
        val arm=item.str("arm")
        val file=archives.resolve("R6-$arm-202610202141-original.zip")
        check(Files.isRegularFile(file) && sha(file)==item.str("zip_sha256"))
        ZipFile(file.toFile()).use { zip ->
            val pre="precision-r6-prospective-output/"
            check(zip.getInputStream(requireNotNull(zip.getEntry(pre+"source-sha.txt")))
                .bufferedReader().readText().trim()==item.str("source_sha"))
            val path=pre+arm+"-seed-202610202141/"
            val source=zip.getInputStream(requireNotNull(zip.getEntry(path+"initial-state.json")))
                .bufferedReader().readText()
            states[arm]=json.decodeFromString<GameState>(source)
            sourceDigest[arm]=sha(file)
            val initial=states.getValue(arm)
            check(initial.getHand(seat).size==7)
            if (arm=="a") {
                val actionText=zip.getInputStream(requireNotNull(zip.getEntry(path+"actions.jsonl")))
                    .bufferedReader().readLines().filter(String::isNotBlank)
                    .map { Json.parseToJsonElement(it).jsonObject }
                val submits=actionText.filter { it.str("record")=="submit" }
                val results=actionText.filter { it.str("record")=="result" }
                for (idx in listOf(222,227)) {
                    val a=submits.single { it["index"]!!.jsonPrimitive.int==idx }
                    val b=results.single { it["index"]!!.jsonPrimitive.int==idx }
                    val executed=Json.parseToJsonElement(a.str("action")).jsonObject
                    check(a.str("actor")==seat.value && b["error"] is JsonNull)
                    if (idx==222) {
                        check(a.str("sourceName")=="Misty Rainforest" &&
                            executed.str("type")=="ActivateAbility")
                        check(b["events"]!!.jsonArray.any { it.jsonPrimitive.content.contains(
                            "LifeChangedEvent(playerId=manual-mirror-1, oldLife=40, newLife=39, reason=PAYMENT") })
                    } else {
                        check(executed.str("type")=="SubmitDecision")
                        val decision=executed["response"]!!.jsonObject
                        check(decision.str("type")=="CardsSelectedResponse" &&
                            decision["selectedCards"]!!.jsonArray.size==1)
                        check(b["events"]!!.jsonArray.any { e ->
                            e.jsonPrimitive.content.contains(
                                "ZoneChangeEvent(entityId=e184, entityName=Steam Vents, fromZone=LIBRARY, toZone=BATTLEFIELD")
                        })
                        check(b["events"]!!.jsonArray.any { e ->
                            e.jsonPrimitive.content.contains(
                                "LibrarySearchedEvent(playerId=manual-mirror-1, sourceName=Misty Rainforest")
                        })
                    }
                    archivedWitness+=buildJsonObject {
                        put("index",idx);put("historicAccepted",true);put("actor",seat.value)
                        put("originalActionType",executed.str("type"))
                        put("originalSource",a["sourceName"])
                    }
                }
            }
        }
    }
    check(states.keys==setOf("control","a"))
    fun name(state:GameState,id:EntityId)=
        requireNotNull(state.getEntity(id)?.get<CardComponent>()).name
    fun opening(state:GameState):List<String>=state.getHand(seat).map { name(state,it) }
    fun controllerKeep(state:GameState):Boolean {
        val cards=state.getHand(seat).associateWith { id ->
            val card=requireNotNull(state.getEntity(id)?.get<CardComponent>())
            CardSummary(name=card.name,manaCost=card.manaCost.toString(),
                typeLine=card.typeLine.toString(),oracleText=card.oracleText)
        }
        val request=MulliganInfo(hand=state.getHand(seat),mulliganCount=0,
            cardsToPutOnBottom=0,cards=cards,isOnThePlay=false)
        return EngineAiPlayerController(registry,seat,gameStateProvider={state})
            .decideMulligan(request)
    }
    test("R6: actual CONTROL and Challenger A two-land originals vs two synthetic paid-green land identities; no games") {
        val control=states.getValue("control")
        val a=states.getValue("a")
        val originalA=opening(a)
        check(originalA==expectedCards)
        check(opening(control)==expectedCards.map { if(it=="Misty Rainforest") "Evolving Wilds" else it })
        val originalController=controllerKeep(control)
        val aController=controllerKeep(a)
        check(!originalController && !aController) {
            "Original R4 both-arm mulligan source diagnostic drift"
        }
        fun syntheticReplace(replacement:String):Pair<GameState,EntityId> {
            val misty=a.getHand(seat).single { name(a,it)=="Misty Rainforest" }
            val available=a.getLibrary(seat)
                .filter { name(a,it)==replacement }
                .sortedBy { id ->
                    val c=requireNotNull(a.getEntity(id)?.get<CardComponent>())
                    c.cardDefinitionId.toString()+":"+id.value
                }
            check(available.isNotEmpty()) { "No real original source library $replacement" }
            val candidate=available.first()
            val state=a.removeFromZone(ZoneKey(seat,Zone.HAND),misty)
                .addToZone(ZoneKey(seat,Zone.LIBRARY),misty)
                .removeFromZone(ZoneKey(seat,Zone.LIBRARY),candidate)
                .addToZone(ZoneKey(seat,Zone.HAND),candidate)
            check(state.getHand(seat).size==7 && originalA==opening(a)) {
                "Original A was mutated (source must remain immutable)"
            }
            return state to candidate
        }
        val (forest,forestId)=syntheticReplace("Forest")
        val (breeding,breedingId)=syntheticReplace("Breeding Pool")
        val forestKeep=controllerKeep(forest)
        val breedingKeep=controllerKeep(breeding)
        check(forestKeep) { "Source controller refuses ordinary basic Forest + Birds access" }
        check(breedingKeep) { "Source controller refuses directly-held Breeding Pool + Birds access" }
        val forestComponent=requireNotNull(a.getEntity(forestId)?.get<CardComponent>())
        val shockComponent=requireNotNull(a.getEntity(breedingId)?.get<CardComponent>())
        check(forestComponent.typeLine.toString().contains("Forest"))
        check(shockComponent.typeLine.toString().contains("Forest") &&
            shockComponent.typeLine.toString().contains("Island"))
        check(a.getLibrary(seat).contains(breedingId))
        check(a.getHand(seat).any { name(a,it)=="Birds of Paradise" })
        val proof=buildJsonObject {
            put("schema","MANUAL_CRUISE_R6_FETCH_SOURCE_SENSITIVITY_V1")
            put("originalControlHand",JsonArray(opening(control).map(::JsonPrimitive)))
            put("originalAHand",JsonArray(originalA.map(::JsonPrimitive)))
            put("originalControlControllerKeep",originalController)
            put("originalAControllerKeep",aController)
            put("syntheticBasicForestControllerKeep",forestKeep)
            put("syntheticBreedingPoolControllerKeep",breedingKeep)
            put("realOriginalABreedingPoolAvailableInLibrary",true)
            put("syntheticForestCardDefinitionId",forestComponent.cardDefinitionId)
            put("syntheticBreedingPoolCardDefinitionId",shockComponent.cardDefinitionId)
            put("originalSourceArchiveSHA256Control",sourceDigest.getValue("control"))
            put("originalSourceArchiveSHA256A",sourceDigest.getValue("a"))
            put("historicalMistyActivationAndSearchAccepted",true)
            put("historicalMistyOriginalActions",JsonArray(archivedWitness))
            put("realAlternativeFetchCastLineProven",false)
            put("futureOpponentBehaviorProven",false)
            put("improvedWinRateProven",false)
            put("newFullGames",0)
            put("policyChanged",false)
            put("conclusion","SOURCE_PLANNER_FETCH_RESOLUTION_GAP_CANDIDATE_NEEDS_ACTUAL_ENGINE_FORK")
        }
        Files.writeString(output.resolve("R6_FETCH_MECHANISM.json"),proof.toString()+"\n")
        println("CRUISE_R6_FETCH_MECHANISM="+proof)
    }
})
