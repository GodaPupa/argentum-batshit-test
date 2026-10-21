package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.LandPlayContext
import com.wingedsheep.ai.engine.advisor.modules.ManualCruiseR9ExecutableFetchAdvisor
import com.wingedsheep.ai.engine.advisor.modules.ManualCruiseR9ExecutableFetchAdvisorModule
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
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
 * Eight predeclared R9 positive/negative controls on IMMUTABLE original source
 * and source-derived diagnostic states. Exact original 41-action prefix first,
 * then at most one decision and bounded engine-funded mechanism.
 * No GameInitializer, redraw, extra turn, full game, or opponent forecast.
 */
class ManualCruiseR9ExecutableFetchCandidateTest : FunSpec({
    val root = Path.of(requireNotNull(System.getenv("MANUAL_R9_ROOT")))
    val archiveRoot = Path.of(requireNotNull(System.getenv("MANUAL_R9_ARCHIVES")))
    val output = Path.of(requireNotNull(System.getenv("MANUAL_R9_OUTPUT")))
    Files.createDirectories(output)

    val protocol = Json.parseToJsonElement(Files.readString(root.resolve(
        "experiments/manual-transmission/research/cruise-sport-race-20261021/CRUISE_R9_EXECUTABLE_FETCH_PROTOCOL.json"
    ))).jsonObject
    fun JsonObject.str(key: String) = requireNotNull(this[key]).jsonPrimitive.content
    fun JsonObject.num(key: String) = requireNotNull(this[key]).jsonPrimitive.int
    check(protocol.str("schema") ==
        "ARGENTUM_CRUISE_R9_EXECUTABLE_FETCH_POLICY_PREDECLARATION_V1")
    check(protocol["promotion_status"]!!.jsonPrimitive.content ==
        "NOT_AUTHORIZED_RESEARCH_PROTOCOL_ONLY")
    check(protocol["budget"]!!.jsonObject.num("new_games_authorized") == 0)
    check(protocol["budget"]!!.jsonObject.num("remaining_reserved") == 2)
    val IDs = protocol["preregistered_cases"]!!.jsonArray.map {
        it.jsonObject.str("id")
    }
    check(IDs == listOf(
        "R9-POS-A-42","R9-NEG-CONTROL-42","R9-NEG-NO-DORK",
        "R9-NEG-MISSING-TARGET","R9-NEG-LIFE-TAX",
        "R9-NEG-PREEMPTIVE-LETHAL","R9-NEG-OPPONENT-WINDOW",
        "R9-NEG-ENTRY-TAPPED"
    ))
    val r5 = ManualTransmissionR5Candidate.profile
    val r9 = ManualTransmissionCruiseR9Candidate.profile
    check(r5.id == "manual-transmission-r5-survival-opt-in")
    check(r9.id == "manual-transmission-cruise-r9-executable-fetch-opt-in")
    check(r9.advisorModules == r5.advisorModules + ManualCruiseR9ExecutableFetchAdvisorModule)
    check(AiProfile.CURRENT.advisorModules.isEmpty())
    check(r9.sequenceLandsByUsableMana == r5.sequenceLandsByUsableMana)
    check(r9.respectRequiredAttackDefenders == r5.respectRequiredAttackDefenders)
    val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
    val registry = CardRegistry().apply {
        MtgSetCatalog.all.forEach {set -> register(set.cards); register(set.basicLands)}
    }
    val simulator = GameSimulator(registry)
    val processor = ActionProcessor(registry)
    val seat = EntityId("manual-mirror-1")
    val archiveSha = mapOf(
        "control" to "031011134d10e3ae002ad7dc304ff7dafcc5e14f9a117c436e510dfb984349cb",
        "a" to "a4396d43fdd831f5f67f2a6b865630af9a90ff1420c2b127be6e6b2f294a6efe"
    )
    fun hash(file: Path) = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(file)).joinToString(""){"%02x".format(it)}
    fun name(state: GameState, id: EntityId) =
        requireNotNull(state.getEntity(id)?.get<CardComponent>()).name

    fun originalBefore42(arm: String): GameState {
        val file = archiveRoot.resolve("R6-" + arm + "-202610202141-original.zip")
        check(Files.isRegularFile(file))
        check(hash(file) == archiveSha.getValue(arm)) {
            "Original immutable R6 artifact digest mismatch for " + arm
        }
        ZipFile(file.toFile()).use { z ->
            val outer = "precision-r6-prospective-output/"
            val base = outer + arm + "-seed-202610202141/"
            fun original(path: String) = z.getInputStream(
                requireNotNull(z.getEntry(path)){ "Missing original " + path }
            ).bufferedReader().readText()
            check(original(outer + "source-sha.txt").trim() ==
                "53a5162eb3659310b1878894a90ae58c244a6745")
            val summary = Json.parseToJsonElement(original(base + "summary.json")).jsonObject
            check(summary.str("status") == "COMPLETE" &&
                summary.str("profile") == r5.id &&
                !summary["prefixOnly"]!!.jsonPrimitive.boolean)
            var state = json.decodeFromString<GameState>(original(base + "initial-state.json"))
            val stream = original(base + "actions.jsonl")
                .lineSequence().filter(String::isNotBlank)
                .map { Json.parseToJsonElement(it).jsonObject }.toList()
            check(stream.size == summary.num("accepted") * 2)
            for (i in 1..41) {
                val submit = stream[(i-1)*2]
                val reference = stream[(i-1)*2+1]
                check(submit.str("record") == "submit" && submit.num("index") == i)
                check(reference.str("record") == "result" && reference.num("index") == i)
                val executed = processor.process(
                    state, json.decodeFromString<GameAction>(submit.str("action"))
                ).result
                check(executed.error == null) {
                    "R9 archived action replay failed at " + i + ": " + executed.error
                }
                check(executed.events.map { it.toString() } ==
                    reference["events"]!!.jsonArray.map { it.jsonPrimitive.content }) {
                    "R9 archived events drift at action " + i
                }
                check(state.turnOrder.map { executed.state.lifeTotal(it) } ==
                    reference["life"]!!.jsonArray.map { it.jsonPrimitive.int }) {
                    "R9 original life totals drift at action " + i
                }
                state = executed.state
            }
            val action42 = stream[82]
            check(action42.str("record") == "submit" && action42.num("index") == 42)
            check(action42.str("actor") == seat.value)
            val chosen = json.decodeFromString<GameAction>(action42.str("action"))
            check(chosen is PlayLand && name(state, chosen.cardId) == "Opal Palace")
            check(state.activePlayerId == seat && state.priorityPlayerId == seat &&
                state.phase.isMainPhase && state.stack.isEmpty() &&
                state.pendingDecision == null)
            return state
        }
    }

    fun quote(state: GameState, land: String) =
        simulator.getLegalActions(state, seat).single {
            it.affordable && (it.action as? PlayLand)?.cardId?.let { id ->
                name(state,id) == land
            } == true
        }
    fun adjusted(state: GameState, land: String, defaultScore: Double = 2.95): Double? =
        ManualCruiseR9ExecutableFetchAdvisor.evaluateLandPlay(LandPlayContext(
            state=state, playerId=seat, action=quote(state,land), 
            passScore = -1.95, defaultScore = defaultScore, simulator=simulator,
        ))
    fun actualCandidate(state: GameState) = AIPlayer.create(registry,seat,r9).chooseAction(state)
    fun record(id: String, outcome: String, extra: String = "") {
        val obj = buildJsonObject {
            put("id",id);put("result",outcome)
            put("originalFullGamesReplayed",0)
            put("originalActionPrefixReplayed",41)
            put("hypotheticalOpponentPriorityPassesMechanismOnly",true)
            put("productionProfileChanged",false)
            put("newFullGames",0)
            put("extra",extra)
        }
        Files.writeString(output.resolve(id+".json"),obj.toString()+"\n")
        println("CRUISE_R9_FIXED_CONTROL=" + obj)
    }

    test("R9-POS-A-42 original 41 events and genuine source-funded Misty choice") {
        val original = originalBefore42("a")
        val existing = AIPlayer.create(registry,seat,r5).chooseAction(original)
        check(existing is PlayLand && name(original,existing.cardId)=="Opal Palace")
        val score = adjusted(original,"Misty Rainforest")
        check(score != null && kotlin.math.abs(score - 3.075) < 1e-6) {
            "Original real fetch-to-ramp executable line not credited"
        }
        val chosen = actualCandidate(original)
        check(chosen is PlayLand && name(original,chosen.cardId)=="Misty Rainforest") {
            "CRUISE R9 did not choose the predeclared original A acceleration"
        }
        check(processor.process(original,chosen).result.error == null)
        record("R9-POS-A-42","TRUE_SOURCE_FUNDED_FETCH_LAND_SELECTED",
            "R5=Opal Palace R9=Misty Rainforest")
    }
    test("R9-NEG-CONTROL-42 original tapped Evolving may not get Birds credit") {
        val original = originalBefore42("control")
        check(adjusted(original,"Evolving Wilds") == null)
        val r5Chosen = AIPlayer.create(registry,seat,r5).chooseAction(original)
        val r9Chosen = actualCandidate(original)
        check(r5Chosen is PlayLand && name(original,r5Chosen.cardId)=="Opal Palace")
        check(r9Chosen == r5Chosen) { "Control fake-green bonus displaced original choice" }
        record("R9-NEG-CONTROL-42","UNPAYABLE_TAPPED_FOREST_CREDIT_REJECTED")
    }
    test("R9-NEG-NO-DORK does not invent a creature or resource") {
        val source = originalBefore42("a")
        val birds = source.getHand(seat).single {name(source,it)=="Birds of Paradise"}
        val without = source.removeFromZone(ZoneKey(seat,Zone.HAND),birds)
            .addToZone(ZoneKey(seat,Zone.GRAVEYARD),birds)
        check(adjusted(without,"Misty Rainforest")==null)
        record("R9-NEG-NO-DORK","NO_HAND_CREATURE_NO_CREDIT")
    }
    test("R9-NEG-MISSING-TARGET no fictional land from hidden library") {
        val source=originalBefore42("a")
        var state=source
        val available=source.getLibrary(seat).filter { id ->
            val card=source.getEntity(id)?.get<CardComponent>() ?: return@filter false
            val landTypes=card.typeLine.toString()
            card.typeLine.isLand && ("Forest" in landTypes || "Island" in landTypes)
        }
        check(available.isNotEmpty())
        for (land in available) {
            state=state.removeFromZone(ZoneKey(seat,Zone.LIBRARY),land)
                .addToZone(ZoneKey(seat,Zone.EXILE),land)
        }
        check(adjusted(state,"Misty Rainforest")==null)
        record("R9-NEG-MISSING-TARGET","NO_SEARCHABLE_FOREST_ISLAND_NO_CREDIT",
            "Removed only actual searchable land-type targets in synthetic state")
    }
    test("R9-NEG-LIFE-TAX activation may not be funded at one life") {
        val source=originalBefore42("a")
        val low=source.withLifeTotal(seat,1)
        check(adjusted(low,"Misty Rainforest")==null)
        record("R9-NEG-LIFE-TAX","NO_NONLETHAL_FETCH_PAYMENT_NO_CREDIT")
    }
    test("R9-NEG-OPPONENT-WINDOW never infers own main-phase land play") {
        val source=originalBefore42("a")
        val land=quote(source,"Misty Rainforest")
        val opponent=source.turnOrder.first { it != seat }
        val noPriority=source.copy(priorityPlayerId=opponent)
        check(ManualCruiseR9ExecutableFetchAdvisor.evaluateLandPlay(
            LandPlayContext(noPriority,seat,land,-1.95,2.95,simulator)
        )==null)
        val noMain=source.copy(phase=Phase.COMBAT,step=Step.BEGIN_COMBAT)
        check(ManualCruiseR9ExecutableFetchAdvisor.evaluateLandPlay(
            LandPlayContext(noMain,seat,land,-1.95,2.95,simulator)
        )==null)
        record("R9-NEG-OPPONENT-WINDOW","NOT_OWN_LEGAL_MAIN_PHASE_NO_CREDIT")
    }
    test("R9-NEG-ENTRY-TAPPED cannot spend the Forest before a legal untap") {
        val original=originalBefore42("control")
        val baseline=AIPlayer.create(registry,seat,r5).chooseAction(original)
        check(adjusted(original,"Evolving Wilds",2.95)==null)
        check(actualCandidate(original)==baseline)
        record("R9-NEG-ENTRY-TAPPED","TAPPED_SEARCH_FOREST_NO_SAME_TURN_GREEN")
    }
})
