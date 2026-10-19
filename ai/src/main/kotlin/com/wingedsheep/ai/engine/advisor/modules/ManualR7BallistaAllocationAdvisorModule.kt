package com.wingedsheep.ai.engine.advisor.modules

import com.wingedsheep.ai.engine.advisor.*
import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.state.components.battlefield.CountersComponent
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TargetsComponent
import com.wingedsheep.sdk.core.CounterType
import com.wingedsheep.sdk.model.EntityId

/**
 * One opt-in owner for Ballista AND Statue; delegates all existing R4 win witnesses,
 * Statue response and safe ordinary moves. Never register the original module beside it.
 */
object ManualR7BallistaAllocationAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) {
        registry.register(ManualR7BallistaCombinedAdvisor)
    }
}

object ManualR7BallistaCombinedAdvisor : CardAdvisor {
    override val cardNames = setOf("Walking Ballista", "Ancestral Statue")

    override fun respondToDecision(context: AdvisorDecisionContext) =
        ManualBallistaContinuationAdvisor.respondToDecision(context)

    /**
     * A narrow, public-information "do not exhaust the last counters into an unkillable
     * creature while earlier Ballista shots remain on the stack" witness.
     *
     * Pending same-source damage already targeting this same creature counts toward lethal.
     * This preserves a full queued nine-shot Animar kill and normal Ballista terminal lines.
     * An absence of a witness falls through to all original R4 scoring behavior.
     */
    override fun evaluateCast(context: CastContext): Double? {
        val protected = ManualBallistaContinuationAdvisor.evaluateCast(context)
        if (protected != null) return protected
        val action = context.action.action as? ActivateAbility ?: return null
        val state = context.state
        val source = state.getEntity(action.sourceId) ?: return null
        if (source.get<CardComponent>()?.name != "Walking Ballista") return null
        val targetId = (action.targets.singleOrNull() as? ChosenTarget.Permanent)?.entityId ?: return null
        val target = state.getEntity(targetId) ?: return null
        if (target.get<CardComponent>()?.typeLine?.isCreature != true) return null
        val targetController = target.get<ControllerComponent>()?.playerId ?: return null
        if (!state.isOpponentTo(targetController, context.playerId)) return null
        if (targetId !in state.projectedState.getBattlefieldControlledBy(targetController)) return null

        val counters = source.get<CountersComponent>()
            ?.getCount(CounterType.PLUS_ONE_PLUS_ONE) ?: return null
        if (counters <= 0) return null
        val toughness = state.projectedState.getToughness(targetId) ?: return null
        val damageMarked = target.get<DamageComponent>()?.amount ?: 0
        val remainingToKill = (toughness - damageMarked).coerceAtLeast(1)

        fun pendingShotTarget(stackId: EntityId): EntityId? {
            val stackEntity = state.getEntity(stackId) ?: return null
            val activation = stackEntity.get<ActivatedAbilityOnStackComponent>() ?: return null
            if (activation.sourceId != action.sourceId || activation.sourceName != "Walking Ballista") return null
            return (stackEntity.get<TargetsComponent>()?.targets?.singleOrNull() as? ChosenTarget.Permanent)?.entityId
        }
        val queuedTargets = state.stack.mapNotNull(::pendingShotTarget)
        val queuedAtTarget = queuedTargets.count { it == targetId }
        val queuedElsewhere = queuedTargets.count { it != targetId }

        // Not a general damage penalty. Only defer the specifically demonstrated
        // "already building a shot stack, but cannot kill this creature even if we
        // remove every remaining Ballista counter" situation. Other tactical
        // windows, player shots, ordinary lethal removal, and no queued shots
        // preserve R4's unchanged score.
        if (queuedElsewhere == 0 || remainingToKill <= counters + queuedAtTarget) return null
        if (!context.passScore.isFinite() || !context.defaultScore.isFinite()) return null

        // Strategist may discount a priority pass after advisor scoring; keep a
        // small safety margin below the raw pass score rather than returning
        // null, which would incorrectly retain its high one-ply chip score.
        return minOf(context.defaultScore, context.passScore - 3.0)
    }
}
