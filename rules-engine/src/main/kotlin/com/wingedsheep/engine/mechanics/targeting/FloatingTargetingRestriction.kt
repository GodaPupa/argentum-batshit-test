package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.EntityId

/** Shared by action enumeration, target selection, announcement and resolution revalidation. */
object FloatingTargetingRestriction {
    fun prevents(state: GameState, targetId: EntityId, targetingController: EntityId): Boolean =
        state.floatingEffects.any { floating ->
            val restriction = floating.effect.modification as? SerializableModification.PreventTargeting
                ?: return@any false
            targetId in floating.effect.affectedEntities && targetingController in restriction.controllers &&
                (restriction.targetObject == null || restriction.targetObject == state.objectRef(targetId))
        }
}
