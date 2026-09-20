package com.wingedsheep.gym.matchup

import com.wingedsheep.ai.engine.AIPlayer
import com.wingedsheep.ai.engine.AiProfile
import com.wingedsheep.ai.engine.EngineAiPlayerController
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.registry.CardRegistry

object PestControlPreboardProductionDriver {
    fun drive(
        registry: CardRegistry,
        session: PestControlPreboardSession,
        maxActions: Int = 12_000,
        maxTurns: Int = 60,
        maxActionsPerTurn: Int = 500,
    ): MatchupRawGame {
        val players = session.environment.playerIds
        val mulligans = players.associateWith { player ->
            EngineAiPlayerController(registry, player, gameStateProvider = { session.environment.state })
        }
        session.driveValidatedLondonMulligans(mulligans)
        val agents = players.associateWith { player ->
            AIPlayer.create(registry, player, AiProfile.PRODUCTION_CANDIDATE_EXPIRING)
        }
        var lastTurn = session.environment.turnNumber
        var actionsThisTurn = 0
        while (!session.environment.isTerminal) {
            session.enforceLimits(maxActions = maxActions, maxTurns = maxTurns)
            if (session.environment.turnNumber != lastTurn) {
                lastTurn = session.environment.turnNumber
                actionsThisTurn = 0
            }
            if (++actionsThisTurn > maxActionsPerTurn) {
                session.reject(
                    ProtocolDefectKind.WEDGE,
                    "more than $maxActionsPerTurn exact-one actions on turn $lastTurn",
                )
            }
            val state = session.environment.state
            val decision = state.pendingDecision
            val acting = decision?.playerId ?: state.priorityPlayerId
                ?: session.reject(ProtocolDefectKind.WEDGE, "no pending decision or priority holder")
            val agent = agents.getValue(acting)
            val action: GameAction = if (decision != null) {
                SubmitDecision(acting, agent.respondToDecision(state, decision))
            } else {
                agent.chooseAction(state)
            }
            session.submit(action, fallbackUsed = false)
        }
        return session.rawGame()
    }
}
