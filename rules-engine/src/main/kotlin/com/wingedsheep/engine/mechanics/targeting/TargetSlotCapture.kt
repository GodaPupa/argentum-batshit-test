package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId

/**
 * Capture a newly validated selection while its original requirement groups still exist.
 * An omitted optional response is empty in its declared position. This is not a validator
 * and must never reconstruct a historical selection from flat targets or current state.
 */
fun captureTargetSlots(
    state: GameState,
    requirementCount: Int,
    selected: Map<Int, List<ChosenTarget>>,
): TargetSlotPlan {
    require(requirementCount >= 0)
    require(selected.keys.all { it in 0 until requirementCount }) { "Unknown target requirement" }
    return TargetSlotPlan((0 until requirementCount).map { index ->
        TargetInstanceSelection(selected[index].orEmpty().map { target ->
            TargetSlot(target, if (target is ChosenTarget.Player) null else state.objectRef(targetId(target)))
        })
    })
}

/**
 * Bind an entity-keyed division only when each key identifies exactly one selected occurrence.
 * Unknown/ambiguous legacy input returns null; never duplicate one allocation across occurrences.
 * No GameState is read, so filling divisions cannot refresh visits captured before a pause.
 */
fun TargetSlotPlan.withUnambiguousDivision(division: Map<EntityId, Int>): TargetSlotPlan? {
    val ids = instances.flatMap { group -> group.slots.map { targetId(it.target) } }
    if (ids.size != ids.toSet().size || division.keys != ids.toSet() || division.values.any { it < 0 }) return null
    return copy(instances = instances.map { group ->
        group.copy(slots = group.slots.map { slot -> slot.copy(dividedAmount = division.getValue(targetId(slot.target))) })
    })
}

private fun targetId(target: ChosenTarget): EntityId = when (target) {
    is ChosenTarget.Permanent -> target.entityId
    is ChosenTarget.Player -> target.playerId
    is ChosenTarget.Card -> target.cardId
    is ChosenTarget.Spell -> target.spellEntityId
}
