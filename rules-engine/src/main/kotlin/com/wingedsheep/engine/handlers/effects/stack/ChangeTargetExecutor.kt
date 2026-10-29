package com.wingedsheep.engine.handlers.effects.stack

import com.wingedsheep.engine.core.ChangeSpellTargetContinuation
import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.DecisionHandler
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.mechanics.targeting.GenericRetargeting
import com.wingedsheep.engine.mechanics.targeting.currentTargetPlan
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.ChangeTargetEffect
import kotlin.reflect.KClass

/**
 * Executor for ChangeTargetEffect.
 * "Change the target of target spell or ability with a single target."
 *
 * Logic:
 * 1. Get the target spell/ability from context
 * 2. Check it has exactly one target — if not, the effect does nothing
 * 3. Find all legal new targets based on the spell/ability's target requirement
 * 4. Present a selection decision to the controller
 * 5. Push ChangeSpellTargetContinuation (reused)
 */
class ChangeTargetExecutor : EffectExecutor<ChangeTargetEffect> {

    override val effectType: KClass<ChangeTargetEffect> = ChangeTargetEffect::class

    private val decisionHandler = DecisionHandler()

    override fun execute(
        state: GameState,
        effect: ChangeTargetEffect,
        context: EffectContext
    ): EffectResult {
        var preparedState = state
        // 1. Get target spell/ability
        val targetSpell = context.targets.firstOrNull()
        if (targetSpell !is ChosenTarget.Spell) {
            return EffectResult.error(preparedState, "No valid spell/ability target for ChangeTarget")
        }

        preparedState = GenericRetargeting.prepareLegacy(preparedState, targetSpell.spellEntityId)

        val stackEntity = preparedState.getEntity(targetSpell.spellEntityId)
            ?: return EffectResult.error(preparedState, "Spell/ability not found on stack")

        val plan = stackEntity.currentTargetPlan() ?: return EffectResult.success(preparedState)
        val currentTarget = plan.targets.singleOrNull() ?: return EffectResult.success(preparedState)
        if (effect.onlyIfCurrentTargetIsController && getTargetEntityId(currentTarget) != context.controllerId) {
            return EffectResult.success(preparedState)
        }
        val snapshot = GenericRetargeting.capture(preparedState, targetSpell.spellEntityId, context.controllerId,
            playersOnly = effect.newTargetMustBePlayer) ?: return EffectResult.success(preparedState)

        // 4. Present selection decision to the controller
        val sourceName = context.sourceId?.let { preparedState.getEntity(it)?.get<CardComponent>()?.name }
        val continuation = ChangeSpellTargetContinuation(
            spellEntityId = targetSpell.spellEntityId,
            sourceId = context.sourceId,
            objectReferences = context.objectReferences,
            retargetSnapshot = snapshot
        )

        val decisionResult = decisionHandler.createCardSelectionDecision(
            state = preparedState,
            playerId = context.controllerId,
            sourceId = context.sourceId,
            sourceName = sourceName,
            prompt = "Choose a new target",
            options = snapshot.recipients.keys.toList(),
            minSelections = 1,
            maxSelections = 1,
            useTargetingUI = true,
            answer = continuation
        )

        return EffectResult.propagatePause(
            decisionResult.state,
            decisionResult.events
        )
    }

    private fun getTargetEntityId(target: ChosenTarget): EntityId? {
        return when (target) {
            is ChosenTarget.Permanent -> target.entityId
            is ChosenTarget.Player -> target.playerId
            is ChosenTarget.Card -> target.cardId
            is ChosenTarget.Spell -> target.spellEntityId
        }
    }
}
