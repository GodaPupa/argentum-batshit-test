package com.wingedsheep.ai.engine.advisor.modules

import com.wingedsheep.ai.engine.SimulationResult
import com.wingedsheep.ai.engine.advisor.CardAdvisor
import com.wingedsheep.ai.engine.advisor.CardAdvisorModule
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.core.Zone

/** Opt-in measured same-turn development; does not alter CURRENT or implement a Manual gear. */
object ManualAnimarDevelopmentAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) { registry.register(ManualAnimarDevelopmentAdvisor) }
}

/**
 * Credit only the incremental gain from casting the SAME real, affordable hand creature
 * after deploying Animar versus casting it without Animar. Subtract the creature's ordinary
 * gain so neither its body nor its ETB is awarded for free. No fixed commander bonus,
 * future draws, mana refresh, tax bypass, or unexecuted multi-cast chain is assumed.
 * A followup must itself improve the deployed board; a better but still negative
 * continuation is not credit the ordinary pilot will collect.
 */
object ManualAnimarDevelopmentAdvisor : CardAdvisor {
    override val cardNames = setOf("Animar, Soul of Elements")
    override fun evaluateCast(context: CastContext): Double? {
        val cast = context.action.action as? CastSpell ?: return null
        val state = context.state
        val player = context.playerId
        if (cast.cardId !in state.getZone(ZoneKey(player, Zone.COMMAND)) ||
            state.activePlayerId != player || !state.phase.isMainPhase || state.stack.isNotEmpty() ||
            !context.defaultScore.isFinite() || !context.passScore.isFinite()) return null
        val deployed = context.simulator.simulate(state, cast) as? SimulationResult.Terminal ?: return null
        val after = deployed.state
        if (after.gameOver || after.turnNumber != state.turnNumber || after.activePlayerId != player ||
            cast.cardId !in after.getZone(ZoneKey(player, Zone.BATTLEFIELD))) return null
        val beforeValue = context.evaluator.evaluate(state, state.projectedState, player)
        val deployedValue = context.evaluator.evaluate(after, after.projectedState, player)
        val initialCounters = after.getEntity(cast.cardId)?.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
        val hand = state.getZone(ZoneKey(player, Zone.HAND)).toSet()
        fun eligible(action: com.wingedsheep.engine.legalactions.LegalAction): Boolean {
            val creature = action.action as? CastSpell ?: return false
            return action.affordable && !action.requiresTargets && !action.hasXCost &&
                creature.cardId in hand && state.getEntity(creature.cardId)?.get<CardComponent>()?.typeLine?.isCreature == true
        }
        val without = context.simulator.getLegalActions(state, player).filter(::eligible)
            .associateBy { (it.action as CastSpell).cardId }
        val with = context.simulator.getLegalActions(after, player).filter(::eligible)
        var best = 0.0
        for (followup in with.take(12)) {
            val id = (followup.action as CastSpell).cardId
            val plain = without[id] ?: continue
            val withResult = context.simulator.simulate(after, followup.action) as? SimulationResult.Terminal ?: continue
            val withoutResult = context.simulator.simulate(state, plain.action) as? SimulationResult.Terminal ?: continue
            if (withResult.state.gameOver || withoutResult.state.gameOver) continue
            val counters = withResult.state.getEntity(cast.cardId)?.get<CountersComponent>()
                ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: 0
            if (counters <= initialCounters) continue
            val withGain = context.evaluator.evaluate(withResult.state, withResult.state.projectedState, player) - deployedValue
            if (!withGain.isFinite() || withGain <= 0.0) continue
            val withoutGain = context.evaluator.evaluate(withoutResult.state, withoutResult.state.projectedState, player) - beforeValue
            val incremental = withGain - withoutGain
            if (incremental.isFinite() && incremental > best) best = incremental
        }
        return if (best > 0.0) context.defaultScore + best else null
    }
}
