package com.wingedsheep.engine.handlers.effects

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.EffectContext
import com.wingedsheep.engine.handlers.TargetFinder
import com.wingedsheep.engine.handlers.effects.library.MoveCollectionExecutor
import com.wingedsheep.engine.handlers.effects.zones.MoveToZoneEffectExecutor
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.effects.MoveCollectionEffect
import com.wingedsheep.sdk.scripting.effects.MoveToZoneEffect

/** Commit only the captured source incarnations, through the original effect executors. */
object PendingEntryCommitter {
    fun commit(
        state: GameState,
        plan: PendingZoneEntryPlan,
        cardRegistry: CardRegistry,
        targetFinder: TargetFinder,
        effectExecutor: (GameState, Effect, EffectContext) -> EffectResult,
    ): EffectResult {
        val current = plan.entries.filter { state.objectRef(it.source.entityId) == it.source }
        var result = when (val effect = plan.effect) {
            is MoveToZoneEffect -> if (current.isEmpty()) EffectResult.success(state) else
                MoveToZoneEffectExecutor(cardRegistry, targetFinder, effectExecutor, plan.copy(entries = current))
                    .execute(state, effect, plan.context)
            is MoveCollectionEffect -> MoveCollectionExecutor(
                cardRegistry, targetFinder, current.associateBy { it.source.entityId }, state,
            ).execute(state, effect, plan.context)
            else -> return EffectResult.error(state, "Unsupported pending entry operation")
        }
        // These executors are called directly after a saved choice, outside the registry's
        // instruction boundary. Freeze the complete batch before the copy's exile rider or
        // the enclosing pipeline can change what entry characteristic filters observe.
        result = result.copy(events = com.wingedsheep.engine.event.captureBattlefieldEntrySnapshots(
            result.state, result.events,
        ))
        result = result.copy(events = com.wingedsheep.engine.event.captureBattlefieldEntryObservations(
            result.state, result.events, com.wingedsheep.engine.event.TriggerDetector(cardRegistry),
        ))
        // The copied graveyard rider is tied both to a real arrival and to the chosen old object.
        // It cannot exile a later visit of the same physical card after a saved choice resumes.
        for (entry in current) {
            val exile = entry.exileAfterEntry ?: continue
            val entered = result.events.filterIsInstance<ZoneChangeEvent>().any {
                it.entityId == entry.source.entityId && it.oldObject == entry.source && it.toZone == Zone.BATTLEFIELD
            }
            if (entered && result.state.objectRef(exile.entityId) == exile) {
                val moved = ZoneTransitionService.moveToZone(result.state, exile.entityId, Zone.EXILE)
                result = result.copy(state = moved.state, events = result.events + moved.events)
            }
        }
        return result
    }
}
