package com.wingedsheep.ai.engine

import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.ai.engine.advisor.modules.ManualAnimarDevelopmentAdvisorModule
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CommanderRegistryComponent
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.sdk.core.DeckFormat
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.CommanderEligibility
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

/** Real compiled catalog, four real 100-card decks; no synthetic card replacement or fallback. */
class ManualTransmissionRoundTwoComparisonTest : FunSpec({
    test("matched baseline and one candidate four-player Animar games") {
        if (System.getenv("MANUAL_ROUND2_RUN") != "1") return@test
        val seedIndex = requireNotNull(System.getenv("MANUAL_ROUND2_SEED_INDEX")).toInt()
        val candidateSeat = requireNotNull(System.getenv("MANUAL_ROUND2_CANDIDATE_SEAT")).toInt()
        require(seedIndex in 0..4 && candidateSeat in 0..3)
        fun runArm(arm: String): Boolean {
        val root = Path.of(requireNotNull(System.getenv("MANUAL_ROUND2_ROOT")))
        val output = Path.of(requireNotNull(System.getenv("MANUAL_ROUND2_OUTPUT"))).resolve("seed-$seedIndex-seat-$candidateSeat").resolve(arm)
        Files.createDirectories(output)
        check(!Files.exists(output.resolve("actions.jsonl"))) { "Refusing to overwrite a prior game" }
        val json = Json { serializersModule = engineSerializersModule; allowStructuredMapKeys = true }
        fun write(name: String, value: JsonElement) = Files.writeString(output.resolve(name), value.toString())
        var state: GameState? = null
        var players = emptyList<EntityId>()
        var submitted = 0
        var accepted = 0
        var error: String? = null
        var status = "PRE_INITIALIZATION"
        val casts = linkedMapOf<String, Int>()
        val turnsByPlayer = linkedMapOf<String, Int>()
        val started = System.nanoTime()
        val seed = 202610190801L + seedIndex
        val firstCommanderTurn = linkedMapOf<String, Int>()
        val firstCommanderPersonalTurn = linkedMapOf<String, Int>()
        val offered = linkedMapOf<String, Int>()
        val declined = linkedMapOf<String, Int>()
        val legalOffered = linkedMapOf<String, Int>()
        val legalDeclined = linkedMapOf<String, Int>()
        var scoredChoiceRecords = 0
        var comboRelevantActions = 0
        val candidateProfile = AiProfile.CURRENT.copy(id = "manual-animar-development-r1", advisorModules = listOf(ManualAnimarDevelopmentAdvisorModule))
        val actions = Files.newBufferedWriter(output.resolve("actions.jsonl"))
        val insights = Files.newBufferedWriter(output.resolve("decisions.jsonl"))
        try {
            val registry = CardRegistry()
            MtgSetCatalog.all.forEach { set ->
                registry.register(set.cards)
                registry.register(set.basicLands)
            }
            val deckPath = root.resolve("experiments/manual-transmission/control/v0.7.decklist.txt")
            val bytes = Files.readAllBytes(deckPath)
            val original = bytes.toString(Charsets.UTF_8).lineSequence().mapNotNull { line ->
                Regex("^(\\d+) (.+)$").matchEntire(line)?.let { m ->
                    List(m.groupValues[1].toInt()) { m.groupValues[2] }
                }
            }.flatten().toList()
            val names = original.map(ManualMirrorInput::resolve)
            val commander = "Animar, Soul of Elements"
            val banned = Files.readAllLines(root.resolve("tools/manual-mirror/commander-banned-20261019.txt")).toSet()
            val issues = mutableListOf<String>()
            if (names.size != 100 || names.count { it == commander } != 1) issues += "Expected 100 cards including exactly one Animar"
            val commanderDef = registry.getCard(commander)
            if (commanderDef == null || !CommanderEligibility.isLegalCommander(commanderDef)) issues += "Commander unavailable or ineligible"
            val rows = names.distinct().map { name ->
                val card = registry.getCard(name)
                if (card == null) issues += "Missing compiled identity: $name"
                if (card != null && commanderDef != null && !commanderDef.colorIdentity.containsAll(card.colorIdentity)) issues += "Color identity: $name"
                if (name in banned) issues += "Commander banned: $name"
                if (card != null && card.legalFormats.isNotEmpty() && DeckFormat.COMMANDER !in card.legalFormats) issues += "Compiled format legality: $name"
                if (card != null && !card.typeLine.isBasicLand && names.count { it == name } != 1) issues += "Singleton: $name"
                buildJsonObject {
                    put("name", name); put("quantity", names.count { it == name }); put("registered", card != null)
                    put("colorIdentity", card?.colorIdentity.toString())
                    put("legalFormats", card?.legalFormats.toString())
                    put("legalityMetadataPresent", card?.legalFormats?.isNotEmpty() == true)
                }
            }
            write("registry-audit.json", buildJsonObject {
                put("deckSha256", MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) })
                put("cardCount", names.size); put("cards", JsonArray(rows))
                put("issues", JsonArray(issues.map(::JsonPrimitive)))
                put("banSource", "https://magic.wizards.com/en/banned-restricted-list")
            })
            check(issues.isEmpty()) { issues.joinToString("; ") }
            val deck = Deck(names.filter { it != commander }, commander = commander)
            check(deck.cards.size == 99)
            val init = GameInitializer(registry).initializeGame(GameConfig(
                players = (0..3).map { seat -> PlayerConfig("Animar-$seat", deck,
                    startingLife = 40, playerId = EntityId("manual-mirror-$seat"), commanderCardName = commander) },
                format = Format.Commander(), seed = seed, startingPlayerIndex = 0,
                skipMulligans = true, useHandSmoother = false,
            ))
            players = init.playerIds
            state = init.state
            check(players.size == 4 && init.state.turnOrder.size == 4)
            players.forEach { id ->
                val command = init.state.getZone(ZoneKey(id, Zone.COMMAND))
                check(command.size == 1)
                check(init.state.getEntity(command.single())?.get<CardComponent>()?.name == commander)
                check(init.state.getEntity(id)?.get<CommanderRegistryComponent>()?.commanderIds?.toSet() == command.toSet())
                check(init.state.getZone(ZoneKey(id, Zone.LIBRARY)).size + init.state.getZone(ZoneKey(id, Zone.HAND)).size == 99)
                check(init.state.lifeTotal(id) == 40)
                casts[id.value] = 0
                offered[id.value] = 0; declined[id.value] = 0
                legalOffered[id.value] = 0; legalDeclined[id.value] = 0
                turnsByPlayer[id.value] = 0
            }
            Files.writeString(output.resolve("initial-state.json"), json.encodeToString(init.state))
            write("initialization.json", buildJsonObject {
                put("seed", seed); put("arm", arm); put("candidateSeat", candidateSeat); put("profile", if (arm == "candidate") candidateProfile.id else AiProfile.CURRENT.id); put("players", JsonArray(players.map { JsonPrimitive(it.value) }))
                put("commanderPerSeat", commander); put("libraryBeforeDraw", 99); put("startingLife", 40)
                put("openingPolicy", "All four players keep their seeded opening seven; no hand smoother")
                put("initialized", true)
            })
            status = "RUNNING"
            val sink = AiInsightSink { _, insight ->
                scoredChoiceRecords++
                val options = insight.options.filter { it.cardName == commander && it.action is CastSpell }
                if (options.isNotEmpty()) {
                    offered[insight.playerId.value] = offered.getValue(insight.playerId.value) + 1
                    if (options.none { it.chosen }) declined[insight.playerId.value] = declined.getValue(insight.playerId.value) + 1
                }
                insights.write(json.encodeToString(insight)); insights.newLine(); insights.flush()
            }
            val ai = players.mapIndexed { seat, id -> id to AIPlayer.create(registry, id,
                if (arm == "candidate" && seat == candidateSeat) candidateProfile else AiProfile.CURRENT,
                insightSink = sink) }.toMap()
            val processor = ActionProcessor(registry)
            val opportunityEnumerator = com.wingedsheep.engine.legalactions.LegalActionEnumerator.create(registry)
            var lastTurn = -1
            var sameTurnActions = 0
            while (true) {
                val current = requireNotNull(state)
                if (current.gameOver) { status = "COMPLETE"; break }
                if (submitted >= 30000 || current.turnNumber > 400 || sameTurnActions > 3000 ||
                    (System.nanoTime() - started) / 1_000_000 > 1_200_000) {
                    status = "RESOURCE_CAP"; error = "Bound reached before terminal game state"; break
                }
                if (lastTurn != current.turnNumber) {
                    lastTurn = current.turnNumber; sameTurnActions = 0
                    current.activePlayerId?.let { id -> turnsByPlayer[id.value] = turnsByPlayer.getValue(id.value) + 1 }
                }
                val decision = current.pendingDecision
                val actor = decision?.playerId ?: requireNotNull(current.priorityPlayerId) { "No pending decision or priority player" }
                val action: GameAction = if (decision != null) SubmitDecision(actor, ai.getValue(actor).respondToDecision(current, decision))
                    else ai.getValue(actor).chooseAction(current)
                if (decision == null && current.activePlayerId == actor && current.phase.isMainPhase &&
                    current.getZone(ZoneKey(actor, Zone.COMMAND)).isNotEmpty()) {
                    val commandIds = current.getZone(ZoneKey(actor, Zone.COMMAND)).toSet()
                    val available = opportunityEnumerator.enumerate(current, actor,
                        com.wingedsheep.engine.legalactions.EnumerationMode.ACTIONS_ONLY).any {
                        it.affordable && (it.action as? CastSpell)?.cardId in commandIds
                    }
                    if (available) {
                        legalOffered[actor.value] = legalOffered.getValue(actor.value) + 1
                        if (action !is CastSpell || action.cardId !in commandIds)
                            legalDeclined[actor.value] = legalDeclined.getValue(actor.value) + 1
                    }
                }
                submitted++; sameTurnActions++
                val source = when (action) { is CastSpell -> action.cardId; is ActivateAbility -> action.sourceId; else -> null }
                val sourceName = source?.let { current.getEntity(it)?.get<CardComponent>()?.name }
                val comboRelevant = sourceName in setOf(commander, "Ancestral Statue", "Walking Ballista", "Purphoros, God of the Forge")
                if (comboRelevant) comboRelevantActions++
                actions.write(buildJsonObject {
                    put("comboRelevantAction", comboRelevant); put("record", "submit"); put("index", submitted); put("turn", current.turnNumber)
                    put("actor", actor.value); put("step", current.step.name); put("sourceName", sourceName)
                    put("pendingDecision", decision?.toString()); put("action", json.encodeToString<GameAction>(action))
                }.toString()); actions.newLine(); actions.flush()
                val result = processor.process(current, action).result
                result.events.filterIsInstance<SpellCastEvent>().forEach { event ->
                    if (event.cardName == commander) {
                        casts[event.casterId.value] = casts.getValue(event.casterId.value) + 1
                        firstCommanderTurn.putIfAbsent(event.casterId.value, current.turnNumber)
                        firstCommanderPersonalTurn.putIfAbsent(event.casterId.value, turnsByPlayer.getValue(event.casterId.value))
                    }
                }
                actions.write(buildJsonObject {
                    put("record", "result"); put("index", submitted); put("error", result.error?.toString())
                    put("events", JsonArray(result.events.map { JsonPrimitive(it.toString()) }))
                    put("life", JsonArray(players.map { JsonPrimitive(result.state.lifeTotal(it)) }))
                    put("gameOver", result.state.gameOver); put("winner", result.state.winnerId?.value)
                }.toString()); actions.newLine(); actions.flush()
                state = result.state
                if (result.error != null) { status = "ENGINE_REJECTION"; error = "$sourceName: ${result.error}"; break }
                accepted++
                if (accepted == 64) Files.writeString(output.resolve("smoke-passed.txt"), "64 accepted real engine submissions in the same mirror game\n")
            }
        } catch (failure: Throwable) {
            status = "EXCEPTION"
            error = "${failure::class.qualifiedName}: ${failure.message}"
            Files.writeString(output.resolve("exception.txt"), failure.stackTraceToString())
        } finally {
            actions.close(); insights.close()
            val final = state
            if (final != null) runCatching { Files.writeString(output.resolve("final-state.json"), json.encodeToString(final)) }
                .onFailure { Files.writeString(output.resolve("snapshot-error.txt"), it.stackTraceToString()) }
            val summary = buildJsonObject {
                put("status", status); put("seed", seed); put("arm", arm); put("candidateSeat", candidateSeat)
                put("candidatePlayer", players.getOrNull(candidateSeat)?.value)
                put("profilesBySeat", JsonArray((0..3).map { JsonPrimitive(if (arm == "candidate" && it == candidateSeat) candidateProfile.id else AiProfile.CURRENT.id) }))
                put("scoredChoiceRecords", scoredChoiceRecords); put("comboRelevantActionCount", comboRelevantActions)
                put("firstCommanderGlobalTurn", buildJsonObject { firstCommanderTurn.forEach { (id, n) -> put(id, n) } })
                put("firstCommanderPersonalTurn", buildJsonObject { firstCommanderPersonalTurn.forEach { (id, n) -> put(id, n) } })
                put("scoredCommanderOpportunityDecisions", buildJsonObject { offered.forEach { (id, n) -> put(id, n) } })
                put("scoredCommanderDeclinedDecisions", buildJsonObject { declined.forEach { (id, n) -> put(id, n) } })
                put("legalCommanderOpportunityDecisions", buildJsonObject { legalOffered.forEach { (id, n) -> put(id, n) } })
                put("legalCommanderDeclinedDecisions", buildJsonObject { legalDeclined.forEach { (id, n) -> put(id, n) } })
                put("opportunityDefinition", "Insight records containing a materialized Animar CastSpell option; repeated priority windows count separately; excludes unmaterialized legal opportunities")
                put("submitted", submitted); put("accepted", accepted); put("error", error)
                put("gameOver", final?.gameOver == true); put("winner", final?.winnerId?.value)
                put("playerTurnNumber", final?.turnNumber); put("durationMs", (System.nanoTime() - started) / 1_000_000)
                put("turnsByPlayer", buildJsonObject { turnsByPlayer.forEach { (id, n) -> put(id, n) } })
                put("commanderCasts", buildJsonObject { casts.forEach { (id, n) -> put(id, n) } })
                put("life", buildJsonObject { players.forEach { id -> put(id.value, final?.lifeTotal(id)) } })
                put("comboAttempts", "Not automatically inferred; inspect complete action and decision streams")
            }
            write("summary.json", summary)
            println("MANUAL_ROUND2_RESULT=$summary")
        }
        return status == "COMPLETE" && state?.gameOver == true
        }
        // Preserve both arms, including failures, before asserting this matched cell completed.
        val controlComplete = runArm("control")
        val candidateComplete = runArm("candidate")
        check(controlComplete && candidateComplete) { "Matched cell incomplete; inspect both preserved summary.json files" }
    }
})
