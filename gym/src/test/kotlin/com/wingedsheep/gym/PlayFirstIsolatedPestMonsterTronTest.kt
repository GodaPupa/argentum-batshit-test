package com.wingedsheep.gym

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.EngineAiPlayerController
import com.wingedsheep.ai.engine.PestMonsterTronPolicy
import com.wingedsheep.ai.llm.BottomCardsInfo
import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption.APPEND
import java.nio.file.StandardOpenOption.CREATE_NEW
import kotlin.time.Duration.Companion.minutes
import java.security.MessageDigest


/** Prepared adapter only. No fixed seed or sample, no B01/G08 resume path.
 * A future owner-approved case record and exact workflow activation are required to opt in.
 * These local checks bind supplied inputs; they do not authenticate a remote owner or prove
 * global exclusion completeness. One JVM case, same engine/decks/pilots as frozen B01.
 */
class PlayFirstIsolatedPestMonsterTronTest : FunSpec({
    val acknowledge = System.getenv("PLAY_FIRST_ISOLATED_ACK") == "NEW_NONOFFICIAL_ORIGINAL_ONLY"
    test("one separately authorized isolated exhibition case").config(enabled = acknowledge, timeout = 45.minutes) {
        check(System.getenv("GITHUB_RUN_ATTEMPT") == "1") { "Original attempt only" }
        val sourceSha = System.getenv("PLAY_FIRST_ISOLATED_SOURCE_SHA") ?: error("Missing exact source")
        require(sourceSha.matches(Regex("[0-9a-f]{40}")))
        val casePath = Path.of(System.getenv("PLAY_FIRST_ISOLATED_CASE_FILE") ?: error("No approved case supplied"))
        val bytes = Files.readAllBytes(casePath)
        val caseDigest = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        check(caseDigest == System.getenv("PLAY_FIRST_ISOLATED_CASE_SHA256")) { "Case bytes do not match pinned digest" }
        val input = Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)).jsonObject
        require(input.keys == setOf("blockId", "gameId", "pair", "pestSeat", "fixtureSeedHex", "authorizationComment",
            "sourceCommit", "scope", "executionAuthorized", "intendedBlockGames"))
        check(input.getValue("scope").jsonPrimitive.content == "NONOFFICIAL_PLAY_FIRST_EXHIBITION_ONLY")
        check(input.getValue("executionAuthorized").jsonPrimitive.boolean)
        check(input.getValue("sourceCommit").jsonPrimitive.content == sourceSha)
        val blockId = input.getValue("blockId").jsonPrimitive.content
        val gameId = input.getValue("gameId").jsonPrimitive.content
        require(blockId.matches(Regex("PLAY_FIRST_[A-Z0-9_]{1,80}")) && !blockId.contains("B01"))
        require(gameId.matches(Regex("G[0-9]{2,4}")))
        val pair = input.getValue("pair").jsonPrimitive.int; require(pair > 0)
        val pestSeat = input.getValue("pestSeat").jsonPrimitive.int; require(pestSeat in 0..1)
        val intendedBlockGames = input.getValue("intendedBlockGames").jsonPrimitive.int; require(intendedBlockGames in 1..100)
        val authorizationComment = input.getValue("authorizationComment").jsonPrimitive.long
        require(authorizationComment > 0 && authorizationComment !in setOf(6081552107L, 6084057209L)) {
            "B01 and construction-only authority cannot authorize a new game"
        }
        val seedHex = input.getValue("fixtureSeedHex").jsonPrimitive.content
        require(seedHex.matches(Regex("0x[0-7][0-9a-f]{15}")))
        val seed = seedHex.substring(2).toLong(16)
        require(seed != 0L && seed !in setOf(0x504C415946495253L, 0x504C4159424C3031L,
            0x504C4159424C3032L, 0x504C4159424C3033L, 0x504C4159424C3034L)) { "Prior exhibition originals cannot be replayed" }
        val root = Path.of(System.getenv("PLAY_FIRST_ISOLATED_EVIDENCE_ROOT") ?: error("Missing case root"))
            .toAbsolutePath().normalize()
        require(Files.isDirectory(root) && !Files.isSymbolicLink(root))
        val trace = root.resolve("original.jsonl")
        require(!Files.exists(trace)) { "Original case slot is already consumed" }
        PlayFirstTimingJournal(root.resolve("timing.jsonl"), mapOf("blockId" to blockId,
            "gameId" to gameId, "sourceCommit" to sourceSha, "caseInputSha256" to caseDigest,
            "authorizationComment" to authorizationComment.toString())).use { timing ->
            val deckNames = if (pestSeat == 0) listOf("Pest Control v1.0", "mehanske Monster Tron")
                else listOf("mehanske Monster Tron", "Pest Control v1.0")
            val json = Json {
                serializersModule = engineSerializersModule
                encodeDefaults = true
                allowStructuredMapKeys = true
            }
            fun append(record: JsonObject) { Files.writeString(trace, record.toString() + "\n", APPEND) }
            Files.writeString(trace, buildJsonObject {
                put("recordType", "EXHIBITION_INTENT")
                put("blockId", blockId)
                put("authorizationComment", authorizationComment)
                put("gameId", gameId)
                put("pair", pair)
                put("pestSeat", pestSeat)
                put("startingSeat", 0)
                put("seat0Deck", deckNames[0])
                put("seat1Deck", deckNames[1])
                put("fixtureSeedHex", "0x" + seed.toString(16))
                put("scope", "NONOFFICIAL_PLAY_FIRST_EXHIBITION_ONLY")
                put("format", "PAUPER_60_CARD_PREBOARD_HEADS_UP")
                put("experimentalResult", false)
                put("officialExecutionAuthorized", false)
                put("formalSeedAllocation", false)
                put("sourceCommit", sourceSha)
                put("sourceParent", "80c6774d0362521ad438e8a23d5fa7e0701ad468")
                put("baselineMainCommit", "4816825fb6a4d6535f0365554d204bc964b7ad73")
                put("pestMainSha256", PEST_CONTROL_V10_HASH)
                put("monsterTronMainSha256", PEST_MONSTER_TRON_MAIN_SHA256)
                put("intendedBlockGames", intendedBlockGames)
                put("caseInputSha256", caseDigest)
                put("executionDesign", "ISOLATED_CASE_V1_TIMING_ONLY_NO_POLICY_CHANGE")
                put("intendedGamesThisCase", 1)
                put("maxActions", 12000)
                put("maxTurns", 60)
                put("maxActionsPerTurn", 500)
            }.toString() + "\n", CREATE_NEW)

            var actionCount = 0
            try {
                val registry = timing.measure("REGISTRY_CREATE") { CardRegistry().apply {
                    register(PredefinedTokens.allTokens)
                    MtgSetCatalog.all.forEach { set -> register(set.cards); register(set.basicLands) }
                } }
                PestControlPreboardDecks.verifyFrozenIdentities()
                check(PestControlTierOneMonsterTronAdmission.validationErrors(TierOneMonsterTronAdmission()).isEmpty())
                check(PestControlTierOneMonsterTronAdmission.unresolvedMain(registry).isEmpty())
                check(PestControlPreboardDecks.pestMainCounts.values.sum() == 60)
                check(PestControlTierOneMonsterTronAdmission.mainCounts.values.sum() == 60)
                val pestConfig = PlayerConfig("Pest Control v1.0", PestControlPreboardDecks.pestMain(), startingLife = 20)
                val tronConfig = PlayerConfig("mehanske Monster Tron", PestControlTierOneMonsterTronReadiness.mainDeck(), startingLife = 20)
                val env = timing.measure("ENVIRONMENT_CREATE") { GameEnvironment.create(registry) }
                timing.measure("INITIALIZE") { env.reset(GameConfig(
                    players = if (pestSeat == 0) listOf(pestConfig, tronConfig) else listOf(tronConfig, pestConfig),
                    skipMulligans = false, useHandSmoother = false, startingPlayerIndex = 0, seed = seed,
                )) }
                val players = env.playerIds
                check(players.size == 2)
                fun handNames(): JsonArray = JsonArray(players.map { player ->
                    JsonArray(env.state.getHand(player).map { id ->
                        JsonPrimitive(env.state.getEntity(id)?.get<CardComponent>()?.name ?: id.value)
                    })
                })
                append(buildJsonObject {
                    put("recordType", "INITIALIZED")
                    put("gameId", gameId)
                    put("player0", players[0].value)
                    put("player1", players[1].value)
                    put("turn", env.turnNumber)
                    put("gameOver", env.state.gameOver)
                    put("handsBySeat", handNames())
                })
                fun summaries(state: GameState, ids: List<EntityId>): Map<EntityId, CardSummary> = ids.associateWith { id ->
                    val card = state.getEntity(id)?.get<CardComponent>()
                    CardSummary(name = card?.name ?: id.value, manaCost = card?.manaCost?.toString(),
                        typeLine = card?.typeLine?.toString(), power = card?.baseStats?.basePower,
                        toughness = card?.baseStats?.baseToughness, oracleText = card?.oracleText)
                }
                fun submit(action: GameAction) {
                    val before = env.state
                    val sequence = ++actionCount
                    append(buildJsonObject {
                        put("recordType", "ACTION_INTENT")
                        put("sequence", sequence)
                        put("turn", before.turnNumber)
                        put("step", before.step.name)
                        put("actor", action.playerId.value)
                        put("pendingDecision", before.pendingDecision?.let { it::class.simpleName } ?: "")
                        put("selectedAction", json.encodeToJsonElement(GameAction.serializer(), action))
                    })
                    val applied = timing.measure("ENGINE_SUBMIT", mapOf("sequence" to sequence.toString(),
                        "actor" to action.playerId.value, "turn" to before.turnNumber.toString(), "step" to before.step.name)) {
                        env.stepExactlyOne(action)
                    }
                    val rejected = applied as? ExactlyOneSubmissionResult.Rejected
                    append(buildJsonObject {
                        put("recordType", "ACTION_RESULT")
                        put("sequence", sequence)
                        put("accepted", applied is ExactlyOneSubmissionResult.Applied)
                        put("rejectionReason", rejected?.reason ?: "")
                        put("emittedEvents", JsonArray(env.lastStepEvents.map { json.encodeToJsonElement(GameEvent.serializer(), it) }))
                        put("turnAfter", env.state.turnNumber)
                        put("gameOverAfter", env.state.gameOver)
                        put("lifeAfter", JsonArray(players.map { JsonPrimitive(env.state.lifeTotal(it)) }))
                    })
                    check(rejected == null) { "Engine rejected $gameId action $sequence: ${rejected?.reason}" }
                    check(applied is ExactlyOneSubmissionResult.Applied) { "Unrecognized engine submission" }
                }
                val mulligans = players.associateWith { player ->
                    EngineAiPlayerController(registry, player, gameStateProvider = { env.state })
                }
                var mulliganTransitions = 0
                for (player in env.state.turnOrder) {
                    val controller = mulligans.getValue(player)
                    while (true) {
                        check(++mulliganTransitions <= 30) { "London mulligan did not settle" }
                        val state = env.state
                        val component = state.getEntity(player)?.get<MulliganStateComponent>() ?: error("Missing mulligan state")
                        if (component.hasKept) break
                        val hand = state.getHand(player)
                        val keep = timing.measure("MULLIGAN_SELECT", mapOf("actor" to player.value,
                            "nextSequence" to (actionCount + 1).toString())) { controller.decideMulligan(MulliganInfo(hand = hand,
                            mulliganCount = component.mulligansTaken, cardsToPutOnBottom = component.cardsToBottom,
                            cards = summaries(state, hand), isOnThePlay = state.turnOrder.first() == player)) }
                        submit(if (keep) KeepHand(player) else TakeMulligan(player))
                    }
                }
                for (player in env.state.turnOrder) {
                    val state = env.state
                    val component = state.getEntity(player)?.get<MulliganStateComponent>() ?: continue
                    if (component.cardsToBottom == 0) continue
                    val hand = state.getHand(player)
                    val bottom = timing.measure("BOTTOM_SELECT", mapOf("actor" to player.value,
                        "nextSequence" to (actionCount + 1).toString())) {
                        mulligans.getValue(player).chooseBottomCards(BottomCardsInfo(hand, component.cardsToBottom, summaries(state, hand)))
                    }
                    submit(BottomCards(player, bottom))
                }
                append(buildJsonObject {
                    put("recordType", "MULLIGAN_COMPLETE")
                    put("gameId", gameId)
                    put("actions", actionCount)
                    put("handsBySeat", handNames())
                })
                val agents = timing.measure("AGENTS_CREATE") { players.mapIndexed { seat, player -> AIPlayer.create(registry, player,
                    if (seat == pestSeat) AiProfile.PRODUCTION_CANDIDATE_EXPIRING else PestMonsterTronPolicy.profile) } }
                var lastTurn = env.turnNumber
                var actionsThisTurn = 0
                while (!env.isTerminal) {
                    check(actionCount < 12000) { "Exhibition reached 12000-action cap" }
                    check(env.turnNumber <= 60) { "Exhibition reached 60-turn cap" }
                    if (env.turnNumber != lastTurn) { lastTurn = env.turnNumber; actionsThisTurn = 0 }
                    check(++actionsThisTurn <= 500) { "Exhibition no-progress per-turn action cap" }
                    val state = env.state
                    val decision = state.pendingDecision
                    val acting = decision?.playerId ?: state.priorityPlayerId ?: error("No actor")
                    val seat = players.indexOf(acting)
                    check(seat >= 0)
                    val phase = if (decision != null) "RESPOND_DECISION" else "CHOOSE_ACTION"
                    val action = timing.measure(phase, mapOf("actor" to acting.value, "seat" to seat.toString(),
                        "nextSequence" to (actionCount + 1).toString(), "turn" to state.turnNumber.toString(),
                        "step" to state.step.name, "pendingDecision" to (decision?.let { it::class.simpleName } ?: ""))) {
                        if (decision != null) SubmitDecision(acting, agents[seat].respondToDecision(state, decision))
                        else agents[seat].chooseAction(state)
                    }
                    submit(action)
                }
                val terminal = env.state
                check(terminal.gameOver) { "No legitimate engine terminal; cannot claim a game" }
                val winnerSeat = terminal.winnerId?.let { players.indexOf(it) } ?: -1
                append(buildJsonObject {
                    put("recordType", "EXHIBITION_TERMINAL")
                    put("gameId", gameId)
                    put("pair", pair)
                    put("pestSeat", pestSeat)
                    put("seat0Deck", deckNames[0])
                    put("seat1Deck", deckNames[1])
                    put("gameOver", terminal.gameOver)
                    put("winnerId", terminal.winnerId?.value ?: "")
                    put("winnerSeat", winnerSeat)
                    put("turn", terminal.turnNumber)
                    put("actions", actionCount)
                    put("lifeBySeat", JsonArray(players.map { JsonPrimitive(terminal.lifeTotal(it)) }))
                    put("scope", "NONOFFICIAL_PLAY_FIRST_EXHIBITION_ONLY")
                    put("formalExperimentCount", 0)
                })
                println("PLAY_FIRST_ISOLATED_RESULT $gameId pestSeat=$pestSeat winnerSeat=$winnerSeat turns=${terminal.turnNumber} actions=$actionCount NONOFFICIAL")
            } catch (failure: Throwable) {
                append(buildJsonObject {
                    put("recordType", "EXHIBITION_FAILED")
                    put("gameId", gameId)
                    put("actionsObserved", actionCount)
                    put("failureClass", failure::class.qualifiedName ?: "Throwable")
                    put("failure", failure.message ?: "unreported")
                    put("notOfficial", true)
                })
                throw failure
            }
        }
    }
})
