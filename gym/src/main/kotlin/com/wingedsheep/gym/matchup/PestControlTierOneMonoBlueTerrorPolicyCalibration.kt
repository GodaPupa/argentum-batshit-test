package com.wingedsheep.gym.matchup

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.EngineAiPlayerController
import com.wingedsheep.ai.llm.BottomCardsInfo
import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.engine.core.BottomCards
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gym.ExactlyOneSubmissionResult
import com.wingedsheep.gym.GameEnvironment
import com.wingedsheep.sdk.model.EntityId

const val PEST_MONO_BLUE_TERROR_POLICY_CALIBRATION_SHA256 =
    "38741508238b542304a18e88b4976c83eb87a5c27801424f25e940de16d8b227"
const val PEST_MONO_BLUE_TERROR_POLICY_CALIBRATION_STATUS =
    "PRODUCTION_AI_COMPATIBILITY_REHEARSED_EXECUTION_NOT_AUTHORIZED"

data class MonoBlueTerrorPolicyCalibrationInspection(
    val errors: List<String>,
    val calibrationSha256: String,
    val status: String,
    val completedGames: Int,
    val terrorActedGames: Int,
    val actionCounts: List<Int>,
    val terminalTurns: List<Int>,
    val rejectedActions: Int,
    val wedges: Int,
    val officialSeedValuesExposed: Int = 0,
    val officialSeedsConsumed: Int = 0,
    val officialGamesInitialized: Int = 0,
    val outcomeExposure: Int = 0,
    val executionAuthorized: Boolean = false,
) {
    val green: Boolean get() = errors.isEmpty()
    val failClosed: Boolean get() = green && !executionAuthorized
}

private data class TerrorCalibrationGame(
    val environment: GameEnvironment,
    val terrorPlayer: EntityId,
)

private data class TerrorCalibrationGameResult(
    val gameNumber: Int,
    val gameOver: Boolean,
    val actionCount: Int,
    val terrorGameplayActions: Int,
    val terminalTurn: Int,
    val rejectedActions: Int,
    val wedge: Boolean,
    val failure: String? = null,
)

private val TERROR_POLICY_CALIBRATION_SEEDS = listOf(
    0x7E77_0B1E_2000_0001L,
    0x7E77_0B1E_2000_0002L,
    0x7E77_0B1E_2000_0003L,
    0x7E77_0B1E_2000_0004L,
)

private object PestControlTierOneMonoBlueTerrorPolicyCalibrationRehearsal {
    fun run(registry: CardRegistry): List<TerrorCalibrationGameResult> =
        PestControlTierOneMonoBlueTerrorSmokeHarness.cellTemplate().mapIndexed { index, cell ->
            val seed = TERROR_POLICY_CALIBRATION_SEEDS[index]
            val assignment = MonoBlueTerrorSmokeAssignment(
                gameNumber = cell.gameNumber,
                seed = seed,
                seedHex = terrorAssignmentSeedHex(seed),
                pestSeat = cell.pestSeat,
                terrorSeat = if (cell.pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO,
                startingDeck = cell.startingDeck,
            )
            val game = initialize(registry, assignment)
            drive(registry, assignment.gameNumber, game)
        }

    private fun initialize(
        registry: CardRegistry,
        assignment: MonoBlueTerrorSmokeAssignment,
    ): TerrorCalibrationGame {
        val seats = if (assignment.pestSeat == PestSeat.SEAT_ZERO) {
            listOf(
                "Pest Control v1.0" to PestControlPreboardDecks.pestMain(),
                "Serpico_CC Mono-Blue Terror" to PestControlTierOneMonoBlueTerrorReadiness.mainDeck(),
            )
        } else {
            listOf(
                "Serpico_CC Mono-Blue Terror" to PestControlTierOneMonoBlueTerrorReadiness.mainDeck(),
                "Pest Control v1.0" to PestControlPreboardDecks.pestMain(),
            )
        }
        val pestSeatIndex = if (assignment.pestSeat == PestSeat.SEAT_ZERO) 0 else 1
        val terrorSeatIndex = 1 - pestSeatIndex
        val startingPlayerIndex = when (assignment.startingDeck) {
            MonoBlueTerrorStartingDeck.PEST_CONTROL -> pestSeatIndex
            MonoBlueTerrorStartingDeck.MONO_BLUE_TERROR -> terrorSeatIndex
        }

        val environment = GameEnvironment.create(registry)
        environment.reset(
            GameConfig(
                players = seats.map { (name, deck) -> PlayerConfig(name, deck, startingLife = 20) },
                skipMulligans = false,
                useHandSmoother = false,
                startingPlayerIndex = startingPlayerIndex,
                seed = assignment.seed,
            )
        )
        return TerrorCalibrationGame(
            environment = environment,
            terrorPlayer = environment.playerIds[terrorSeatIndex],
        )
    }

    private fun drive(
        registry: CardRegistry,
        gameNumber: Int,
        game: TerrorCalibrationGame,
        maxActions: Int = 12_000,
        maxTurns: Int = 60,
        maxActionsPerTurn: Int = 500,
    ): TerrorCalibrationGameResult {
        val environment = game.environment
        var actionCount = 0
        var terrorGameplayActions = 0
        var rejectedActions = 0

        return try {
            val controllers = environment.playerIds.associateWith { player ->
                EngineAiPlayerController(registry, player, gameStateProvider = { environment.state })
            }
            driveMulligans(environment, environment.playerIds, controllers) { action ->
                submitExactlyOne(environment, action)
                actionCount++
            }

            val agents = environment.playerIds.associateWith { player ->
                AIPlayer.create(registry, player, AiProfile.PRODUCTION_CANDIDATE_EXPIRING)
            }
            var lastTurn = environment.turnNumber
            var actionsThisTurn = 0

            while (!environment.isTerminal) {
                check(actionCount < maxActions) { "reached $maxActions actions" }
                check(environment.turnNumber <= maxTurns) { "reached turn ${environment.turnNumber}" }
                if (environment.turnNumber != lastTurn) {
                    lastTurn = environment.turnNumber
                    actionsThisTurn = 0
                }
                check(++actionsThisTurn <= maxActionsPerTurn) {
                    "more than $maxActionsPerTurn exact-one actions on turn $lastTurn"
                }

                val state = environment.state
                val decision = state.pendingDecision
                val acting = decision?.playerId ?: state.priorityPlayerId
                    ?: error("no pending decision or priority holder")
                val agent = agents.getValue(acting)
                val action: GameAction = if (decision != null) {
                    SubmitDecision(acting, agent.respondToDecision(state, decision))
                } else {
                    agent.chooseAction(state)
                }
                if (acting == game.terrorPlayer) terrorGameplayActions++
                submitExactlyOne(environment, action)
                actionCount++
            }

            TerrorCalibrationGameResult(
                gameNumber = gameNumber,
                gameOver = environment.state.gameOver,
                actionCount = actionCount,
                terrorGameplayActions = terrorGameplayActions,
                terminalTurn = environment.state.turnNumber,
                rejectedActions = rejectedActions,
                wedge = false,
            )
        } catch (failure: TerrorCalibrationRejectedAction) {
            rejectedActions++
            TerrorCalibrationGameResult(
                gameNumber = gameNumber,
                gameOver = false,
                actionCount = actionCount,
                terrorGameplayActions = terrorGameplayActions,
                terminalTurn = environment.state.turnNumber,
                rejectedActions = rejectedActions,
                wedge = false,
                failure = failure.message,
            )
        } catch (failure: Exception) {
            TerrorCalibrationGameResult(
                gameNumber = gameNumber,
                gameOver = false,
                actionCount = actionCount,
                terrorGameplayActions = terrorGameplayActions,
                terminalTurn = environment.state.turnNumber,
                rejectedActions = rejectedActions,
                wedge = true,
                failure = failure.message ?: failure::class.simpleName,
            )
        }
    }

    private fun driveMulligans(
        environment: GameEnvironment,
        players: List<EntityId>,
        controllers: Map<EntityId, EngineAiPlayerController>,
        submit: (GameAction) -> Unit,
    ) {
        var transitions = 0
        for (player in environment.state.turnOrder) {
            val controller = controllers.getValue(player)
            while (true) {
                check(++transitions <= 30) { "London mulligan did not settle" }
                val current = environment.state
                val component = current.getEntity(player)
                    ?.get<com.wingedsheep.engine.state.components.player.MulliganStateComponent>()
                    ?: error("player lacks MulliganStateComponent")
                if (component.hasKept) break
                val hand = current.getHand(player)
                val keep = controller.decideMulligan(
                    MulliganInfo(
                        hand = hand,
                        mulliganCount = component.mulligansTaken,
                        cardsToPutOnBottom = component.cardsToBottom,
                        cards = cardSummaries(current, hand),
                        isOnThePlay = current.turnOrder.first() == player,
                    )
                )
                submit(if (keep) KeepHand(player) else TakeMulligan(player))
            }
        }
        for (player in players) {
            val current = environment.state
            val component = current.getEntity(player)
                ?.get<com.wingedsheep.engine.state.components.player.MulliganStateComponent>() ?: continue
            if (component.cardsToBottom == 0) continue
            val hand = current.getHand(player)
            val bottom = controllers.getValue(player).chooseBottomCards(
                BottomCardsInfo(hand, component.cardsToBottom, cardSummaries(current, hand))
            )
            submit(BottomCards(player, bottom))
        }
    }

    private fun submitExactlyOne(environment: GameEnvironment, action: GameAction) {
        val result = environment.stepExactlyOne(action)
        val rejected = result as? ExactlyOneSubmissionResult.Rejected
        if (rejected != null) {
            throw TerrorCalibrationRejectedAction("rejected production action: ${rejected.reason}")
        }
    }

    private fun cardSummaries(state: GameState, ids: List<EntityId>): Map<EntityId, CardSummary> =
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
}

private class TerrorCalibrationRejectedAction(message: String) : IllegalStateException(message)

/**
 * Synthetic production-policy compatibility only. It returns no winner identity or official seed.
 */
object PestControlTierOneMonoBlueTerrorPolicyCalibration {
    fun inspect(registry: CardRegistry): MonoBlueTerrorPolicyCalibrationInspection {
        val games = PestControlTierOneMonoBlueTerrorPolicyCalibrationRehearsal.run(registry)
        val errors = mutableListOf<String>()
        games.filterNot { it.gameOver }.forEach { game ->
            errors += "game ${game.gameNumber} did not reach a clean terminal: ${game.failure ?: "unknown"}"
        }
        val completedGames = games.count { it.gameOver }
        val terrorActedGames = games.count { it.terrorGameplayActions > 0 }
        val rejectedActions = games.sumOf { it.rejectedActions }
        val wedges = games.count { it.wedge }
        if (completedGames != 4) errors += "synthetic completion count mismatch"
        if (terrorActedGames != 4) errors += "Terror policy did not act in every synthetic game"
        if (rejectedActions != 0) errors += "production AI submitted rejected actions"
        if (wedges != 0) errors += "synthetic production games wedged"

        val proofBytes = listOf(
            "pest-control-tier-one-mono-blue-terror-policy-compatibility-v1",
            "status=$PEST_MONO_BLUE_TERROR_POLICY_CALIBRATION_STATUS",
            "profile=PRODUCTION_CANDIDATE_EXPIRING",
            "games=4",
            "completedGames=$completedGames",
            "terrorActedGames=$terrorActedGames",
            "rejectedActions=$rejectedActions",
            "wedges=$wedges",
            "officialSeedValuesExposed=0",
            "officialSeedsConsumed=0",
            "officialGamesInitialized=0",
            "outcomeExposure=0",
            "executionAuthorized=false",
        ).joinToString("\n", postfix = "\n").toByteArray()
        val calibrationSha256 = sha256(proofBytes)
        if (calibrationSha256 != PEST_MONO_BLUE_TERROR_POLICY_CALIBRATION_SHA256) {
            errors += "policy calibration proof mismatch"
        }

        return MonoBlueTerrorPolicyCalibrationInspection(
            errors = errors.distinct(),
            calibrationSha256 = calibrationSha256,
            status = PEST_MONO_BLUE_TERROR_POLICY_CALIBRATION_STATUS,
            completedGames = completedGames,
            terrorActedGames = terrorActedGames,
            actionCounts = games.map { it.actionCount },
            terminalTurns = games.map { it.terminalTurn },
            rejectedActions = rejectedActions,
            wedges = wedges,
        )
    }
}
