package com.wingedsheep.ai.engine

import com.wingedsheep.ai.engine.advisor.LandPlayContext
import com.wingedsheep.ai.engine.advisor.modules.ManualCruiseR9ExecutableFetchAdvisor
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
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
 * Full R9 predeclared 8-case source-bound lane, zero new full games.
 *
 * Two exact historical CONTROL/A first-41-action prefixes and R5 comparison;
 * other negatives are deterministic interventions on that frozen state. Each
 * historical submitted action, result event and life vector must replay exactly.
 * Opponent autopasses are ONLY a conditional engine-mechanism witness inside
 * the optional advisor, never a forecast or scored win.
 */
class ManualCruiseR9ExactFetchPolicyTest : FunSpec({
    val root = Path.of(requireNotNull(System.getenv("MANUAL_R9_ROOT")))
    val archives = Path.of(requireNotNull(System.getenv("MANUAL_R9_ARCHIVES")))
    val output = Path.of(requireNotNull(System.getenv("MANUAL_R9_OUTPUT")))
    Files.createDirectories(output)
    val protocol = Json.parseToJsonElement(Files.readString(root.resolve(
        "experiments/manual-transmission/research/cruise-sport-race-20261021/CRUISE_R9_EXECUTABLE_FETCH_PROTOCOL.json"
    ))).jsonObject
    check(protocol["schema"]!!.jsonPrimitive.content ==
        "ARGENTUM_CRUISE_R9_EXECUTABLE_FETCH_POLICY_PREDECLARATION_V1")
    check(protocol["budget"]!!.jsonObject["new_games_authorized"]!!.jsonPrimitive.int == 0)
    check(protocol["budget"]!!.jsonObject["remaining_reserved"]!!.jsonPrimitive.int == 2)
    check(protocol["preregistered_cases"]!!.jsonArray.size == 8)
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
    val engine = ActionProcessor(registry)
    val simulator = GameSimulator(registry)
    val seat = EntityId("manual-mirror-1")
    val r5 = ManualTransmissionR5Candidate.profile
    val r9 = ManualTransmissionCruiseR9Candidate.profile
    check(r9.advisorModules.size == r5.advisorModules.size + 1)
    check(r9.advisorModules.dropLast(1) == r5.advisorModules)
    check(r9.id != r5.id && !r5.advisorModules.contains(r9.advisorModules.last()))
    check(AiProfile.CURRENT.advisorModules.isEmpty())

    fun hash(path: Path): String = MessageDigest.getInstance("SHA-256")
        .digest(Files.readAllBytes(path)).joinToString("") { "%02x".format(it) }
    fun JsonObject.s(k:String):String = requireNotNull(this[k]).jsonPrimitive.content
    fun JsonObject.n(k:String):Int = requireNotNull(this[k]).jsonPrimitive.int
    fun cardName(state:GameState,id:EntityId) =
        state.getEntity(id)?.get<CardComponent>()?.name
    fun landAction(state:GameState, land:String) =
        simulator.getLegalActions(state,seat).single {
            it.affordable && (it.action as? PlayLand)?.cardId?.let { id ->
                cardName(state,id) == land
            } == true
        }
    fun quote(state:GameState, land:String):Double? {
        val chosen=landAction(state,land)
        return ManualCruiseR9ExecutableFetchAdvisor.evaluateLandPlay(
            LandPlayContext(
                state=state,playerId=seat,action=chosen,
                passScore=-1.95,defaultScore=2.95,simulator=simulator
            )
        )
    }
    val originalStates = mutableMapOf<String,GameState>()
    fun original(arm:String):GameState = originalStates.getOrPut(arm) {
        check(arm in setOf("control","a"))
        val archive = archives.resolve("R6-$arm-202610202141-original.zip")
        val digest = if (arm=="a")
            "a4396d43fdd831f5f67f2a6b865630af9a90ff1420c2b127be6e6b2f294a6efe" else
            "031011134d10e3ae002ad7dc304ff7dafcc5e14f9a117c436e510dfb984349cb"
        check(Files.isRegularFile(archive) && hash(archive)==digest) {
            "Source R6 archive integrity failed: $arm"
        }
        ZipFile(archive.toFile()).use { zip ->
            val outer="precision-r6-prospective-output/"
            val pre=outer+arm+"-seed-202610202141/"
            fun read(member:String)=zip.getInputStream(requireNotNull(zip.getEntry(member)))
                .bufferedReader().readText()
            check(read(outer+"source-sha.txt").trim()=="53a5162eb3659310b1878894a90ae58c244a6745")
            val summary=Json.parseToJsonElement(read(pre+"summary.json")).jsonObject
            check(summary.s("profile") == r5.id && summary.s("status")=="COMPLETE")
            check(summary.n("submitted")==summary.n("accepted") &&
                summary["prefixOnly"]!!.jsonPrimitive.boolean == false)
            var state=json.decodeFromString<GameState>(read(pre+"initial-state.json"))
            val actions=read(pre+"actions.jsonl").lineSequence().filter(String::isNotBlank)
                .map{Json.parseToJsonElement(it).jsonObject}.toList()
            check(actions.size==2*summary.n("accepted"))
            for(index in 1..41) {
                val a=actions[(index-1)*2]
                val b=actions[(index-1)*2+1]
                check(a.s("record")=="submit" && b.s("record")=="result" &&
                    a.n("index")==index && b.n("index")==index)
                val action=json.decodeFromString<GameAction>(a.s("action"))
                val result=engine.process(state,action).result
                check(result.error==null) { "Archive replay reject $arm index $index" }
                check(result.events.map { it.toString() } ==
                    b["events"]!!.jsonArray.map { it.jsonPrimitive.content }) {
                    "Archive event mismatch $arm index $index"
                }
                check(state.turnOrder.map { result.state.lifeTotal(it) } ==
                    b["life"]!!.jsonArray.map { it.jsonPrimitive.int }) {
                    "Archive life mismatch $arm index $index"
                }
                state=result.state
            }
            val target=json.decodeFromString<GameAction>(actions[82].s("action"))
            check(actions[82].n("index")==42 && target is PlayLand &&
                cardName(state,target.cardId)=="Opal Palace")
            check(state.activePlayerId==seat && state.priorityPlayerId==seat &&
                state.phase.isMainPhase && state.stack.isEmpty() &&
                state.pendingDecision==null)
            state
        }
    }
    fun write(id:String,state:GameState,baseline:GameAction,candidate:GameAction,
              quote:Double? = null) {
        val result=buildJsonObject {
            put("fixture",id)
            put("originalSeed","202610202141")
            put("historicalActionsReplayed",41)
            put("r5Choice",(baseline as? PlayLand)?.cardId?.let { cardName(state,it) }
                ?: baseline::class.simpleName ?: "unknown")
            put("r9Choice",(candidate as? PlayLand)?.cardId?.let { cardName(state,it) }
                ?: candidate::class.simpleName ?: "unknown")
            put("landScoreOverride",quote?.let(::JsonPrimitive) ?: JsonNull)
            put("newFullGames",0)
            put("profilePromoted",false)
        }
        Files.writeString(output.resolve("$id.json"),result.toString()+"\n")
        println("CRUISE_R9_EXACT_RESULT="+result)
    }

    test("R9-POS-A-42: original R5 Opal tie becomes legal Misty development first") {
        val state=original("a")
        val baseline=AIPlayer.create(registry,seat,r5).chooseAction(state)
        check(baseline is PlayLand && cardName(state,baseline.cardId)=="Opal Palace")
        val override=quote(state,"Misty Rainforest")
        check(override != null && override > 2.95 && override < 3.2) {
            "Positive R9 must be a tiny bounded actual-mana tie break"
        }
        val candidate=AIPlayer.create(registry,seat,r9).chooseAction(state)
        check(candidate is PlayLand && cardName(state,candidate.cardId)=="Misty Rainforest") {
            "Opt-in R9 didn't select the real payable early-development line: $candidate"
        }
        check(engine.process(state,candidate).result.error==null)
        write("R9-POS-A-42",state,baseline,candidate,override)
    }

    test("R9-NEG-CONTROL-42: tapped Evolving Forest has no same-turn credit") {
        val state=original("control")
        val baseline=AIPlayer.create(registry,seat,r5).chooseAction(state)
        val candidate=AIPlayer.create(registry,seat,r9).chooseAction(state)
        check(baseline is PlayLand && cardName(state,baseline.cardId)=="Opal Palace")
        check(candidate==baseline) {"R9 incorrectly prioritized Evolving Wilds"}
        check(quote(state,"Evolving Wilds")==null)
        check(engine.process(state,candidate).result.error==null)
        write("R9-NEG-CONTROL-42",state,baseline,candidate)
    }

    test("R9-NEG-NO-DORK: absent actual one-mana creature earns nothing") {
        val state=original("a")
        val birds=state.getHand(seat).single { cardName(state,it)=="Birds of Paradise" }
        val noDork=state.removeFromZone(ZoneKey(seat,Zone.HAND),birds)
            .addToZone(ZoneKey(seat,Zone.GRAVEYARD),birds)
        check(quote(noDork,"Misty Rainforest")==null)
        write("R9-NEG-NO-DORK",noDork,PassPriority(seat),PassPriority(seat))
    }

    test("R9-NEG-MISSING-TARGET: no green-type search target in actual library") {
        val state=original("a")
        val greenLandIds=state.getLibrary(seat).filter { id ->
            val card=state.getEntity(id)?.get<CardComponent>()
            card?.typeLine?.isLand==true &&
                card.typeLine.toString().contains("Forest")
        }
        check(greenLandIds.isNotEmpty())
        val noGreen=greenLandIds.fold(state) { acc,id ->
            acc.removeFromZone(ZoneKey(seat,Zone.LIBRARY),id)
                .addToZone(ZoneKey(seat,Zone.EXILE),id)
        }
        check(quote(noGreen,"Misty Rainforest")==null)
        write("R9-NEG-MISSING-TARGET",noGreen,PassPriority(seat),PassPriority(seat))
    }

    test("R9-NEG-LIFE-TAX: fetch cannot earn mana if paid activation is fatal") {
        val state=original("a").withLifeTotal(seat,1)
        check(quote(state,"Misty Rainforest")==null)
        write("R9-NEG-LIFE-TAX",state,PassPriority(seat),PassPriority(seat))
    }

    test("R9-NEG-OPPONENT-WINDOW: no bonus without own priority or main phase") {
        val state=original("a")
        val noPriority=state.copy(priorityPlayerId=EntityId("manual-mirror-0"))
        val offStep=state.copy(phase=Phase.COMBAT,step=Step.BEGIN_COMBAT)
        // The land action is the pinned legal original candidate, but neither
        // altered state may claim that the actor can actually play it.
        val option=landAction(state,"Misty Rainforest")
        fun evaluate(hypothetical:GameState)=
            ManualCruiseR9ExecutableFetchAdvisor.evaluateLandPlay(
                LandPlayContext(hypothetical,seat,option,-1.95,2.95,simulator)
            )
        check(evaluate(noPriority)==null && evaluate(offStep)==null)
        write("R9-NEG-OPPONENT-WINDOW",state,PassPriority(seat),PassPriority(seat))
    }

    test("R9-NEG-ENTRY-TAPPED: legal Evolving Wilds cannot fund Birds") {
        val state=original("control")
        check(quote(state,"Evolving Wilds")==null)
        write("R9-NEG-ENTRY-TAPPED",state,PassPriority(seat),PassPriority(seat))
    }
})

/** Preserve a real immediately legal Bolt terminal against speculative ramp. */
class ManualCruiseR9ImmediateLethalGuardTest : ScenarioTestBase() {
    init {
        test("R9-NEG-PREEMPTIVE-LETHAL: immediate opponent lethal wins over fetch development") {
            val game=scenario().withPlayers("Manual R9","Opponent")
                .withCardInCommandZone(1,"Animar, Soul of Elements")
                .withCardInHand(1,"Misty Rainforest")
                .withCardInHand(1,"Birds of Paradise")
                .withCardInHand(1,"Lightning Bolt")
                .withCardInLibrary(1,"Forest")
                .withCardOnBattlefield(1,"Mountain")
                .withLifeTotal(2,3)
                .withActivePlayer(1)
                .inPhase(Phase.PRECOMBAT_MAIN,Step.PRECOMBAT_MAIN)
                .build()
            val seat=game.player1Id
            val before=game.state
            val original=AIPlayer.create(cardRegistry,seat,
                ManualTransmissionR5Candidate.profile).chooseAction(before)
            val candidate=AIPlayer.create(cardRegistry,seat,
                ManualTransmissionCruiseR9Candidate.profile).chooseAction(before)
            fun isBolt(a:GameAction) =
                a is CastSpell && before.getEntity(a.cardId)?.get<CardComponent>()?.name=="Lightning Bolt"
            check(isBolt(original) && isBolt(candidate))
            val actual=actionProcessor.process(before,candidate).result
            check(actual.error==null)
            val sim=GameSimulator(cardRegistry).simulate(before,candidate)
            check(sim is SimulationResult.Terminal &&
                sim.state.gameOver && sim.state.winnerId==seat)
            println("CRUISE_R9_LETHAL_GUARD=R5_AND_R9_BOLT_ACCEPTED; conditionalQuietStateOnly=true; newGames=0")
        }
    }
}
