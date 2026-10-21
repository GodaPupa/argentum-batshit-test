package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
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
 * Read-only exact ORIGINAL R6 code (source 53a5162e...) replay of a previously
 * completed full-game trace. Stop *before* archived action 3017; do NOT
 * continue gameplay, issue new seeds, initialize a new game, or test policy.
 *
 * This exporter is required because replaying the R6 archived stream under
 * newer R9 rules engine diverged at original action 716. We preserve that
 * discrepancy rather than falsely marking the newer-source replay GREEN.
 * The state emitted here comes from original R6 source and exact original
 * event/life reproduction only.
 */
class ManualCruiseR10OriginalR6CheckpointExportTest : FunSpec({
    val input=Path.of(requireNotNull(System.getenv("MANUAL_R10_OLD_ARCHIVE")))
    val output=Path.of(requireNotNull(System.getenv("MANUAL_R10_OLD_OUTPUT")))
    Files.createDirectories(output)
    val expectedSha="1bb49d6bd7179ec4561cb2c635dca5a08653163d361c5509e81791521b46714d"
    val digest=MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(input)).joinToString(""){"%02x".format(it)}
    check(digest==expectedSha)
    val json=Json{
        serializersModule=engineSerializersModule
        allowStructuredMapKeys=true
    }
    val registry=CardRegistry().apply{
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }
    val engine=ActionProcessor(registry)
    val originalSource="53a5162eb3659310b1878894a90ae58c244a6745"
    val target=3017
    val seat=EntityId("manual-mirror-0")

    test("exact original source R6 3016-event replay, export source-bound 3017 pre-action state") {
        ZipFile(input.toFile()).use{zip ->
            val outer="precision-r6-prospective-output/"
            val pre=outer+"a-seed-202610202143/"
            fun read(s:String)=zip.getInputStream(
                requireNotNull(zip.getEntry(s)){"Archived original member missing "+s}
            ).bufferedReader().readText()
            check(read(outer+"source-sha.txt").trim()==originalSource)
            val metadata=Json.parseToJsonElement(read(pre+"initialization.json")).jsonObject
            val summary=Json.parseToJsonElement(read(pre+"summary.json")).jsonObject
            check(metadata["openingPolicy"]!!.jsonPrimitive.content==
                "Four seats keep seed-derived initial seven, no smoother or mulligans")
            check(summary["status"]!!.jsonPrimitive.content=="COMPLETE")
            check(summary["profile"]!!.jsonPrimitive.content==
                "manual-transmission-r5-survival-opt-in")
            check(summary["accepted"]!!.jsonPrimitive.int==
                summary["submitted"]!!.jsonPrimitive.int)
            var state=json.decodeFromString<GameState>(read(pre+"initial-state.json"))
            val records=read(pre+"actions.jsonl").lineSequence()
                .filter(String::isNotBlank)
                .map{Json.parseToJsonElement(it).jsonObject}.toList()
            check(records.size==summary["accepted"]!!.jsonPrimitive.int*2)
            for(index in 1 until target){
                val original=records[(index-1)*2]
                val result=records[(index-1)*2+1]
                check(original["record"]!!.jsonPrimitive.content=="submit")
                check(result["record"]!!.jsonPrimitive.content=="result")
                check(original["index"]!!.jsonPrimitive.int==index &&
                    result["index"]!!.jsonPrimitive.int==index)
                val action=json.decodeFromString<GameAction>(
                    original["action"]!!.jsonPrimitive.content)
                val observed=engine.process(state,action).result
                check(observed.error==null){"Historical R6 action refused at "+index+": "+observed.error}
                check(observed.events.map{it.toString()}==
                    result["events"]!!.jsonArray.map{it.jsonPrimitive.content}){
                    "Original R6 source event mismatch at "+index
                }
                check(state.turnOrder.map{observed.state.lifeTotal(it)}==
                    result["life"]!!.jsonArray.map{it.jsonPrimitive.int}){
                    "Original R6 source life mismatch at "+index
                }
                state=observed.state
            }
            val next=records[(target-1)*2]
            val action=json.decodeFromString<GameAction>(next["action"]!!.jsonPrimitive.content)
            check(next["index"]!!.jsonPrimitive.int==target &&
                next["actor"]!!.jsonPrimitive.content==seat.value)
            check(action is CastSpell && state.getEntity(action.cardId)?.get<CardComponent>()?.name=="Paradise Druid")
            check(state.priorityPlayerId==seat && state.activePlayerId==seat)
            val lands=state.controlledBattlefield(seat).count{
                state.projectedState.hasType(it,"LAND")
            }
            check(lands>=2)
            val serialized=json.encodeToString(state)
            Files.writeString(output.resolve("r10-original-r6-a-2143-before-3017.json"),serialized)
            val checksum=MessageDigest.getInstance("SHA-256")
                .digest(serialized.toByteArray()).joinToString(""){"%02x".format(it)}
            val witness=buildJsonObject{
                put("schema","ARGENTUM_R10_ORIGINAL_R6_SOURCE_PROVEN_CHECKPOINT_V1")
                put("originalR6CommitSha",originalSource)
                put("originalZipSha256",digest)
                put("seed","202610202143")
                put("arm","a")
                put("seat",seat.value)
                put("originalActionBefore",target)
                put("exactOriginalAcceptedActionsReplayed",target-1)
                put("fullEventAndLifeComparisons",target-1)
                put("historicalOriginalChosen","Cast Paradise Druid")
                put("originalPersonalBattlefieldLandCount",lands)
                put("snapshotSha256",checksum)
                put("newFullGames",0)
                put("sourcePortToR9Complete",false)
                put("disposition","ORIGINAL_SOURCE_EXACT_NOT_NEW_SOURCE_REPLAY")
            }
            Files.writeString(output.resolve("r10-original-r6-a-2143-provenance.json"),
                witness.toString()+"\n")
            println("CRUISE_R10_OLD_SOURCE_CHECKPOINT="+witness)
        }
    }
})
