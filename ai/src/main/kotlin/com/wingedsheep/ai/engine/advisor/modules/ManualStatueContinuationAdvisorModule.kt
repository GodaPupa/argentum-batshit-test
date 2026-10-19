package com.wingedsheep.ai.engine.advisor.modules

import com.wingedsheep.ai.engine.*
import com.wingedsheep.ai.engine.advisor.*
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/** Explicit opt-in only. Bounds hypothetical self-returns, never the legal rules action space. */
object ManualStatueContinuationAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) { registry.register(ManualStatueContinuationAdvisor) }
}

object ManualStatueContinuationAdvisor : CardAdvisor {
    override val cardNames = setOf("Ancestral Statue")
    private const val LOOKAHEAD_CASTS = 8
    private fun name(state: GameState, id: EntityId) = state.getEntity(id)?.get<CardComponent>()?.name
    private fun self(state: GameState, decision: SelectCardsDecision) = decision.options.firstOrNull {
        name(state, it) == "Ancestral Statue"
    }
    private fun simulation(context: AdvisorDecisionContext) = GameSimulator(context.simulator.cardRegistry)
    private fun selfResolver(sim: GameSimulator) {
        sim.decisionResolver = { state, decision ->
            val select = decision as? SelectCardsDecision
            val id = select?.takeIf { it.context.sourceName == "Ancestral Statue" }?.let { self(state, it) }
            if (id != null) CardsSelectedResponse(decision.id, listOf(id))
            else com.wingedsheep.ai.engine.rollout.FastDecisionResponder().respond(state, decision, decision.playerId)
        }
    }
    private fun damage(before: GameState, after: GameState, player: EntityId): Boolean =
        (after.gameOver && after.winnerId == player) || before.turnOrder.filter { it != player }.any {
            val old = before.getEntity(it)?.get<LifeTotalComponent>()?.life ?: return@any false
            val now = after.getEntity(it)?.get<LifeTotalComponent>()?.life ?: old
            now < old
        }

    private fun readyExit(context: AdvisorDecisionContext, state: GameState, statue: EntityId): Boolean {
        val sim = simulation(context)
        val before = context.evaluator.evaluate(state, state.projectedState, context.playerId)
        return sim.getLegalActions(state, context.playerId).filter {
            val cast = it.action as? CastSpell
            it.affordable && !it.requiresTargets && cast != null && cast.cardId != statue &&
                cast.cardId in state.getZone(ZoneKey(context.playerId, Zone.HAND)) &&
                state.getEntity(cast.cardId)?.get<CardComponent>()?.typeLine?.isCreature == true
        }.take(12).any {
            val result = sim.simulate(state, it.action) as? SimulationResult.Terminal
            result != null && (damage(state, result.state, context.playerId) ||
                context.evaluator.evaluate(result.state, result.state.projectedState, context.playerId) > before)
        }
    }

    /** A witness must use affordable, enumerated actions and complete engine resolutions. */
    private fun hasContinuation(context: AdvisorDecisionContext, returned: GameState, statue: EntityId): Boolean {
        val sim = simulation(context)
        selfResolver(sim)
        val player = context.playerId
        fun creatures(s: GameState) = sim.getLegalActions(s, player).filter {
            val cast = it.action as? CastSpell
            it.affordable && !it.requiresTargets && cast != null && cast.cardId != statue &&
                cast.cardId in s.getZone(ZoneKey(player, Zone.HAND)) &&
                s.getEntity(cast.cardId)?.get<CardComponent>()?.typeLine?.isCreature == true
        }
        val originallyAffordable = creatures(returned).map { it.action }.toSet()
        var state = returned
        repeat(LOOKAHEAD_CASTS) {
            val cast = sim.getLegalActions(state, player).firstOrNull {
                it.affordable && !it.requiresTargets && (it.action as? CastSpell)?.cardId == statue
            } ?: return false
            val next = (sim.simulate(state, cast.action) as? SimulationResult.Terminal)?.state ?: return false
            if (next.turnNumber != returned.turnNumber || next.activePlayerId != player) return false
            if (damage(state, next, player)) return true
            for (exit in creatures(next).filter { it.action !in originallyAffordable }.take(12)) {
                val final = (sim.simulate(next, exit.action) as? SimulationResult.Terminal)?.state ?: continue
                if (damage(next, final, player) || context.evaluator.evaluate(final, final.projectedState, player) >
                    context.evaluator.evaluate(next, next.projectedState, player)) return true
            }
            if (statue !in next.getZone(ZoneKey(player, Zone.HAND))) return false
            state = next
        }
        return false
    }

    override fun respondToDecision(context: AdvisorDecisionContext): DecisionResponse? {
        val decision = context.decision as? SelectCardsDecision ?: return null
        if (decision.minSelections != 1 || decision.maxSelections != 1) return null
        val statue = self(context.state, decision) ?: return null
        val sim = simulation(context)
        val selfResponse = CardsSelectedResponse(decision.id, listOf(statue))
        val returned = (sim.simulateDecision(context.state, selfResponse) as? SimulationResult.Terminal)?.state
        if (returned != null && (damage(context.state, returned, context.playerId) || readyExit(context, returned, statue) || hasContinuation(context, returned, statue)))
            return selfResponse
        // Stop scaling counters without a concrete payoff. Keep the body via an actual legal exit.
        return decision.options.filter { it != statue }.mapNotNull { id ->
            val response = CardsSelectedResponse(decision.id, listOf(id))
            val result = sim.simulateDecision(context.state, response) as? SimulationResult.Terminal ?: return@mapNotNull null
            response to context.evaluator.evaluate(result.state, result.state.projectedState, context.playerId)
        }.maxByOrNull { it.second }?.first ?: selfResponse
    }

    override fun evaluateCast(context: CastContext): Double? {
        if (context.action.action !is CastSpell) return null
        val cast = context.action.action as CastSpell
        val sim = GameSimulator(context.simulator.cardRegistry)
        sim.decisionResolver = { state, decision ->
            val child = AdvisorDecisionContext(state, state.projectedState, context.playerId, decision,
                decision.context.sourceName ?: "", context.evaluator, sim)
            if (decision.context.sourceName == "Ancestral Statue") respondToDecision(child)
                ?: com.wingedsheep.ai.engine.rollout.FastDecisionResponder().respond(state, decision, decision.playerId)
            else com.wingedsheep.ai.engine.rollout.FastDecisionResponder().respond(state, decision, decision.playerId)
        }
        val result = sim.simulate(context.state, context.action.action) as? SimulationResult.Terminal
            ?: return context.passScore - 0.001
        val value = context.evaluator.evaluate(result.state, result.state.projectedState, context.playerId)
        if (cast.cardId in result.state.getZone(ZoneKey(context.playerId, Zone.HAND)) &&
            !damage(context.state, result.state, context.playerId)) {
            // The self-return has reached an executable positive hand exit: stop recasting Statue.
            // The real pilot still chooses the next action; no future payoff is added to this score.
            val decision = SelectCardsDecision("statue-probe", context.playerId, "", DecisionContext(), listOf(cast.cardId), 1, 1)
            val child = AdvisorDecisionContext(context.state, context.projected, context.playerId, decision,
                "Ancestral Statue", context.evaluator, sim)
            if (readyExit(child, context.state, cast.cardId)) return context.passScore - 0.001
        }
        return value
    }
}
