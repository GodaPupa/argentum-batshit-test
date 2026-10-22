package com.wingedsheep.engine.event

import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.core.Zone

/**
 * Observe one completed entry instruction, including all simultaneous coentrants. Child
 * instructions retain their observations when a composite returns, including an explicitly
 * empty observation. Nothing is stacked and no target or ordering decision is made here.
 */
fun captureBattlefieldEntryObservations(
    state: GameState,
    events: List<GameEvent>,
    detector: TriggerDetector,
): List<GameEvent> {
    val entries = events.filterIsInstance<ZoneChangeEvent>().filter {
        it.toZone == Zone.BATTLEFIELD && !it.entryTriggersAlreadyProcessed &&
            it.observedEntryTriggers == null
    }
    if (entries.isEmpty()) return events
    val observed = detector.detectEntryInstructionTriggers(state, entries, events)
    val causes = java.util.Collections.newSetFromMap(
        java.util.IdentityHashMap<GameEvent, Boolean>(),
    ).apply { addAll(entries) }
    return events.map { event ->
        if (event is ZoneChangeEvent && event in causes) {
            event.copy(observedEntryTriggers = if (event === entries.first()) observed else emptyList())
        } else event
    }
}
