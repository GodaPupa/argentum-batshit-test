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

/**
 * ONE NEW, exactly seat-reversed companion to the first NONOFFICIAL head-to-head exhibition.
 *
 * Does NOT import the official Monster Tron execution loader, coordinator, allocation vector,
 * claim/ref/marker, seed registry, frozen R1 run, or any experimental-acceptance path.
 * Not a Commander trial; not formal Pest twelve-game replication; no statistical claim.
 *
 * Exactly one new seat-reversed assignment uses the first exhibition\u0027s deterministic nonofficial fixture seed.\n * This is intentionally paired, not a retry, replacement, or official seed allocation. The original
 * accepted Pest and Monster Tron main decks and existing two AI profiles are read, never edited.
 * Negative/partial outcomes remain evidence; a fault is not repaired into a synthetic winner.
 */
class PlayFirstPestMonsterTronSeatReversalTest : FunSpec({
    val acknowledge = System.getenv("PLAY_FIRST_SEAT_REVERSAL_ACK") ==
        "EXHIBITION_ONLY_SEAT_REVERSED_COMPANION_NO_OFFICIAL_CLAIM_20261009"

    test("one nonofficial seat-reversed Monster Tron versus Pest Control engine game").config(
        enabled = acknowledge,
        timeout = 45.minutes,
    ) {
        val sourceSha = System.getenv("PLAY_FIRST_SEAT_REVERSAL_SOURCE_SHA")
            ?: error("PLAY_FIRST_SEAT_REVERSAL_SOURCE_SHA must bind the source commit")
        require(sourceSha.matches(Regex("[0-9a-f]{40}")))
        val evidenceRoot = System.getenv("PLAY_FIRST_SEAT_REVERSAL_EVIDENCE_ROOT")
            ?: error("PLAY_FIRST_SEAT_REVERSAL_EVIDENCE_ROOT must be set")
        val root = Path.of(evidenceRoot).toAbsolutePath().normalize()
        require(Files.isDirectory(root))
        val trace = root.resolve("exhibition-seat-reversal-original.jsonl")
        require(!Files.exists(trace)) { "One-game exhibition already consumed this output slot" }
        val seed = 0x504C415946495253L  // matched companion: same nonofficial fixture, reversed seats; NOT a retry or allocated official seed
        val json = Json {
            serializersModule = engineSerializersModule
            encodeDefaults = true
            allowStructuredMapKeys = true
        }

        fun append(record: JsonObject) {
            Files.writeString(trace, record.toString() + "\n", APPEND)
        }
        Files.writeString(trace, buildJsonObject {
            put("recordType", "EXHIBITION_INTENT")
            put("scope", "NONOFFICIAL_PLAY_FIRST_EXHIBITION_ONLY")
            put("format", "PAUPER_60_CARD_PREBOARD_HEADS_UP")
            put("seat0Deck", "mehanske Monster Tron")
            put("seat1Deck", "Pest Control v1.0")
            put("matchedOriginalRunId", 37889980098L)
            put("matchedOriginalArtifactId", 11598521277L)
            put("matchedOriginalSourceCommit", "53b09bd99dc009a479529d044b9bc81acfbe89c1")
            put("seatReversal", true)
            put("reusesNonofficialFixtureForPairing", true)
            put("experimentalResult", false)
            put("officialExecutionAuthorized", false)
            put("formalSeedAllocation", false)
            put("sourceCommit", sourceSha)
            put("baselineMainCommit", "4816825fb6a4d6535f0365554d204bc964b7ad73")
            put("pestMainSha256", PEST_CONTROL_V10_HASH)
            put("monsterTronMainSha256", PEST_MONSTER_TRON_MAIN_SHA256)
            put("fixtureSeedHex", "0x504c415946495253")
            put("intendedGames", 1)
            put("maxActions", 12000)
            put("maxTurns", 60)
            put("maxActionsPerTurn", 500)
        }.toString() + "\n", CREATE_NEW)

        var actionCount = 0
        try {
            val registry = CardRegistry().apply {
                register(PredefinedTokens.allTokens)
                MtgSetCatalog.all.forEach { set ->
                    register(set.cards)
                    register(set.basicLands)
                }
            }
            PestControlPreboardDecks.verifyFrozenIdentities()
            check(PestControlTierOneMonsterTronAdmission.validationErrors(TierOneMonsterTronAdmission()).isEmpty())
            check(PestControlTierOneMonsterTronAdmission.unresolvedMain(registry).isEmpty())
            check(PestControlPreboardDecks.pestMainCounts.values.sum() == 60)
            check(PestControlTierOneMonsterTronAdmission.mainCounts.values.sum() == 60)
            val env = GameEnvironment.create(registry)
            env.reset(
                GameConfig(
                    players = listOf(
                        PlayerConfig("mehanske Monster Tron", PestControlTierOneMonsterTronReadiness.mainDeck(), startingLife = 20),
                        PlayerConfig("Pest Control v1.0", PestControlPreboardDecks.pestMain(), startingLife = 20),
                    ),
                    skipMulligans = false,
                    useHandSmoother = false,
                    startingPlayerIndex = 0,
                    seed = seed,
                )
            )
            val players = env.playerIds
            check(players.size == 2)
            append(buildJsonObject {
                put("recordType", "INITIALIZED")
                put("player0", players[0].value)
                put("player1", players[1].value)
                put("turn", env.turnNumber)
                put("gameOver", env.state.gameOver)
            })

            fun summaries(state: GameState, ids: List<EntityId>): Map<EntityId, CardSummary> =
                ids.associateWith { id ->
                    val card = state.getEntity(id)?.get<CardComponent>()
                    CardSummary(
                        name = card?.name ?: id.value,
                        manaCost = card?.manaCost?.toString(),
                        typeLine = card?.typeLine?.toString(),
                        power = card?.baseStats?.basePower,
                        toughness = card?.baseStats?.baseToughness,
                        oracleText = card?.oracleText,
                    )
                }

            fun submit(action: GameAction) {
                val before = env.state
                actionCount++
                val sequence = actionCount
                // The intent precedes the real engine submission; a missing result is not success.
                append(buildJsonObject {
                    put("recordType", "ACTION_INTENT")
                    put("sequence", sequence)
                    put("turn", before.turnNumber)
                    put("step", before.step.name)
                    put("actor", action.playerId.value)
                    put("pendingDecision", before.pendingDecision?.let { it::class.simpleName } ?: "")
                    put("selectedAction", json.encodeToJsonElement(GameAction.serializer(), action))
                })
                val applied = env.stepExactlyOne(action)
                val rejected = applied as? ExactlyOneSubmissionResult.Rejected
                append(buildJsonObject {
                    put("recordType", "ACTION_RESULT")
                    put("sequence", sequence)
                    put("accepted", applied is ExactlyOneSubmissionResult.Applied)
                    put("rejectionReason", rejected?.reason ?: "")
                    put("emittedEvents", JsonArray(env.lastStepEvents.map {
                        json.encodeToJsonElement(GameEvent.serializer(), it)
                    }))
                    put("turnAfter", env.state.turnNumber)
                    put("gameOverAfter", env.state.gameOver)
                    put("lifeAfter", JsonArray(players.map { JsonPrimitive(env.state.lifeTotal(it)) }))
                })
                check(rejected == null) { "Engine rejected exhibition action " + sequence + ": " + rejected?.reason }
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
                    val component = state.getEntity(player)?.get<MulliganStateComponent>()
                        ?: error("Missing mulligan state")
                    if (component.hasKept) break
                    val hand = state.getHand(player)
                    val keep = controller.decideMulligan(
                        MulliganInfo(
                            hand = hand,
                            mulliganCount = component.mulligansTaken,
                            cardsToPutOnBottom = component.cardsToBottom,
                            cards = summaries(state, hand),
                            isOnThePlay = state.turnOrder.first() == player,
                        )
                    )
                    submit(if (keep) KeepHand(player) else TakeMulligan(player))
                }
            }
            for (player in env.state.turnOrder) {
                val state = env.state
                val component = state.getEntity(player)?.get<MulliganStateComponent>() ?: continue
                if (component.cardsToBottom == 0) continue
                val hand = state.getHand(player)
                val bottom = mulligans.getValue(player).chooseBottomCards(
                    BottomCardsInfo(hand, component.cardsToBottom, summaries(state, hand))
                )
                submit(BottomCards(player, bottom))
            }

            val agents = players.mapIndexed { seat, player ->
                AIPlayer.create(
                    registry,
                    player,
                    if (seat == 0) PestMonsterTronPolicy.profile
                    else AiProfile.PRODUCTION_CANDIDATE_EXPIRING,
                )
            }
            var lastTurn = env.turnNumber
            var actionsThisTurn = 0
            while (!env.isTerminal) {
                check(actionCount < 12000) { "Exhibition reached 12000-action cap" }
                check(env.turnNumber <= 60) { "Exhibition reached 60-turn cap" }
                if (env.turnNumber != lastTurn) {
                    lastTurn = env.turnNumber
                    actionsThisTurn = 0
                }
                check(++actionsThisTurn <= 500) { "Exhibition no-progress per-turn action cap" }
                val state = env.state
                val decision = state.pendingDecision
                val acting = decision?.playerId ?: state.priorityPlayerId
                    ?: error("No priority player or pending decision")
                val seat = players.indexOf(acting)
                check(seat >= 0)
                val action = if (decision != null) {
                    SubmitDecision(acting, agents[seat].respondToDecision(state, decision))
                } else {
                    agents[seat].chooseAction(state)
                }
                submit(action)
            }

            val terminal = env.state
            val winnerSeat = terminal.winnerId?.let { players.indexOf(it) } ?: -1
            append(buildJsonObject {
                put("recordType", "EXHIBITION_TERMINAL")
                put("seat0Deck", "mehanske Monster Tron")
                put("seat1Deck", "Pest Control v1.0")
                put("matchedOriginalRunId", 37889980098L)
                put("seatReversal", true)
                put("gameOver", terminal.gameOver)
                put("winnerId", terminal.winnerId?.value ?: "")
                put("winnerSeat", winnerSeat)
                put("turn", terminal.turnNumber)
                put("actions", actionCount)
                put("lifeBySeat", JsonArray(players.map { JsonPrimitive(terminal.lifeTotal(it)) }))
                put("scope", "NONOFFICIAL_PLAY_FIRST_EXHIBITION_ONLY")
                put("formalExperimentCount", 0)
            })
            check(terminal.gameOver) { "No legitimate engine terminal; cannot claim a game" }
            println("PLAY_FIRST_SEAT_REVERSAL_RESULT gameOver=true winnerSeat=" + winnerSeat +
                " turns=" + terminal.turnNumber + " actions=" + actionCount +
                " trace=exhibition-seat-reversal-original.jsonl NONOFFICIAL")
        } catch (failure: Throwable) {
            append(buildJsonObject {
                put("recordType", "EXHIBITION_FAILED")
                put("actionsObserved", actionCount)
                put("failureClass", failure::class.qualifiedName ?: "Throwable")
                put("failure", failure.message ?: "unreported")
                put("notOfficial", true)
            })
            throw failure
        }
    }
})
