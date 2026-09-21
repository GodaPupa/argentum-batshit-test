package com.wingedsheep.gym.matchup

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.EngineAiPlayerController
import com.wingedsheep.ai.llm.BottomCardsInfo
import com.wingedsheep.ai.llm.CardSummary
import com.wingedsheep.ai.llm.MulliganInfo
import com.wingedsheep.engine.core.BottomCards
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.TakeMulligan
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.gym.ExactlyOneSubmissionResult
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class GrixisOfficialActionTrace(
    val sequence: Int,
    val turn: Int,
    val actingPlayerId: EntityId,
    val pendingDecisionType: String?,
    val selectedAction: JsonElement,
    val emittedEvents: List<JsonElement>,
    val accepted: Boolean,
    val rejectionReason: String? = null,
)

@Serializable
data class GrixisOfficialTerminal(
    val gameOver: Boolean,
    val winnerId: EntityId?,
    val turn: Int,
)

@Serializable
data class GrixisOfficialRawGame(
    val provenance: GrixisSmokeProvenance,
    val actions: List<GrixisOfficialActionTrace>,
    val terminal: GrixisOfficialTerminal?,
    val telemetry: GrixisTelemetryIndex,
)

object PestControlTierOneGrixisProductionDriver {
    fun drive(
        registry: CardRegistry,
        officialGame: GrixisAuthorizedOfficialGame,
        maxActions: Int = 12_000,
        maxTurns: Int = 60,
        maxActionsPerTurn: Int = 500,
    ): GrixisOfficialRawGame {
        val environment = officialGame.environment
        val traces = mutableListOf<GrixisOfficialActionTrace>()
        val controllers = environment.playerIds.associateWith { player ->
            EngineAiPlayerController(registry, player, gameStateProvider = { environment.state })
        }

        driveMulligans(environment.state, environment.playerIds, controllers) { action ->
            submitExactlyOne(environment, action, traces)
        }

        val agents = environment.playerIds.associateWith { player ->
            AIPlayer.create(registry, player, AiProfile.PRODUCTION_CANDIDATE_EXPIRING)
        }

        var lastTurn = environment.turnNumber
        var actionsThisTurn = 0
        while (!environment.isTerminal) {
            check(traces.size < maxActions) { "reached $maxActions actions" }
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
            val action = if (decision != null) {
                SubmitDecision(acting, agent.respondToDecision(state, decision))
            } else {
                agent.chooseAction(state)
            }
            submitExactlyOne(environment, action, traces)
        }

        val raw = GrixisOfficialRawGame(
            provenance = officialGame.provenance,
            actions = traces.toList(),
            terminal = GrixisOfficialTerminal(
                gameOver = environment.state.gameOver,
                winnerId = environment.state.winnerId,
                turn = environment.state.turnNumber,
            ),
            telemetry = PestControlTierOneGrixisTelemetryContract.indexText(
                traces.map { trace ->
                    GrixisTelemetryTextTrace(
                        actionSequence = trace.sequence,
                        selectedActionJson = trace.selectedAction.toString(),
                        emittedEventJson = trace.emittedEvents.map { it.toString() },
                    )
                }
            ),
        )
        check(raw.terminal?.gameOver == true)
        check(raw.actions.all { it.accepted && it.rejectionReason == null })
        return raw
    }

    fun encode(raw: GrixisOfficialRawGame): ByteArray =
        (PROTOCOL_JSON.encodeToString(raw) + "\n").toByteArray()

    private fun driveMulligans(
        state: GameState,
        players: List<EntityId>,
        controllers: Map<EntityId, EngineAiPlayerController>,
        submit: (GameAction) -> Unit,
    ) {
        var transitions = 0
        for (player in state.turnOrder) {
            val controller = controllers.getValue(player)
            while (true) {
                check(++transitions <= 30) { "London mulligan did not settle" }
                val current = controllerState(controllers, player, state)
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
            val current = controllerState(controllers, player, state)
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

    private fun controllerState(
        controllers: Map<EntityId, EngineAiPlayerController>,
        player: EntityId,
        fallback: GameState,
    ): GameState = controllers[player]?.let { fallback } ?: fallback

    private fun submitExactlyOne(
        environment: com.wingedsheep.gym.GameEnvironment,
        action: GameAction,
        traces: MutableList<GrixisOfficialActionTrace>,
    ) {
        val before = environment.state
        val acting = action.playerId
        val result = environment.stepExactlyOne(action)
        val rejected = result as? ExactlyOneSubmissionResult.Rejected
        val trace = GrixisOfficialActionTrace(
            sequence = traces.size + 1,
            turn = before.turnNumber,
            actingPlayerId = acting,
            pendingDecisionType = before.pendingDecision?.let { it::class.simpleName },
            selectedAction = PROTOCOL_JSON.encodeToJsonElement(GameAction.serializer(), action),
            emittedEvents = environment.lastStepEvents.map {
                PROTOCOL_JSON.encodeToJsonElement(com.wingedsheep.engine.core.GameEvent.serializer(), it)
            },
            accepted = rejected == null,
            rejectionReason = rejected?.reason,
        )
        traces += trace
        check(rejected == null) { "rejected official action: ${rejected?.reason}" }
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
