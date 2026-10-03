package com.wingedsheep.engine.mechanics.sba.creature

import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.handlers.effects.ZoneMovementUtils
import com.wingedsheep.engine.mechanics.sba.SbaOrder
import com.wingedsheep.engine.mechanics.sba.SbaZoneMovementHelper
import com.wingedsheep.engine.mechanics.sba.StateBasedActionCheck
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.DamageComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Keyword

/**
 * 704.5g - A creature that's been dealt lethal damage is destroyed.
 * 704.5h - A creature that's been dealt damage by a source with deathtouch is destroyed.
 * Note: Indestructible creatures are not destroyed by lethal damage (Rule 702.12b).
 * Creatures with regeneration shields are regenerated instead of destroyed.
 */
class LethalDamageCheck : StateBasedActionCheck {
    override val name = "704.5g/h Lethal Damage"
    override val order = SbaOrder.LETHAL_DAMAGE

    override fun check(state: GameState): ExecutionResult = check(state, state)

    override fun check(state: GameState, passStartState: GameState): ExecutionResult {
        val lethal = mutableListOf<com.wingedsheep.engine.state.ObjectRef>()
        val projected = state.projectedState

        for (entityId in state.getBattlefield().toList()) {
            val container = state.getEntity(entityId) ?: continue
            val cardComponent = container.get<CardComponent>() ?: continue
            val damageComponent = container.get<DamageComponent>() ?: continue

            if (!projected.isCreature(entityId)) continue

            if (projected.hasKeyword(entityId, Keyword.INDESTRUCTIBLE)) continue

            val effectiveToughness = projected.getToughness(entityId) ?: 0

            val hasLethalDamage = damageComponent.amount >= effectiveToughness
            // CR 704.5h — destroyed if dealt damage by a deathtouch source, regardless of the
            // form that damage took. `deathtouchDamageReceived` is only ever set when a deathtouch
            // source actually dealt nonzero damage (marked, or as wither -1/-1 counters), so no
            // separate `amount > 0` guard is needed — and requiring marked `amount > 0` would
            // wrongly spare a creature whose deathtouch damage arrived as wither counters (which
            // are not marked damage, CR 702.80a).
            val hasDeathtouch = damageComponent.deathtouchDamageReceived

            if (hasLethalDamage || hasDeathtouch) {
                state.objectRef(entityId)?.let { lethal += it }
            }
        }

        return destroyRemaining(state, lethal, passStartState)
    }

    fun destroyRemaining(
        state: GameState,
        remaining: List<com.wingedsheep.engine.state.ObjectRef>,
        passStartState: GameState
    ): ExecutionResult {
        var current = state
        val events = mutableListOf<com.wingedsheep.engine.core.GameEvent>()
        for ((index, ref) in remaining.withIndex()) {
            if (!current.isCurrentObject(ref) || ref.entityId !in current.getBattlefield()) continue
            val card = current.getEntity(ref.entityId)?.get<CardComponent>() ?: continue
            val frame = com.wingedsheep.engine.core.LethalDestructionContinuation(remaining.drop(index + 1), passStartState)
            val queued = current.pushContinuation(frame)
            val replacement = com.wingedsheep.engine.handlers.effects.DestructionReplacements.replace(
                queued, ref.entityId, canRegenerate = true, byEffect = false)
            val result = replacement?.toExecutionResult() ?: SbaZoneMovementHelper.putCreatureInGraveyard(
                queued, ref.entityId, card, "lethal damage", passStartState)
            events += result.events
            if (result.isPaused) return ExecutionResult.propagatePause(result.state, events)
            if (result.error != null) return result.copy(events = events)
            // The remainder frame belongs to this synchronous operation; only a pause retains it.
            check(result.state.peekContinuation() == frame)
            current = result.state.popContinuation().second
        }
        return ExecutionResult.success(current, events)
    }
}
