package com.wingedsheep.engine.handlers.effects.stack

import com.wingedsheep.engine.core.ChangeSpellTargetContinuation
import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.DecisionHandler
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.effects.EffectExecutor
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.mechanics.targeting.GenericRetargeting
import com.wingedsheep.engine.mechanics.targeting.currentTargetPlan
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.sdk.scripting.effects.ChangeSpellTargetEffect
import kotlin.reflect.KClass

/**
 * Executor for ChangeSpellTargetEffect.
 * "If target spell has only one target and that target is a creature,
 * change that spell's target to another creature."
 *
 * Logic:
 * 1. Get the target spell from context
 * 2. Check the spell has exactly one target and that target is a creature
 * 3. If not, the effect does nothing (the "if" condition fails)
 * 4. Find all other creatures on the battlefield as legal new targets
 * 5. Present a selection decision to Meddle's controller
 * 6. Push ChangeSpellTargetContinuation
 */
class ChangeSpellTargetExecutor : EffectExecutor<ChangeSpellTargetEffect> {

    override val effectType: KClass<ChangeSpellTargetEffect> = ChangeSpellTargetEffect::class

    private val decisionHandler = DecisionHandler()

    override fun execute(
        state: GameState,
        effect: ChangeSpellTargetEffect,
        context: EffectContext
    ): EffectResult {
        var preparedState = state
        // 1. Get target spell
        val targetSpell = context.targets.firstOrNull()
        if (targetSpell !is ChosenTarget.Spell) {
            return EffectResult.error(preparedState, "No valid spell target for ChangeSpellTarget")
        }

        preparedState = GenericRetargeting.prepareLegacy(preparedState, targetSpell.spellEntityId)

        val spellEntity = preparedState.getEntity(targetSpell.spellEntityId)
            ?: return EffectResult.error(preparedState, "Spell not found on stack")

        if (spellEntity.get<SpellOnStackComponent>() == null) return EffectResult.success(preparedState)
        val singleTarget = spellEntity.currentTargetPlan()?.targets?.singleOrNull() ?: return EffectResult.success(preparedState)
        if (singleTarget !is ChosenTarget.Permanent) {
            return EffectResult.success(preparedState)
        }

        val projected = preparedState.projectedState

        // Check the target is a creature using projected types
        if (!projected.hasType(singleTarget.entityId, "CREATURE")) {
            return EffectResult.success(preparedState)
        }

        // If targetMustBeSource, verify the spell's target is the source of this effect
        if (effect.targetMustBeSource && singleTarget.entityId != context.sourceId) {
            return EffectResult.success(preparedState)
        }

        val snapshot = GenericRetargeting.capture(preparedState, targetSpell.spellEntityId, context.controllerId,
            creaturesOnly = true) ?: return EffectResult.success(preparedState)

        // 6. Present selection decision to the controller
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
            prompt = "Choose a new creature target",
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
}
