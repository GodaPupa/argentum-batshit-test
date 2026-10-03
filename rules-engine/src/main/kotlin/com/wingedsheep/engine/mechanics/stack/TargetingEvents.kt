package com.wingedsheep.engine.mechanics.stack

import com.wingedsheep.engine.core.BecomesTargetEvent
import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TargetedByControllerThisTurnComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.sdk.model.EntityId

/** Shared target declaration and target-change event emission. Legality belongs to the caller. */
object TargetingEvents {
    /**
     * Replace an existing stack object's targets and report each newly targeted entity once.
     * Keeping a target, including moving it between slots, does not make it become a target again.
     * The targeting source and controller are the original spell/ability, never the redirect effect.
     */
    fun replaceTargets(state: GameState, stackObjectId: EntityId, targets: List<ChosenTarget>): EffectResult {
        if (stackObjectId !in state.stack) return EffectResult.success(state)
        val objectEntity = state.getEntity(stackObjectId) ?: return EffectResult.success(state)
        val previous = objectEntity.get<TargetsComponent>() ?: return EffectResult.success(state)
        val spell = objectEntity.get<SpellOnStackComponent>()
        val controllerId = spell?.casterId
            ?: objectEntity.get<ActivatedAbilityOnStackComponent>()?.controllerId
            ?: objectEntity.get<TriggeredAbilityOnStackComponent>()?.controllerId
            ?: return EffectResult.success(state)
        if (previous.targets == targets) return EffectResult.success(state)

        val captured = TargetsComponent.capture(state, targets, previous.targetRequirements)
        // Retained targets keep their old object-identity stamps, even if that object has blinked.
        val retainedStamps = previous.targetEntryStamps.filterKeys { id ->
            targets.any { it is ChosenTarget.Permanent && it.entityId == id }
        }
        var updated = state.updateEntity(stackObjectId) {
            it.with(captured.copy(targetEntryStamps = captured.targetEntryStamps + retainedStamps))
        }
        val events = mutableListOf<GameEvent>()
        for (target in targets.distinct()) {
            if (target !in previous.targets) {
                updated = emit(updated, target, stackObjectId, controllerId, events, sourceIsSpell = spell != null)
            }
        }
        return EffectResult.success(updated, events)
    }

    fun emit(
        state: GameState,
        target: ChosenTarget,
        sourceEntityId: EntityId,
        controllerId: EntityId,
        events: MutableList<GameEvent>,
        sourceIsSpell: Boolean
    ): GameState {
        val isSpell = target is ChosenTarget.Spell
        val isPlayer = target is ChosenTarget.Player
        val targetEntityId = when (target) {
            is ChosenTarget.Permanent -> target.entityId
            is ChosenTarget.Spell -> target.spellEntityId
            is ChosenTarget.Player -> target.playerId
            is ChosenTarget.Card -> return state
        }
        val targetName = if (isPlayer) {
            state.getEntity(targetEntityId)?.get<PlayerComponent>()?.name ?: "Unknown"
        } else {
            state.getEntity(targetEntityId)?.get<CardComponent>()?.name ?: "Unknown"
        }
        val firstTime = isSpell || !hasBeenTargetedByController(state, targetEntityId, controllerId)
        events.add(
            BecomesTargetEvent(
                targetEntityId,
                targetName,
                sourceEntityId,
                controllerId,
                firstTime,
                targetIsSpell = isSpell,
                sourceIsSpell = sourceIsSpell,
                targetIsPlayer = isPlayer
            )
        )
        return if (isSpell) state else markTargetedByController(state, targetEntityId, controllerId)
    }

    // =========================================================================
    // Valiant / "first time targeted" tracking
    // =========================================================================

    /**
     * Check if the target entity has already been targeted by the given controller this turn.
     */
    private fun hasBeenTargetedByController(state: GameState, targetId: EntityId, controllerId: EntityId): Boolean {
        val component = state.getEntity(targetId)?.get<TargetedByControllerThisTurnComponent>()
        return component?.hasBeenTargetedBy(controllerId) == true
    }

    /**
     * Mark the target entity as having been targeted by the given controller this turn.
     */
    private fun markTargetedByController(state: GameState, targetId: EntityId, controllerId: EntityId): GameState {
        return state.updateEntity(targetId) { container ->
            val existing = container.get<TargetedByControllerThisTurnComponent>()
                ?: TargetedByControllerThisTurnComponent()
            container.with(existing.withController(controllerId))
        }
    }


}
