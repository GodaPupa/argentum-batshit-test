package com.wingedsheep.ai.engine.advisor.modules

import com.wingedsheep.ai.engine.SimulationResult
import com.wingedsheep.ai.engine.advisor.*
import com.wingedsheep.ai.engine.rollout.FastDecisionResponder
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.effects.composite.canAutoPayManaCost
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.player.PlayerLostComponent
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step

/** Opt-in debt guard; never changes CURRENT, R4 composition, or Pact's legal rules. */
object ManualPactDebtAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) { registry.register(ManualPactDebtAdvisor) }
}

/**
 * Reject an unfunded delayed debt after real engine passes reach the caster's next upkeep.
 * This is a bounded all-pass resource witness, not a prediction of opponents' future plays.
 * No new mana source, land drop, invented untap, or future drawn card is credited.
 */
object ManualPactDebtAdvisor : CardAdvisor {
    override val cardNames = setOf("Pact of Negation")
    internal enum class Debt { FUNDED, UNFUNDED, UNKNOWN }

    internal fun nextUpkeepDebt(context: CastContext, after: GameState,
        observe: ((GameState, GameAction?, String?) -> Unit)? = null,
    ): Debt {
        val processor = ActionProcessor(EngineServices(context.simulator.cardRegistry), computeUndo = false)
        val fast = FastDecisionResponder()
        var state = after
        repeat(256) {
            if (state.gameOver || context.playerId !in state.activePlayers || state.getEntity(context.playerId)?.has<PlayerLostComponent>() == true) {
                observe?.invoke(state, null, "UNKNOWN: game ended or pilot inactive before witness")
                return Debt.UNKNOWN
            }
            if (state.turnNumber > context.state.turnNumber && state.activePlayerId == context.playerId && state.step == Step.UPKEEP) {
                val debt = if (canAutoPayManaCost(state, context.playerId, ManaCost.parse("{3}{U}{U}"), context.simulator.cardRegistry))
                    Debt.FUNDED else Debt.UNFUNDED
                observe?.invoke(state, null, debt.name)
                return debt
            }
            val pending = state.pendingDecision
            val actor = pending?.playerId ?: state.priorityPlayerId ?: run {
                observe?.invoke(state, null, "UNKNOWN: missing priority")
                return Debt.UNKNOWN
            }
            val action = if (pending != null) SubmitDecision(actor, fast.respond(state, pending, actor)) else {
                // A combat step still has ordinary priority after its declaration. Never submit
                // a second declaration just because the global step name has not advanced.
                if (state.step == Step.DECLARE_ATTACKERS || state.step == Step.DECLARE_BLOCKERS)
                    context.simulator.getLegalActions(state, actor).firstOrNull {
                        it.action is DeclareAttackers || it.action is DeclareBlockers
                    }?.action ?: PassPriority(actor)
                else PassPriority(actor)
            }
            val result = processor.process(state, action).result
            observe?.invoke(state, action, result.error)
            if (result.error != null) return Debt.UNKNOWN
            state = result.state
        }
        observe?.invoke(state, null, "UNKNOWN: 256-submission cap")
        return Debt.UNKNOWN
    }

    override fun evaluateCast(context: CastContext): Double? {
        if (context.action.action !is CastSpell || !context.passScore.isFinite()) return null
        val cast = context.simulator.simulate(context.state, context.action.action) as? SimulationResult.Terminal ?: return null
        val player = context.playerId
        if (cast.state.gameOver && cast.state.winnerId == player) return null
        val passed = context.simulator.simulate(context.state, PassPriority(player)) as? SimulationResult.Terminal
        val losesImmediately = passed != null && (player !in passed.state.activePlayers || passed.state.getEntity(player)?.has<PlayerLostComponent>() == true ||
            (passed.state.gameOver && passed.state.winnerId != player))
        val survives = player in cast.state.activePlayers && cast.state.getEntity(player)?.has<PlayerLostComponent>() != true &&
            (!cast.state.gameOver || cast.state.winnerId == player)
        // A last-chance answer to actual immediate loss must remain available even without debt mana.
        if (losesImmediately && survives) return null
        return if (nextUpkeepDebt(context, cast.state) == Debt.UNFUNDED)
            minOf(context.defaultScore, context.passScore - 1.0) else null
    }
}
