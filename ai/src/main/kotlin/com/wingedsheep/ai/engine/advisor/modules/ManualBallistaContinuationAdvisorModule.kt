package com.wingedsheep.ai.engine.advisor.modules

import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.SimulationResult
import com.wingedsheep.ai.engine.advisor.*
import com.wingedsheep.ai.engine.rollout.FastDecisionResponder
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.engine.state.components.stack.ChosenTarget

/** One owner per card; replace the R3 module explicitly rather than registering both. */
object ManualBallistaContinuationAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) { registry.register(ManualBallistaContinuationAdvisor) }
}

/** Opt-in bounded terminal witness, with R3's ordinary Statue exits retained unchanged. */
object ManualBallistaContinuationAdvisor : CardAdvisor {
    override val cardNames = setOf("Ancestral Statue", "Walking Ballista")
    private const val MAX_STATUE_CASTS = 8
    private const val MAX_SHOTS = 160
    private fun name(state: GameState, id: EntityId) = state.getEntity(id)?.get<CardComponent>()?.name
    private fun simulator(context: CastContext) = GameSimulator(context.simulator.cardRegistry).apply {
        decisionResolver = { state, decision ->
            val select = decision as? SelectCardsDecision
            val source = decision.context.sourceId
            val statue = if (select != null && decision.context.sourceName == "Ancestral Statue")
                source?.takeIf { it in select.options } ?: select.options.singleOrNull { name(state, it) == "Ancestral Statue" }
                else null
            if (statue != null) CardsSelectedResponse(decision.id, listOf(statue))
            else FastDecisionResponder().respond(state, decision, decision.playerId)
        }
    }

    /** Only an actual engine terminal victory is a witness. Shields, shroud and life protection count. */
    private fun finishShots(initial: GameState, player: EntityId, sim: GameSimulator): GameState? {
        var state = initial
        repeat(MAX_SHOTS) {
            if (state.gameOver) return state.takeIf { it.winnerId == player }
            if (state.activePlayerId != player || state.turnNumber != initial.turnNumber || state.stack.isNotEmpty()) return null
            val shot = sim.getLegalActions(state, player).firstOrNull {
                val action = it.action as? ActivateAbility
                it.affordable && it.requiresTargets && action != null && name(state, action.sourceId) == "Walking Ballista" &&
                    state.getOpponents(player).any { opponent -> opponent in (it.validTargets ?: emptyList()) }
            } ?: return null
            val target = state.getOpponents(player).filter { it in (shot.validTargets ?: emptyList()) }
                .minByOrNull { state.lifeTotal(it) } ?: return null
            val action = (shot.action as ActivateAbility).copy(targets = listOf(ChosenTarget.Player(target)))
            val next = sim.simulate(state, action) as? SimulationResult.Terminal ?: return null
            state = next.state
        }
        return state.takeIf { it.gameOver && it.winnerId == player }
    }

    private fun castAndFinish(state: GameState, player: EntityId, sim: GameSimulator): GameState? {
        val actions = sim.getLegalActions(state, player).filter {
            val cast = it.action as? CastSpell
            it.affordable && cast != null && name(state, cast.cardId) == "Walking Ballista"
        }.sortedByDescending { (it.action as CastSpell).xValue ?: 0 }.take(3)
        for (action in actions) {
            val result = sim.simulate(state, action.action) as? SimulationResult.Terminal ?: continue
            finishShots(result.state, player, sim)?.let { return it }
        }
        return null
    }

    override fun respondToDecision(context: AdvisorDecisionContext): DecisionResponse? =
        if (context.sourceCardName == "Ancestral Statue") ManualStatueContinuationAdvisor.respondToDecision(context) else null

    override fun evaluateCast(context: CastContext): Double? {
        val action = context.action.action
        val sim = simulator(context)
        fun score(terminal: GameState) = context.evaluator.evaluate(terminal, terminal.projectedState, context.playerId) / 2
        if (action is ActivateAbility && name(context.state, action.sourceId) == "Walking Ballista") {
            if (!context.action.requiresTargets) return null
            val result = sim.simulate(context.state, action) as? SimulationResult.Terminal ?: return null
            return finishShots(result.state, context.playerId, sim)?.let(::score)
        }
        if (action !is CastSpell) return null
        if (name(context.state, action.cardId) == "Walking Ballista") {
            val result = sim.simulate(context.state, action) as? SimulationResult.Terminal ?: return null
            return finishShots(result.state, context.playerId, sim)?.let(::score)
        }
        if (name(context.state, action.cardId) != "Ancestral Statue") return null
        if (context.state.getZone(ZoneKey(context.playerId, Zone.HAND)).none { name(context.state, it) == "Walking Ballista" })
            return ManualStatueContinuationAdvisor.evaluateCast(context)
        if (!context.state.phase.isMainPhase || context.state.stack.isNotEmpty()) return ManualStatueContinuationAdvisor.evaluateCast(context)
        // Do not add another counter once the real Ballista exit already wins.
        if (castAndFinish(context.state, context.playerId, sim) != null) return context.passScore - 0.001
        var state = context.state
        repeat(MAX_STATUE_CASTS) {
            val cast = sim.getLegalActions(state, context.playerId).firstOrNull {
                it.affordable && (it.action as? CastSpell)?.cardId == action.cardId
            } ?: return ManualStatueContinuationAdvisor.evaluateCast(context)
            val result = sim.simulate(state, cast.action) as? SimulationResult.Terminal
                ?: return ManualStatueContinuationAdvisor.evaluateCast(context)
            state = result.state
            if (state.turnNumber != context.state.turnNumber || state.activePlayerId != context.playerId ||
                action.cardId !in state.getZone(ZoneKey(context.playerId, Zone.HAND)))
                return ManualStatueContinuationAdvisor.evaluateCast(context)
            castAndFinish(state, context.playerId, sim)?.let { return score(it) }
        }
        return ManualStatueContinuationAdvisor.evaluateCast(context)
    }
}
