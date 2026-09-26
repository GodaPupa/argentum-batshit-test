package com.wingedsheep.engine.handlers.effects.token

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.mechanics.combat.CombatDefenders
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.Effect

/**
 * CR 508.4: an effect saying only "attacking" leaves each entering creature's defender to its
 * controller. A trigger's player, or the source creature's defender, does not specify that choice.
 * Like AuraTokenHostChooser, the resolution decision reuses the target-selection DTO without
 * targeting anything: there is no cast, target assignment, ward, or becomes-target event.
 */
internal object AttackingTokenDefenderChooser {
    fun legalDefenders(state: GameState, controllerId: EntityId): List<EntityId> =
        if (state.phase == Phase.COMBAT && state.isActiveTurnFor(controllerId)) {
            CombatDefenders.legalAttackDefenders(state, controllerId)
        } else emptyList() // CR 506.3b: a nonattacking player's creature enters without attacking.

    fun pause(
        state: GameState,
        effect: Effect,
        context: EffectContext,
        controllerId: EntityId,
        count: Int,
        legalDefenders: List<EntityId>,
    ): EffectResult {
        require(count > 0 && legalDefenders.size > 1)
        val continuation = AttackingTokenDefenderContinuation(
            effect, context, controllerId, count, legalDefenders,
        )
        return EffectResult.from(state.suspendForDecision(
            { id -> ChooseTargetsDecision(
                id = id,
                playerId = controllerId,
                prompt = "Choose what each entering token attacks",
                context = DecisionContext(
                    sourceId = context.sourceId,
                    sourceName = context.sourceId?.let { state.getEntity(it)?.get<CardComponent>()?.name },
                    phase = DecisionPhase.RESOLUTION,
                ),
                targetRequirements = (0 until count).map { index -> TargetRequirementInfo(
                    index = index,
                    description = "defender for token ${index + 1}",
                    minTargets = 1,
                    maxTargets = 1,
                ) },
                legalTargets = (0 until count).associateWith { legalDefenders },
            ) },
            continuation,
            emptyList(),
        ))
    }
}
