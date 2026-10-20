package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderComponent
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
 * Bounded original-state forks, only from previously reconstructed and source-bound
 * R4 GameStates. ActionProcessor compares legal direct submissions WITHOUT
 * autopassing opposing players. GameSimulator's quiet branch is separately labeled
 * autopass and is never presented as true competitive or opponent-responsive proof.
 */
class ManualIdentityR4CounterfactualForkTest : FunSpec({
    test("R22 original seed-1622 decisions 1153 and 1158: two legal one-action forks") {
        check(System.getenv("MANUAL_IDENTITY_R4_FORK_MODE") == "EXACT_TWO_ARCHIVED_STATES")
        val zipFile = Path.of(requireNotNull(System.getenv("MANUAL_IDENTITY_R4_FORK_ARCHIVE")))
        val output = Path.of(requireNotNull(System.getenv("MANUAL_IDENTITY_R4_FORK_OUTPUT")))
        Files.createDirectories(output)
        fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString("") { "%02x".format(it) }
        val archiveSha = sha(Files.readAllBytes(zipFile))
        check(archiveSha == "d27a0b8bedc4377c1852cfde23f217e6b92c62a193bdf67972da7320be7ac492") {
            "R4 original state evidence zip is not the accepted bytes"
        }
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        val registry = CardRegistry().apply {
            MtgSetCatalog.all.forEach { register(it.cards); register(it.basicLands) }
        }
        val processor = ActionProcessor(registry)
        val sim = GameSimulator(registry)
        val evaluator = AIPlayer.defaultEvaluator()
        val player = EntityId("manual-mirror-3")
        val expected = mapOf(
            1153 to "c763adca80ecd1d5bdf7b41acabb04f2755c64fc790bff2a60b41ff81c0fdf27",
            1158 to "cc049d0c5d0d90c506aa0ca3f65767549c30d229c55472e6b265e6ec3d6748ae",
        )
        val outRows = mutableListOf<JsonElement>()
        fun JsonObject.str(k: String) = this[k]?.jsonPrimitive?.contentOrNull
        fun JsonObject.num(k: String) = this[k]?.jsonPrimitive?.intOrNull
        ZipFile(zipFile.toFile()).use { zip ->
            fun read(relative: String) = zip.getInputStream(
                requireNotNull(zip.getEntry("r4-receipt/" + relative)) {
                    "Missing R4 artifact entry: " + relative
                }).bufferedReader().readText()
            check(read("source-sha.txt").trim() == "68a8e331c84ebf53575deab9951ec80ca75312a3")
            val report = json.parseToJsonElement(read("result.json")).jsonObject
            check(report.str("status") == "EXACT_ORIGINAL_STATE_REPLAY_VERIFIED")
            check(report.num("archivedSubmissionsReplayed") == 1513)
            check(report.num("matchingEngineEventRows") == 1513)
            check(report["prefix64FullSerializedStateEqual"]?.jsonPrimitive?.boolean == true)
            check(report.num("fullGamesInitialized") == 0 && report.num("defectsProven") == 0)
            val observed = report.getValue("observedWindows").jsonArray
                .map { it.jsonObject }.associateBy { it.getValue("index").jsonPrimitive.int }
            for (index in listOf(1153, 1158)) {
                val originalStateText = read("exact-state-before-" + index + ".json")
                check(sha(originalStateText.toByteArray()) == expected.getValue(index)) {
                    "Original state bytes changed at " + index
                }
                val sourceRow = observed.getValue(index)
                check(sourceRow.str("stateBeforeSha256") == expected.getValue(index))
                check(sourceRow["forkExecuted"]?.jsonPrimitive?.boolean == false)
                val state = json.decodeFromString<GameState>(originalStateText)
                check(state.turnNumber == 28 && state.step.name == "PRECOMBAT_MAIN")
                check(state.activePlayerId == player && state.priorityPlayerId == player)
                val insight = json.parseToJsonElement(
                    read("decision-before-" + index + ".json")).jsonObject
                val options = insight.getValue("options").jsonArray.map { it.jsonObject }
                val chosen = options.single {
                    it["chosen"]?.jsonPrimitive?.booleanOrNull == true
                }
                val animar = options.single {
                    it.str("cardName") == "Animar, Soul of Elements" &&
                        it.str("actionType") == "CastSpell"
                }
                check(chosen.getValue("action") == sourceRow.getValue("originalAction"))
                check(animar.getValue("action") == sourceRow.getValue("alternativeAnimarCast"))
                val chosenAction = json.decodeFromString<GameAction>(chosen.getValue("action").toString())
                val animarAction = json.decodeFromString<GameAction>(
                    animar.getValue("action").toString()) as CastSpell
                val actualLegal = sim.getLegalActions(state, player).filter {
                    it.affordable && (it.action as? CastSpell)?.cardId == animarAction.cardId
                }
                check(actualLegal.size == 1) {
                    "Animar isn't independently affordable at original state " + index
                }
                val cmd = state.getEntity(animarAction.cardId)?.get<CommanderComponent>()
                check(cmd?.castsFromCommandZone == 0 && state.getZone(ZoneKey(player, Zone.COMMAND))
                    .contains(animarAction.cardId))
                val chosenOne = processor.process(state, chosenAction).result
                val castOne = processor.process(state, animarAction).result
                check(chosenOne.error == null && castOne.error == null) {
                    "One of original source-bound game actions is engine-illegal at " + index
                }
                val handNames = state.getZone(ZoneKey(player, Zone.HAND)).map {
                    state.getEntity(it)?.get<CardComponent>()?.name ?: it.value
                }
                check("Ancestral Statue" in handNames)
                // OPTIONAL, explicitly non-adversarial observation: each side is advanced
                // only to the simulator's next quiet boundary using AUTO-PASS priority.
                val chosenQuiet = sim.simulate(state, chosenAction)
                val animarQuiet = sim.simulate(state, animarAction)
                fun describe(result: SimulationResult, cardName: String): JsonElement {
                    val stateAfter = when (result) {
                        is SimulationResult.Terminal -> result.state
                        is SimulationResult.NeedsDecision -> result.state
                        is SimulationResult.Illegal -> result.state
                        is SimulationResult.StoppedAtLimit -> result.state
                    }
                    val sameTurnNext = stateAfter.activePlayerId == player &&
                        stateAfter.phase.isMainPhase && stateAfter.stack.isEmpty() &&
                        stateAfter.priorityPlayerId == player
                    val hand = stateAfter.getZone(ZoneKey(player, Zone.HAND))
                    val couldCastFblthp = if (sameTurnNext) sim.getLegalActions(stateAfter, player).any {
                        val cast = it.action as? CastSpell
                        it.affordable && cast != null && cast.cardId in hand &&
                            stateAfter.getEntity(cast.cardId)?.get<CardComponent>()?.name == "Fblthp, the Lost"
                    } else false
                    val quietScore = if (result is SimulationResult.Terminal &&
                        !result.state.gameOver) evaluator.evaluate(
                            result.state, result.state.projectedState, player
                        ) else null
                    return buildJsonObject {
                        put("scenario", cardName)
                        put("simulationBoundary", result::class.simpleName)
                        put("gameOver", stateAfter.gameOver)
                        put("winner", stateAfter.winnerId?.value)
                        put("sameTurnActorHasQuietPriority", sameTurnNext)
                        put("couldCastFblthpNextAtQuietBoundary", couldCastFblthp)
                        put("nonadversarialQuietScore", quietScore)
                        put("opponentsAutoPassedPriorityDuringSimulation", true)
                        put("opponentResponsesHeldConstant", false)
                    }
                }
                outRows += buildJsonObject {
                    put("index", index)
                    put("originalSourceStateSha256", expected.getValue(index))
                    put("actor", player.value)
                    put("globalTurn", state.turnNumber)
                    put("handBefore", JsonArray(handNames.map(::JsonPrimitive)))
                    put("animarOfferManaCost", actualLegal.single().manaCostString)
                    put("commanderTaxGeneric", 0)
                    put("chosen", chosen.str("label"))
                    put("historicalChosenScore", chosen.getValue("score"))
                    put("historicalAnimarScore", animar.getValue("score"))
                    put("chosenOneActionEngineAccepted", chosenOne.error == null)
                    put("animarOneActionEngineAccepted", castOne.error == null)
                    put("chosenOneActionStackCount", chosenOne.state.stack.size)
                    put("animarOneActionStackCount", castOne.state.stack.size)
                    put("chosenQuietAutopass", describe(chosenQuiet, "original"))
                    put("animarQuietAutopass", describe(animarQuiet, "cast-animar"))
                    put("finiteControlledForks", 2)
                    put("fullCommanderGamesInitialized", 0)
                    put("actualOpponentResponsiveCounterfactual", false)
                    put("strategicDefectProven", false)
                    put("disposition", "INCONCLUSIVE_NOT_A_PROMOTION")
                }
                Files.writeString(output.resolve("two-fork-observations.json"),
                    JsonArray(outRows).toString())
                println("MANUAL_R4_EXACT_FORK " + outRows.last().toString())
            }
        }
        check(outRows.size == 2)
        val summary = buildJsonObject {
            put("status", "EXACT_STATE_TWO_FORKS_EXECUTED_NO_PROVEN_DEFECT")
            put("r4SourceCommit", "68a8e331c84ebf53575deab9951ec80ca75312a3")
            put("r4Run", 39454771346L)
            put("originalR22Run", 39435937911L)
            put("archiveSha256", archiveSha)
            put("sourceSeed", "202610191622")
            put("opportunities", JsonArray(outRows))
            put("actualOpponentResponsiveCounterfactual", false)
            put("newGames", 0)
            put("cardChanges", 0)
            put("AiProfileCurrentChanges", 0)
            put("defectsProven", 0)
            put("noHardwareOrPolicyPromotion", true)
        }
        Files.writeString(output.resolve("summary.json"), summary.toString())
        println("MANUAL_R4_FORK_RESULT=" + summary)
    }
})
