package com.wingedsheep.ai.engine

import com.wingedsheep.ai.insight.AiInsightSink
import com.wingedsheep.ai.engine.advisor.modules.ManualAnimarDevelopmentAdvisorModule
import com.wingedsheep.ai.engine.advisor.modules.ManualStatueContinuationAdvisorModule
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
class ManualR22MatchedWanGameplayHarnessTest : FunSpec({
    test("authorized matched-slot R5-policy four-player observation") {
        check(System.getenv("MANUAL_R22_GAMEPLAY_AUTHORIZATION") == "EXPLICIT_FOUR_GAME") {
            "R22 64-action smoke and full gameplay require separate explicit authorization"
        }
        val seed = requireNotNull(System.getenv("MANUAL_R22_SEED")).toLong()
        val pinned = requireNotNull(System.getenv("MANUAL_R22_PREDECLARED_SEED"))
        check(seed.toString() == pinned && seed in setOf(202610191621L, 202610191622L)) {
            "R22 refuses unknown, historical, substituted or unbound seeds"
        }
        val arm = requireNotNull(System.getenv("MANUAL_R22_ARM"))
        check(arm in setOf("control", "challenger"))
        val mode = requireNotNull(System.getenv("MANUAL_R22_PREFIX_ONLY")) { "Specify 1 for smoke or 0 for full" }
        check(mode == "1" || mode == "0")
        val prefixOnly = mode == "1"
        val selectedProfile = ManualTransmissionR5Candidate.profile
        check(selectedProfile.id == "manual-transmission-r5-survival-opt-in")
        val r4Owner = com.wingedsheep.ai.engine.advisor.modules.ManualBallistaContinuationAdvisorModule
        val r7Owner = com.wingedsheep.ai.engine.advisor.modules.ManualR7BallistaAllocationAdvisorModule
        val pact = com.wingedsheep.ai.engine.advisor.modules.ManualPactDebtAdvisorModule
        check(pact in selectedProfile.advisorModules && r4Owner in selectedProfile.advisorModules)
        check(r7Owner !in selectedProfile.advisorModules)
        check(AiProfile.CURRENT.advisorModules.isEmpty())
        fun runArm(): Boolean {
        val root = Path.of(requireNotNull(System.getenv("MANUAL_R4_ROOT")))
        val output = Path.of(requireNotNull(System.getenv("MANUAL_R22_OUTPUT"))).resolve("$arm-seed-$seed" + if (prefixOnly) "-prefix" else "")
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
                val firstCommanderTurn = linkedMapOf<String, Int>()
        val firstCommanderPersonalTurn = linkedMapOf<String, Int>()
        val offered = linkedMapOf<String, Int>()
        val declined = linkedMapOf<String, Int>()
        val legalOffered = linkedMapOf<String, Int>()
        val legalDeclined = linkedMapOf<String, Int>()
        var scoredChoiceRecords = 0
        var comboRelevantActions = 0
        val wanCastXs = mutableListOf<Int>()
        val wanCastActionIndices = mutableListOf<Int>()
        val wanRelatedEventRows = mutableListOf<JsonElement>()
        var searchesWithWanAlreadyVisible = 0
        var observedFailedDraws = 0
        fun profile(seat: Int) = selectedProfile
        val actions = Files.newBufferedWriter(output.resolve("actions.jsonl"))
        val insights = Files.newBufferedWriter(output.resolve("decisions.jsonl"))
        try {
            val registry = CardRegistry()
            MtgSetCatalog.all.forEach { set ->
                registry.register(set.cards)
                registry.register(set.basicLands)
            }
            val controlPath = root.resolve("experiments/manual-transmission/control/v0.7.decklist.txt")
            val publishedWanPath = root.resolve("gameplay/manual-v08-wan/CHALLENGER.decklist.txt")
            fun deckNames(path: Path): List<String> =
                Files.readAllLines(path).flatMap { line ->
                    Regex("^(\\d+) (.+)$").matchEntire(line)?.let { m ->
                        List(m.groupValues[1].toInt()) { m.groupValues[2] }
                    } ?: emptyList()
                }
            val baselineCards = deckNames(controlPath)
            val publishedWanCards = deckNames(publishedWanPath)
            val matchedWanCards = ManualR19MatchedSlotHelper.matchedChallengerNames(
                baselineCards, "Lifecrafter’s Bestiary", "Wan Shi Tong, Librarian"
            )
            check(baselineCards.size == 100 && publishedWanCards.size == 100)
            check(publishedWanCards.sorted() == matchedWanCards.sorted())
            val replacedIndex = baselineCards.indexOf("Lifecrafter’s Bestiary")
            check(replacedIndex >= 0 && matchedWanCards[replacedIndex] == "Wan Shi Tong, Librarian")
            check(baselineCards.indices.count { baselineCards[it] != matchedWanCards[it] } == 1)
            check(baselineCards.indices.count { baselineCards[it] != publishedWanCards[it] } == 6)
            // This arm is created IN MEMORY, never read from the section-ordered R18 file.
            val selectedRawNames = if (arm == "challenger") matchedWanCards else baselineCards
            val bytes = if (arm == "control") Files.readAllBytes(controlPath) else
                selectedRawNames.joinToString(" | ", prefix = "MATCHED_SLOT_R22 | ")
                    .toByteArray(Charsets.UTF_8)
            val names = selectedRawNames.map(ManualMirrorInput::resolve)
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
                put("deckSha256Meaning", if (arm == "control") "exact frozen source bytes" else
                    "in-memory matched-slot selected-name serialization, NOT the published R18 deck")
                put("matchedInputIndex", replacedIndex)
                put("matchedInputConstruction", "ManualR19MatchedSlotHelper")
                put("issues", JsonArray(issues.map(::JsonPrimitive)))
                put("banSource", "https://magic.wizards.com/en/banned-restricted-list")
            })
            check(issues.isEmpty()) { issues.joinToString("; ") }
            // Always initialize BOTH arms from the same seed and immutable deck source.
            // Do not permit a future gameplay run until all four real shuffled positions match.
            fun initialize(raw: List<String>): InitializationResult {
                val resolved = raw.map(ManualMirrorInput::resolve)
                val deck = Deck(resolved.filter { it != commander }, commander = commander)
                check(deck.cards.size == 99)
                return GameInitializer(registry).initializeGame(GameConfig(
                    players = (0..3).map { seat -> PlayerConfig("Animar-$seat", deck,
                        startingLife = 40, playerId = EntityId("manual-mirror-$seat"),
                        commanderCardName = commander) },
                    format = Format.Commander(), seed = seed, startingPlayerIndex = 0,
                    skipMulligans = true, useHandSmoother = false,
                ))
            }
            check(names.size == 100)
            val controlInit = initialize(baselineCards)
            val matchedInit = initialize(matchedWanCards)
            check(controlInit.playerIds == matchedInit.playerIds)
            check(controlInit.state.turnOrder == matchedInit.state.turnOrder)
            val canonicalBestiary = ManualMirrorInput.resolve("Lifecrafter’s Bestiary")
            val canonicalWan = ManualMirrorInput.resolve("Wan Shi Tong, Librarian")
            val preflightRows = controlInit.playerIds.mapIndexed { seat, id ->
                fun zoneNames(st: GameState, zone: Zone): List<String> =
                    st.getZone(ZoneKey(id, zone)).map { entity ->
                        st.getEntity(entity)?.get<CardComponent>()?.name
                            ?: error("Missing R22 card identity in $zone at seat $seat")
                    }
                val cLib = zoneNames(controlInit.state, Zone.LIBRARY)
                val mLib = zoneNames(matchedInit.state, Zone.LIBRARY)
                val cHand = zoneNames(controlInit.state, Zone.HAND)
                val mHand = zoneNames(matchedInit.state, Zone.HAND)
                check(cLib.size == 92 && mLib.size == 92 && cHand.size == 7 && mHand.size == 7)
                check(controlInit.state.getZone(ZoneKey(id, Zone.LIBRARY)) ==
                    matchedInit.state.getZone(ZoneKey(id, Zone.LIBRARY)))
                check(controlInit.state.getZone(ZoneKey(id, Zone.HAND)) ==
                    matchedInit.state.getZone(ZoneKey(id, Zone.HAND)))
                check(zoneNames(controlInit.state, Zone.COMMAND) == listOf(commander))
                check(zoneNames(matchedInit.state, Zone.COMMAND) == listOf(commander))
                val c = cLib + cHand
                val m = mLib + mHand
                check(c.size == 99 && m.size == 99)
                check(c.count { it == canonicalBestiary } == 1 && m.count { it == canonicalWan } == 1)
                val different = c.indices.filter { c[it] != m[it] }
                check(different.size == 1)
                check(c.filter { it != canonicalBestiary } == m.filter { it != canonicalWan })
                check(c.filter { it != canonicalBestiary }.size == 98)
                val pos = different.single()
                val replacementZone = if (pos < cLib.size) "LIBRARY" else "HAND"
                val zoneIndex = if (pos < cLib.size) pos else pos - cLib.size
                buildJsonObject {
                    put("seat", seat); put("playerId", id.value)
                    put("sharedLibraryCardsAligned", 98); put("oneReplacement", true)
                    put("replacementZone", replacementZone); put("replacementZoneIndex", zoneIndex)
                    put("matchedEntityIdSlots", true)
                }
            }
            write("r22-initializer-preflight.json", buildJsonObject {
                put("seed", seed); put("positiveSeats", preflightRows.size)
                put("controlInputDifferingSlots", 1); put("r18InputDifferingSlots", 6)
                put("sharedLibraryCardsPerSeat", 98); put("passed", true)
                put("method", "ManualR19MatchedSlotHelper + real GameInitializer")
                put("seats", JsonArray(preflightRows))
            })
            val init = if (arm == "challenger") matchedInit else controlInit
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
                put("seed", seed); put("profile", profile(0).id); put("arm", arm); put("prefixOnly", prefixOnly)
                put("players", JsonArray(players.map { JsonPrimitive(it.value) }))
                put("commanderPerSeat", commander); put("libraryBeforeDraw", 99); put("startingLife", 40)
                put("openingPolicy", "All four players keep their seeded opening seven; no hand smoother")
                put("initialized", true); put("matchedSlotInitializerPreflight", true)
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
                profile(seat),
                insightSink = sink) }.toMap()
            val processor = ActionProcessor(registry)
            val opportunityEnumerator = com.wingedsheep.engine.legalactions.LegalActionEnumerator.create(registry)
            var lastTurn = -1
            var sameTurnActions = 0
            while (true) {
                val current = requireNotNull(state)
                if (current.gameOver) { status = "COMPLETE"; break }
                val elapsedMs = (System.nanoTime() - started) / 1_000_000
                val reason = ManualR9CensorProtocol.reached(
                    elapsedMs = elapsedMs,
                    submitted = submitted,
                    globalTurn = current.turnNumber,
                    sameTurnActions = sameTurnActions,
                )
                if (reason != null) {
                    status = reason.status
                    error = null // resource censoring is observational, never a gameplay error or a loss
                    ManualR9CensorProtocol.writeCensorCheckpoint(output, current, json)
                    break
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
                if (action is CastSpell && sourceName == "Wan Shi Tong, Librarian") {
                    wanCastXs.add(action.xValue ?: 0)
                    wanCastActionIndices.add(submitted)
                }
                val wanVisibleBefore = current.getBattlefield().any { entity ->
                    current.getEntity(entity)?.get<CardComponent>()?.name == "Wan Shi Tong, Librarian"
                }
                actions.write(buildJsonObject {
                    put("comboRelevantAction", comboRelevant); put("record", "submit"); put("index", submitted); put("turn", current.turnNumber)
                    put("actor", actor.value); put("step", current.step.name); put("sourceName", sourceName)
                    put("pendingDecision", decision?.toString()); put("decisionSource", decision?.context?.sourceName)
                    val selection = (action as? SubmitDecision)?.response as? CardsSelectedResponse
                    put("selectedCardNames", JsonArray(if (decision?.context?.sourceName == "Ancestral Statue")
                        selection?.selectedCards.orEmpty().map { JsonPrimitive(current.getEntity(it)?.get<CardComponent>()?.name ?: it.value) }
                        else emptyList()))
                    put("action", json.encodeToString<GameAction>(action))
                }.toString()); actions.newLine(); actions.flush()
                val result = processor.process(current, action).result
                result.events.filterIsInstance<SpellCastEvent>().forEach { event ->
                    if (event.cardName == commander) {
                        casts[event.casterId.value] = casts.getValue(event.casterId.value) + 1
                        firstCommanderTurn.putIfAbsent(event.casterId.value, current.turnNumber)
                        firstCommanderPersonalTurn.putIfAbsent(event.casterId.value, turnsByPlayer.getValue(event.casterId.value))
                    }
                }
                val relatedEvents = result.events.map { it.toString() }.filter {
                    "Wan Shi Tong" in it || "LibrarySearchedEvent" in it || "DrawFailedEvent" in it
                }
                if (relatedEvents.isNotEmpty() || sourceName == "Wan Shi Tong, Librarian") {
                    wanRelatedEventRows += buildJsonObject {
                        put("index", submitted)
                        put("sourceName", sourceName)
                        put("wanVisibleBeforeAction", wanVisibleBefore)
                        put("events", JsonArray(relatedEvents.map(::JsonPrimitive)))
                    }
                }
                if (wanVisibleBefore) {
                    searchesWithWanAlreadyVisible += result.events.count {
                        "LibrarySearchedEvent" in it.toString()
                    }
                }
                observedFailedDraws += result.events.count {
                    "DrawFailedEvent" in it.toString()
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
                if (accepted == 64) {
                    Files.writeString(output.resolve("smoke-passed.txt"), "64 accepted real engine submissions in the same mirror game\n")
                    if (prefixOnly) { status = "PREFIX_COMPLETE"; break }
                }
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
                put("status", status); put("seed", seed); put("profile", profile(0).id); put("arm", arm); put("prefixOnly", prefixOnly)
                put("profilesBySeat", JsonArray((0..3).map { JsonPrimitive(profile(it).id) }))
                put("scoredChoiceRecords", scoredChoiceRecords); put("comboRelevantActionCount", comboRelevantActions)
                put("firstCommanderGlobalTurn", buildJsonObject { firstCommanderTurn.forEach { (id, n) -> put(id, n) } })
                put("firstCommanderPersonalTurn", buildJsonObject { firstCommanderPersonalTurn.forEach { (id, n) -> put(id, n) } })
                put("scoredCommanderOpportunityDecisions", buildJsonObject { offered.forEach { (id, n) -> put(id, n) } })
                put("scoredCommanderDeclinedDecisions", buildJsonObject { declined.forEach { (id, n) -> put(id, n) } })
                put("legalCommanderOpportunityDecisions", buildJsonObject { legalOffered.forEach { (id, n) -> put(id, n) } })
                put("legalCommanderDeclinedDecisions", buildJsonObject { legalDeclined.forEach { (id, n) -> put(id, n) } })
                put("opportunityDefinition", "Insight records containing a materialized Animar CastSpell option; repeated priority windows count separately; excludes unmaterialized legal opportunities")
                put("submitted", submitted); put("accepted", accepted); put("error", error)
                put("censorReason", status.takeIf(ManualR9CensorProtocol::isCensored))
                put("comparableHorizonActions", accepted)
                put("wallClockMs", (System.nanoTime() - started) / 1_000_000)
                put("censorCheckpointSaved", ManualR9CensorProtocol.isCensored(status) &&
                    Files.exists(output.resolve("censor-checkpoint.json")))
                put("observationMeaning", if (ManualR9CensorProtocol.isCensored(status))
                    "Nonterminal censor; ineligible for winning-side comparison" else status)
                put("gameOver", final?.gameOver == true); put("winner", final?.winnerId?.value)
                put("playerTurnNumber", final?.turnNumber); put("durationMs", (System.nanoTime() - started) / 1_000_000)
                put("turnsByPlayer", buildJsonObject { turnsByPlayer.forEach { (id, n) -> put(id, n) } })
                put("commanderCasts", buildJsonObject { casts.forEach { (id, n) -> put(id, n) } })
                put("life", buildJsonObject { players.forEach { id -> put(id.value, final?.lifeTotal(id)) } })
                put("wanCastCount", wanCastActionIndices.size)
                put("wanCastActionIndices", JsonArray(wanCastActionIndices.map(::JsonPrimitive)))
                put("wanCastXs", JsonArray(wanCastXs.map(::JsonPrimitive)))
                put("searchesWithWanAlreadyVisible", searchesWithWanAlreadyVisible)
                put("observedFailedDraws", observedFailedDraws)
                put("wanAttributionNote", "Raw events preserved; do not infer ETB draws from unrelated draw effects")
                put("comboAttempts", "Not automatically inferred; inspect complete action and decision streams")
            }
            write("summary.json", summary)
            write("wan-event-candidates.json", JsonArray(wanRelatedEventRows))
            write("r22-arm-binding.json", buildJsonObject {
                put("seed", seed); put("arm", arm); put("profile", selectedProfile.id)
                put("deckPath", if (arm == "challenger")
                    "IN_MEMORY: ManualR19MatchedSlotHelper replaces Bestiary at frozen control index"
                    else "experiments/manual-transmission/control/v0.7.decklist.txt")
                put("oneCardSwapVerified", true)
                put("matchedSlotPreflightVerified", Files.exists(output.resolve("r22-initializer-preflight.json")))
                put("r21AcceptedRun", 39429315945L)
                put("r7CombinedAdvisorAbsent", true)
                put("automaticAdoption", false)
            })
            println("MANUAL_R22_WAN_RESULT=" + summary)
        }
        return ManualR9CensorProtocol.validObservation(
            status = status,
            accepted = accepted,
            submitted = submitted,
            error = error,
            gameOver = state?.gameOver == true,
            winner = state?.winnerId?.value,
            prefixOnly = prefixOnly,
        )
        }
        // One separately authorized bounded arm only; preserve incomplete prefixes honestly.
        check(runArm()) { "R22 observation invalid: engine error, invalid terminal/censor status, failed prefix or insufficient actions" }
    }
})
