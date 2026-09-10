package com.wingedsheep.engine.handlers.effects.permanent.control

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.RingBearerComponent
import com.wingedsheep.sdk.model.EntityId

/**
 * CR 701.54a: "That creature becomes your Ring-bearer until another creature becomes your
 * Ring-bearer or another player gains control of it." When control of a Ring-bearer actually
 * moves to a different player, the designation ends — permanently, not just for the duration of
 * the control change. So we strip [RingBearerComponent] eagerly here.
 *
 * Without this, a temporary control change (e.g. Threaten — "gain control until end of turn")
 * would suspend the designation while the bearer was under the opponent and then silently
 * resurrect it when control reverted at cleanup, which contradicts the rule.
 *
 * Call this from every executor that emits a [com.wingedsheep.engine.core.ControlChangedEvent]
 * after the floating effect is added and before returning the [com.wingedsheep.engine.core.EffectResult].
 */
internal fun clearRingBearerOnControlChange(
    state: GameState,
    permanentId: EntityId,
    newControllerId: EntityId
): GameState {
    val bearer = state.getEntity(permanentId)?.get<RingBearerComponent>() ?: return state
    if (bearer.ownerId == newControllerId) return state
    return state.updateEntity(permanentId) { it.without<RingBearerComponent>() }
}
