package com.wingedsheep.ai.engine

import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path

/**
 * Opt-in ONLY. R22-derived real four-seat, R5-policy bounded gameplay.
 * No Wan-specific substitution, triggers, seed, or experiment binding.
 * This harness is NOT an invitation to execute before all card preflight gates pass.
 */
class ManualPrecisionR1GameplayHarnessTest : FunSpec({
    test("explicit, preflight-gated precision matched three-arm observation") {
        check(System.getenv("MANUAL_PRECISION_GAMEPLAY_AUTHORIZATION") == "EXPLICIT_PRECISION_PILOT") {
            "Precision gameplay requires explicit authorization and passing preflight"
        }
        val seed = requireNotNull(System.getenv("MANUAL_PRECISION_SEED")).toLong()
        val pinned = requireNotNull(System.getenv("MANUAL_PRECISION_PREDECLARED_SEED"))
        check(seed.toString() == pinned && seed in setOf(202610202111L, 202610202112L)) {
            "Precision R1 refuses unknown or substituted gameplay seeds"
        }
        val arm = requireNotNull(System.getenv("MANUAL_PRECISION_ARM"))
        check(arm in setOf("control", "a", "b"))
        val mode = requireNotNull(System.getenv("MANUAL_PRECISION_PREFIX_ONLY"))
        check(mode in setOf("0", "1"))
        val prefixOnly = mode == "1"

        val selectedProfile = ManualTransmissionR5Candidate.profile
        check(selectedProfile.id == "manual-transmission-r5-survival-opt-in")
        val r4Owner = com.wingedsheep.ai.engine.advisor.modules.ManualBallistaContinuationAdvisorModule
        val r7Owner = com.wingedsheep.ai.engine.advisor.modules.ManualR7BallistaAllocationAdvisorModule
        val pact = com.wingedsheep.ai.engine.advisor.modules.ManualPactDebtAdvisorModule
        check(pact in selectedProfile.advisorModules && r4Owner in selectedProfile.advisorModules)
        check(r7Owner !in selectedProfile.advisorModules)
        check(AiProfile.CURRENT.advisorModules.isEmpty())

        val outputRoot = Path.of(requireNotNull(System.getenv("MANUAL_PRECISION_OUTPUT")))
        val output = outputRoot.resolve(arm + "-seed-" + seed +
            if (prefixOnly) "-prefix" else "")
        Files.createDirectories(output)
        check(!Files.exists(output.resolve("actions.jsonl"))) {
            "Refusing to overwrite an existing Precision observation"
        }
        val json = Json {
            serializersModule = engineSerializersModule
            allowStructuredMapKeys = true
        }
        fun write(name: String, value: JsonElement) =
            Files.writeString(output.resolve(name), value.toString())
        val registry = CardRegistry()
        MtgSetCatalog.all.forEach { set ->
            registry.register(set.cards)
            registry.register(set.basicLands)
        }
        var state: GameState? = null
        var players = emptyList<EntityId>()
        var submitted = 0
        var accepted = 0
        var status = "PRE_INITIALIZATION"
        var error: String? = null
        val started = System.nanoTime()
        val casts = linkedMapOf<String, Int>()
        val turns = linkedMapOf<String, Int>()
        val firstCommanderGlobalTurn = linkedMapOf<String, Int>()
        val firstCommanderPersonalTurn = linkedMapOf<String, Int>()
        val offered = linkedMapOf<String, Int>()
        val declined = linkedMapOf<String, Int>()
        val legalOffered = linkedMapOf<String, Int>()
        val legalDeclined = linkedMapOf<String, Int>()
        val replacementActions = linkedMapOf<String, Int>()
        var scoredChoiceRecords = 0
        var comboRelevantActions = 0

        val actions = Files.newBufferedWriter(output.resolve("actions.jsonl"))
        val insights = Files.newBufferedWriter(output.resolve("decisions.jsonl"))
        try {
            ManualPrecisionR1Inputs.verifySlotDiffs()
            ManualPrecisionR1Inputs.verifyCompiledLegality(arm, registry)
            if (arm != "control")
                ManualPrecisionR1Inputs.verifyMatchedSlots(seed, registry, arm)
            val raw = ManualPrecisionR1Inputs.names(arm)
            val selected = ManualPrecisionR1Inputs.normalizedNames(arm)
            check(raw.size == 100 && selected.size == 100)
            val changedCards = if (arm == "a") ManualPrecisionR1Inputs.LAND_SWAPS else
                if (arm == "b") ManualPrecisionR1Inputs.LAND_SWAPS +
                    ManualPrecisionR1Inputs.ADDITIONAL_SWAPS else emptyList()
            check(raw.count { it == ManualPrecisionR1Inputs.COMMANDER } == 1)
            write("registry-audit.json", buildJsonObject {
                put("registered", true)
                put("commanderLegal", true)
                put("cardCount", selected.size)
                put("arm", arm)
                put("deckSha256", ManualPrecisionR1Inputs.sha256(ManualPrecisionR1Inputs.bytes(arm)))
                put("candidateChangedSlots", changedCards.size)
                put("issues", JsonArray(emptyList()))
            })
            write("r1-initializer-preflight.json", buildJsonObject {
                put("seed", seed); put("arm", arm)
                put("passed", true); put("positiveSeats", 4)
                put("changedSlotCount", changedCards.size)
                put("unchangedSlotOrderPreserved", true)
                put("method", "real GameInitializer / original-deck-position exact-slot comparison")
                put("sharedLibraryCardsPerSeat", 99 - changedCards.size)
            })
            val init = ManualPrecisionR1Inputs.initialize(arm, seed, registry)
            players = (0..3).map { EntityId("manual-mirror-" + it) }
            state = init
            check(players.size == 4 && init.turnOrder.size == 4)
            for (id in players) {
                val command = init.getZone(ZoneKey(id, Zone.COMMAND))
                check(command.size == 1 &&
                    init.getEntity(command.single())?.get<CardComponent>()?.name ==
                        ManualPrecisionR1Inputs.COMMANDER)
                check(init.getEntity(id)?.get<CommanderRegistryComponent>()?.commanderIds?.toSet() ==
                    command.toSet())
                check(init.getZone(ZoneKey(id, Zone.LIBRARY)).size == 92)
                check(init.getZone(ZoneKey(id, Zone.HAND)).size == 7)
                check(init.lifeTotal(id) == 40)
                casts[id.value] = 0; turns[id.value] = 0
                offered[id.value] = 0; declined[id.value] = 0
                legalOffered[id.value] = 0; legalDeclined[id.value] = 0
            }
            Files.writeString(output.resolve("initial-state.json"), json.encodeToString(init))
            write("initialization.json", buildJsonObject {
                put("seed", seed); put("arm", arm)
                put("profile", selectedProfile.id)
                put("commanderPerSeat", ManualPrecisionR1Inputs.COMMANDER)
                put("startingLife", 40); put("libraryBeforeDraw", 99)
                put("openingPolicy", "Four seats keep seed-derived initial seven, no smoother or mulligans")
                put("players", JsonArray(players.map { JsonPrimitive(it.value) }))
            })
            write("r1-arm-binding.json", buildJsonObject {
                put("seed", seed); put("arm", arm); put("profile", selectedProfile.id)
                put("deckPath", ManualPrecisionR1Inputs.file(arm).toString())
                put("deckSha256", ManualPrecisionR1Inputs.sha256(ManualPrecisionR1Inputs.bytes(arm)))
                put("changedSlots", changedCards.size)
                put("matchedInitializerPreflightVerified", true)
                put("r7CombinedAdvisorAbsent", true); put("automaticAdoption", false)
            })
            status = "RUNNING"
            val sink = AiInsightSink { _, insight ->
                scoredChoiceRecords++
                val options = insight.options.filter {
                    it.cardName == ManualPrecisionR1Inputs.COMMANDER && it.action is CastSpell
                }
                if (options.isNotEmpty()) {
                    offered[insight.playerId.value] = offered.getValue(insight.playerId.value) + 1
                    if (options.none { it.chosen })
                        declined[insight.playerId.value] = declined.getValue(insight.playerId.value) + 1
                }
                insights.write(json.encodeToString(insight)); insights.newLine(); insights.flush()
            }
            val ai = players.associateWith { player ->
                AIPlayer.create(registry, player, selectedProfile, insightSink = sink)
            }
            val processor = ActionProcessor(registry)
            val legal = com.wingedsheep.engine.legalactions.LegalActionEnumerator.create(registry)
            var lastTurn = -1
            var sameTurnActions = 0
            while (true) {
                val current = requireNotNull(state)
                if (current.gameOver) { status = "COMPLETE"; break }
                val reason = ManualR9CensorProtocol.reached(
                    elapsedMs = (System.nanoTime() - started) / 1_000_000,
                    submitted = submitted, globalTurn = current.turnNumber,
                    sameTurnActions = sameTurnActions,
                )
                if (reason != null) {
                    status = reason.status
                    error = null
                    ManualR9CensorProtocol.writeCensorCheckpoint(output, current, json)
                    break
                }
                if (lastTurn != current.turnNumber) {
                    lastTurn = current.turnNumber; sameTurnActions = 0
                    current.activePlayerId?.let { player ->
                        turns[player.value] = turns.getValue(player.value) + 1
                    }
                }
                val decision = current.pendingDecision
                val actor = decision?.playerId ?: requireNotNull(current.priorityPlayerId)
                val action: GameAction = if (decision != null)
                    SubmitDecision(actor, ai.getValue(actor).respondToDecision(current, decision))
                    else ai.getValue(actor).chooseAction(current)
                if (decision == null && current.activePlayerId == actor &&
                    current.phase.isMainPhase &&
                    current.getZone(ZoneKey(actor, Zone.COMMAND)).isNotEmpty()) {
                    val commands = current.getZone(ZoneKey(actor, Zone.COMMAND)).toSet()
                    val canCast = legal.enumerate(current, actor,
                        com.wingedsheep.engine.legalactions.EnumerationMode.ACTIONS_ONLY).any {
                        it.affordable && (it.action as? CastSpell)?.cardId in commands
                    }
                    if (canCast) {
                        legalOffered[actor.value] = legalOffered.getValue(actor.value) + 1
                        if (action !is CastSpell || action.cardId !in commands)
                            legalDeclined[actor.value] = legalDeclined.getValue(actor.value) + 1
                    }
                }
                submitted++; sameTurnActions++
                val source = when (action) {
                    is CastSpell -> action.cardId
                    is ActivateAbility -> action.sourceId
                    else -> null
                }
                val sourceName = source?.let {
                    current.getEntity(it)?.get<CardComponent>()?.name
                }
                if (sourceName != null && sourceName in changedCards.map { it.second })
                    replacementActions[sourceName] = replacementActions.getOrDefault(sourceName, 0) + 1
                if (sourceName != null && sourceName in setOf(
                    ManualPrecisionR1Inputs.COMMANDER, "Ancestral Statue",
                    "Walking Ballista", "Purphoros, God of the Forge"
                )) comboRelevantActions++
                actions.write(buildJsonObject {
                    put("record", "submit"); put("index", submitted)
                    put("turn", current.turnNumber); put("actor", actor.value)
                    put("step", current.step.name); put("sourceName", sourceName)
                    put("pendingDecision", decision?.toString())
                    put("decisionSource", decision?.context?.sourceName)
                    put("action", json.encodeToString<GameAction>(action))
                }.toString()); actions.newLine(); actions.flush()
                val result = processor.process(current, action).result
                result.events.filterIsInstance<SpellCastEvent>().forEach { e ->
                    if (e.cardName == ManualPrecisionR1Inputs.COMMANDER) {
                        casts[e.casterId.value] = casts.getValue(e.casterId.value) + 1
                        firstCommanderGlobalTurn.putIfAbsent(e.casterId.value, current.turnNumber)
                        firstCommanderPersonalTurn.putIfAbsent(e.casterId.value,
                            turns.getValue(e.casterId.value))
                    }
                }
                actions.write(buildJsonObject {
                    put("record", "result"); put("index", submitted)
                    put("error", result.error?.toString())
                    put("events", JsonArray(result.events.map { JsonPrimitive(it.toString()) }))
                    put("life", JsonArray(players.map { JsonPrimitive(result.state.lifeTotal(it)) }))
                    put("gameOver", result.state.gameOver)
                    put("winner", result.state.winnerId?.value)
                }.toString()); actions.newLine(); actions.flush()
                state = result.state
                if (result.error != null) {
                    status = "ENGINE_REJECTION"; error = sourceName + ": " + result.error
                    break
                }
                accepted++
                if (accepted == 64) {
                    Files.writeString(output.resolve("smoke-passed.txt"),
                        "64 accepted real ActionProcessor submissions\n")
                    if (prefixOnly) { status = "PREFIX_COMPLETE"; break }
                }
            }
        } catch (failure: Throwable) {
            status = "EXCEPTION"
            error = failure::class.qualifiedName + ": " + failure.message
            Files.writeString(output.resolve("exception.txt"), failure.stackTraceToString())
        } finally {
            actions.close(); insights.close()
            val final = state
            if (final != null) runCatching {
                Files.writeString(output.resolve("final-state.json"), json.encodeToString(final))
            }.onFailure {
                Files.writeString(output.resolve("snapshot-error.txt"), it.stackTraceToString())
            }
            val summary = buildJsonObject {
                put("status", status); put("seed", seed); put("arm", arm)
                put("profile", selectedProfile.id); put("prefixOnly", prefixOnly)
                put("submitted", submitted); put("accepted", accepted); put("error", error)
                put("wallClockMs", (System.nanoTime() - started) / 1_000_000)
                put("censorReason", status.takeIf(ManualR9CensorProtocol::isCensored))
                put("censorCheckpointSaved", ManualR9CensorProtocol.isCensored(status) &&
                    Files.exists(output.resolve("censor-checkpoint.json")))
                put("gameOver", final?.gameOver == true); put("winner", final?.winnerId?.value)
                put("playerTurnNumber", final?.turnNumber)
                put("scoredChoiceRecords", scoredChoiceRecords)
                put("comboRelevantActionCount", comboRelevantActions)
                put("replacementActionCounts", buildJsonObject {
                    replacementActions.forEach { (name, amount) -> put(name, amount) }
                })
                put("commanderCasts", buildJsonObject {
                    casts.forEach { (id, value) -> put(id, value) }
                })
                put("firstCommanderGlobalTurn", buildJsonObject {
                    firstCommanderGlobalTurn.forEach { (id, value) -> put(id, value) }
                })
                put("firstCommanderPersonalTurn", buildJsonObject {
                    firstCommanderPersonalTurn.forEach { (id, value) -> put(id, value) }
                })
                put("scoredCommanderOpportunityDecisions", buildJsonObject {
                    offered.forEach { (id, value) -> put(id, value) }
                })
                put("scoredCommanderDeclinedDecisions", buildJsonObject {
                    declined.forEach { (id, value) -> put(id, value) }
                })
                put("legalCommanderOpportunityDecisions", buildJsonObject {
                    legalOffered.forEach { (id, value) -> put(id, value) }
                })
                put("legalCommanderDeclinedDecisions", buildJsonObject {
                    legalDeclined.forEach { (id, value) -> put(id, value) }
                })
                put("turnsByPlayer", buildJsonObject {
                    turns.forEach { (id, value) -> put(id, value) }
                })
                put("life", buildJsonObject {
                    players.forEach { id -> put(id.value, final?.lifeTotal(id)) }
                })
                put("comboAttempts", "Not automatically inferred: inspect complete action and decision streams")
            }
            write("summary.json", summary)
            println("PRECISION_R1_GAME_RESULT=" + summary)
        }
        check(ManualR9CensorProtocol.validObservation(
            status = status, accepted = accepted, submitted = submitted,
            error = error, gameOver = state?.gameOver == true,
            winner = state?.winnerId?.value, prefixOnly = prefixOnly,
        )) { "Precision observation invalid; status=" + status + " error=" + error }
    }
})
