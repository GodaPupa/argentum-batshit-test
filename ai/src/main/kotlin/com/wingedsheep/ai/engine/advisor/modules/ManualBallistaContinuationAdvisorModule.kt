package com.wingedsheep.ai.engine.advisor.modules

import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.SimulationResult
import com.wingedsheep.ai.engine.XCostSelection
import com.wingedsheep.ai.engine.advisor.*
import com.wingedsheep.ai.engine.rollout.FastDecisionResponder
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
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
    private fun simulator(registry: CardRegistry) = GameSimulator(registry).apply {
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

    /** Only exempt Pact debt when legal Ballista activations prove an engine terminal win.
     * The witness does not predict future opponent plays or fabricate a damage shortcut. */
    internal fun provesUncontestedBallistaWin(state: GameState, player: EntityId, registry: CardRegistry): Boolean {
        if (state.gameOver || state.stack.isNotEmpty() || state.activePlayerId != player) return false
        if (state.projectedState.getBattlefieldControlledBy(player).none { name(state, it) == "Walking Ballista" }) return false
        return finishShots(state, player, simulator(registry)) != null
    }

    private fun castAndFinish(state: GameState, player: EntityId, sim: GameSimulator): GameState? {
        val actions = sim.getLegalActions(state, player).filter {
            val cast = it.action as? CastSpell
            it.affordable && cast != null && name(state, cast.cardId) == "Walking Ballista"
        }.flatMap { if (it.hasXCost) XCostSelection.expandToX(state, it) else listOf(it) }
            .sortedByDescending { (it.action as CastSpell).xValue ?: 0 }.take(3)
        for (action in actions) {
            val result = sim.simulate(state, action.action) as? SimulationResult.Terminal ?: continue
            finishShots(result.state, player, sim)?.let { return it }
        }
        return null
    }

    private fun statueWitness(initial: GameState, player: EntityId, statue: EntityId, sim: GameSimulator): GameState? {
        var state = initial
        repeat(MAX_STATUE_CASTS) {
            val cast = sim.getLegalActions(state, player).firstOrNull {
                it.affordable && (it.action as? CastSpell)?.cardId == statue
            } ?: return null
            val result = sim.simulate(state, cast.action) as? SimulationResult.Terminal ?: return null
            state = result.state
            if (state.turnNumber != initial.turnNumber || state.activePlayerId != player ||
                statue !in state.getZone(ZoneKey(player, Zone.HAND))) return null
            castAndFinish(state, player, sim)?.let { return it }
        }
        return null
    }

    override fun respondToDecision(context: AdvisorDecisionContext): DecisionResponse? {
        if (context.sourceCardName != "Ancestral Statue") return null
        fun ordinary() = ManualStatueContinuationAdvisor.respondToDecision(context)
        val decision = context.decision as? SelectCardsDecision ?: return ordinary()
        if (decision.minSelections != 1 || decision.maxSelections != 1 ||
            context.state.getZone(ZoneKey(context.playerId, Zone.HAND)).none { name(context.state, it) == "Walking Ballista" }) return ordinary()
        val statue = decision.context.sourceId?.takeIf { it in decision.options && name(context.state, it) == "Ancestral Statue" }
            ?: decision.options.singleOrNull { name(context.state, it) == "Ancestral Statue" } ?: return ordinary()
        val response = CardsSelectedResponse(decision.id, listOf(statue))
        val sim = simulator(context.simulator.cardRegistry)
        val returned = sim.simulateDecision(context.state, response) as? SimulationResult.Terminal ?: return ordinary()
        // The actual bounce must agree with the cast witness, including announced affordable X.
        if (castAndFinish(returned.state, context.playerId, sim) != null ||
            statueWitness(returned.state, context.playerId, statue, sim) != null) return response
        return ordinary()
    }

    override fun evaluateCast(context: CastContext): Double? {
        val action = context.action.action
        val sim = simulator(context.simulator.cardRegistry)
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
        return statueWitness(context.state, context.playerId, action.cardId, sim)?.let(::score)
            ?: ManualStatueContinuationAdvisor.evaluateCast(context)
    }
}
