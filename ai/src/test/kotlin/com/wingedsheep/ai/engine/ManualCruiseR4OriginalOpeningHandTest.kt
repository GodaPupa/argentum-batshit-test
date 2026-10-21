package com.wingedsheep.ai.engine

import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.engine.core.engineSerializersModule
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
 * Exactly 40 source-pinned ORIGINAL opening hands, evaluated through real
 * EngineAiPlayerController.decideMulligan. The historical matched R5 harness
 * skipped mulligans, so all outputs are separately labeled hypotheticals.
 * No new game, GameInitializer, redraw, London bottoming, or action execution.
 */
class ManualCruiseR4OriginalOpeningHandTest : FunSpec({
    val root = Path.of(requireNotNull(System.getenv("MANUAL_R4_OPENERS_ROOT")))
    val archives = Path.of(requireNotNull(System.getenv("MANUAL_R4_OPENERS_ARCHIVES")))
    val output = Path.of(requireNotNull(System.getenv("MANUAL_R4_OPENERS_OUTPUT")))
    Files.createDirectories(output)
    val base = root.resolve("experiments/manual-transmission/research/cruise-sport-race-20261021")
    val manifest = Json.parseToJsonElement(Files.readString(base.resolve(
        "CRUISE_R4_OPENERS_MANIFEST.json"))).jsonObject
    val originalManifest = Json.parseToJsonElement(Files.readString(base.resolve(
        "CRUISE_R2_ZONE_MANIFEST.json"))).jsonObject
    check(manifest["schema"]!!.jsonPrimitive.content ==
        "MANUAL_CRUISE_R4_FULL_ORIGINAL_OPENING_HAND_UNIVERSE_V1")
    check(manifest["declared_before_controller_observations"]!!.jsonPrimitive.boolean)
    check(manifest["opening_hand_count"]!!.jsonPrimitive.int == 40)
    check(manifest["original_game_count"]!!.jsonPrimitive.int == 10)
    check(manifest["precision_budget"]!!.jsonObject["additional"]!!.jsonPrimitive.int == 0)
    check(originalManifest["archives"]!!.jsonArray.size == 10)
    fun JsonObject.str(key: String) = requireNotNull(this[key]).jsonPrimitive.content
    fun ZipFile.entryText(path: String) = getInputStream(
        requireNotNull(getEntry(path)) { "Original ZIP member missing: $path" }
    ).bufferedReader().readText()
    fun sha(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    val json = Json {
        serializersModule = engineSerializersModule
        allowStructuredMapKeys = true
    }
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }
    val oneManaDorks = setOf(
        "Arbor Elf", "Llanowar Elves", "Elvish Mystic", "Fyndhorn Elves",
        "Birds of Paradise", "Delighted Halfling"
    )

    for (original in originalManifest["archives"]!!.jsonArray) {
        val spec = original.jsonObject
        val cohort = spec.str("cohort")
        val seed = spec.str("seed")
        val arm = spec.str("arm")
        test("R4 all original four keep-seven hands $cohort-$arm-$seed (NO GAMES)") {
            val archive = archives.resolve("$cohort-$arm-$seed-original.zip")
            check(Files.isRegularFile(archive))
            check(sha(archive) == spec.str("sha256")) {
                "Original R1/R6 ZIP digest mismatch"
            }
            val outer = if (cohort == "R1") "precision-r1-stage1-output/" else
                "precision-r6-prospective-output/"
            val pre = outer + arm + "-seed-" + seed + "/"
            ZipFile(archive.toFile()).use { zip ->
                check(zip.entryText(outer + "source-sha.txt").trim() == spec.str("source_sha"))
                val init = Json.parseToJsonElement(zip.entryText(
                    pre + "initialization.json")).jsonObject
                val summary = Json.parseToJsonElement(zip.entryText(
                    pre + "summary.json")).jsonObject
                check(init.str("openingPolicy") ==
                    "Four seats keep seed-derived initial seven, no smoother or mulligans")
                check(summary.str("profile") == "manual-transmission-r5-survival-opt-in")
                check(summary.str("status") == "COMPLETE")
                check(summary["prefixOnly"]!!.jsonPrimitive.boolean == false)
                val state = json.decodeFromString<GameState>(
                    zip.entryText(pre + "initial-state.json"))
                check(state.turnOrder.size == 4)
                val snapshot = json.encodeToString(state)
                val records = mutableListOf<JsonElement>()
                for (seat in state.turnOrder) {
                    val ids = state.getHand(seat)
                    check(ids.size == 7) { "Non-original-seven: $seat" }
                    val cardInfo = ids.associateWith { cid ->
                        val card = requireNotNull(state.getEntity(cid)?.get<CardComponent>())
                        CardSummary(
                            name = card.name,
                            manaCost = card.manaCost.toString(),
                            typeLine = card.typeLine.toString(),
                            oracleText = card.oracleText
                        )
                    }
                    val info = MulliganInfo(
                        hand = ids,
                        mulliganCount = 0,
                        cardsToPutOnBottom = 0,
                        cards = cardInfo,
                        isOnThePlay = seat == state.turnOrder.first()
                    )
                    val controller = EngineAiPlayerController(
                        registry, seat, gameStateProvider = { state }
                    )
                    val keep = controller.decideMulligan(info)
                    val printedLands = ids.count { cid ->
                        cardInfo[cid]?.typeLine?.contains("Land", ignoreCase = true) == true
                    }
                    val ramp = ids.mapNotNull(cardInfo::get).map { it.name }
                        .filter { it in oneManaDorks }
                    records += buildJsonObject {
                        put("cohort", cohort)
                        put("seed", seed)
                        put("arm", arm)
                        put("seat", seat.value)
                        put("originalHistoricalKept", true)
                        put("originalHistoricalControllerInvoked", false)
                        put("realBotWouldKeepHypothetical", keep)
                        put("realBotWouldMulliganHypothetical", !keep)
                        put("isOnThePlay", info.isOnThePlay)
                        put("printedLandCount", printedLands)
                        put("openingCards", JsonArray(ids.map { cid ->
                            JsonPrimitive(cardInfo.getValue(cid).name)
                        }))
                        put("oneManaDorks", JsonArray(ramp.map(::JsonPrimitive)))
                        put("controllerMulliganCount", 0)
                        put("cardsToPutOnBottom", 0)
                        put("anyReplacementHandDrawn", false)
                        put("simulatedFullGame", false)
                    }
                }
                check(records.size == 4)
                check(json.encodeToString(state) == snapshot) {
                    "Read-only real controller altered source opening GameState"
                }
                val multi = JsonArray(records)
                Files.writeString(output.resolve("r4-$cohort-$arm-$seed.json"),
                    multi.toString() + "\n")
                val keepCount = records.count {
                    it.jsonObject["realBotWouldKeepHypothetical"]!!.jsonPrimitive.boolean
                }
                val oneLandCount = records.count {
                    it.jsonObject["printedLandCount"]!!.jsonPrimitive.int == 1
                }
                println("CRUISE_R4_ORIGINAL_OPENERS cohort=$cohort seed=$seed arm=$arm " +
                    "realHistoricalKeeps=4 hypotheticalBotKeeps=$keepCount " +
                    "hypotheticalBotMulligans=${4-keepCount} onePrintedLand=$oneLandCount NO_NEW_GAMES")
            }
        }
    }
})
