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

        // Target-changing effects replace slots, not the number of announced targets.
        if (targets.size != previous.targets.size) return EffectResult.error(state, "Target slot count cannot change")
        val spliceCount = spell?.splicedTargetsOrdered?.sumOf { it.size } ?: 0
        val mainCount = previous.targets.size - spliceCount
        if (mainCount < 0) return EffectResult.error(state, "Invalid splice target structure")
        fun rebind(slices: List<List<ChosenTarget>>, offset: Int): List<List<ChosenTarget>>? {
            if (slices.isEmpty()) return emptyList()
            val flat = slices.flatten()
            if (offset < 0 || offset + flat.size > previous.targets.size ||
                previous.targets.subList(offset, offset + flat.size) != flat) return null
            var cursor = offset
            return slices.map { slice -> targets.subList(cursor, cursor + slice.size).also { cursor += slice.size }.toList() }
        }
        val reboundModes = rebind(spell?.modeTargetsOrdered.orEmpty(), 0)
            ?: return EffectResult.error(state, "Modal target structure does not match announced slots")
        val reboundSplices = rebind(spell?.splicedTargetsOrdered.orEmpty(), mainCount)
            ?: return EffectResult.error(state, "Splice target structure does not match announced slots")
        fun id(target: ChosenTarget): EntityId = when (target) {
            is ChosenTarget.Player -> target.playerId
            is ChosenTarget.Permanent -> target.entityId
            is ChosenTarget.Spell -> target.spellEntityId
            is ChosenTarget.Card -> target.cardId
        }
        fun remapAllocation(allocation: Map<EntityId, Int>): Map<EntityId, Int>? {
            val result = linkedMapOf<EntityId, Int>()
            for ((oldId, amount) in allocation) {
                val slots = previous.targets.indices.filter { id(previous.targets[it]) == oldId }
                val destinations = slots.map { id(targets[it]) }.distinct()
                if (destinations.size != 1) return null
                val destination = destinations.single()
                // A target-keyed allocation cannot represent merging or splitting two slots.
                if (destination in result) return null
                result[destination] = amount
            }
            return result
        }
        val allocation = spell?.damageDistribution?.let {
            remapAllocation(it) ?: return EffectResult.error(state, "Ambiguous divided target allocation")
        }
        val modeAllocations = spell?.modeDamageDistribution.orEmpty().mapValues { (_, allocation) ->
            remapAllocation(allocation) ?: return EffectResult.error(state, "Ambiguous modal divided allocation")
        }
        val captured = TargetsComponent.capture(state, targets, previous.targetRequirements, previous.announcedTargetCounts)
        // Retained targets keep their old object-identity stamps, even if that object has blinked.
        val retainedStamps = previous.targetEntryStamps.filterKeys { id ->
            targets.any { it is ChosenTarget.Permanent && it.entityId == id }
        }
        var updated = state.updateEntity(stackObjectId) {
            var entity = it.with(captured.copy(targetEntryStamps = captured.targetEntryStamps + retainedStamps))
            if (spell != null) entity = entity.with(spell.copy(
                modeTargetsOrdered = reboundModes,
                splicedTargetsOrdered = reboundSplices,
                damageDistribution = allocation,
                modeDamageDistribution = modeAllocations
            ))
            entity
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
