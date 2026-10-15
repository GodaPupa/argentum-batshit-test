package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.mechanics.layers.ActiveFloatingEffect
import com.wingedsheep.engine.mechanics.layers.SerializableModification
import com.wingedsheep.engine.mechanics.sba.permanent.EndedDurationExpiryCheck
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.EntityId

/** Shared by action enumeration, target selection, announcement and resolution revalidation. */
object FloatingTargetingRestriction {
    private val durationGate = EndedDurationExpiryCheck()

    /** Share the expiry latch's pure live-duration predicate, including mid-resolution reads. */
    fun appliesTo(state: GameState, floating: ActiveFloatingEffect, targetId: EntityId): Boolean {
        val restriction = floating.effect.modification as? SerializableModification.PreventTargeting
            ?: return false
        if (targetId !in floating.effect.affectedEntities) return false
        if (restriction.targetObject != null && restriction.targetObject != state.objectRef(targetId)) return false
        return targetId in durationGate.activeAffectedEntities(state, state.projectedState, floating)
    }

    fun prevents(state: GameState, targetId: EntityId, targetingController: EntityId): Boolean =
        state.floatingEffects.any { floating ->
            val restriction = floating.effect.modification as? SerializableModification.PreventTargeting
                ?: return@any false
            targetingController in restriction.controllers && appliesTo(state, floating, targetId)
        }
}
