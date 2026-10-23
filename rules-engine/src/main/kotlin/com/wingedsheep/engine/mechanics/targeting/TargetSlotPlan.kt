package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/** Stable address within the original targeting instances, including empty optional instances. */
@Serializable
data class TargetSlotAddress(val instanceIndex: Int, val targetIndex: Int) {
    init {
        require(instanceIndex >= 0) { "Negative target-instance index" }
        require(targetIndex >= 0) { "Negative target-slot index" }
    }
}

/**
 * One chosen occurrence, not an entity-keyed map. A null reference is explicitly untracked
 * historical input; it must never be filled by looking up an unchanged target in current state.
 * Division stays with the occurrence when its recipient changes.
 */
@Serializable
data class TargetSlot(
    val target: ChosenTarget,
    val objectRef: ObjectRef? = null,
    val dividedAmount: Int? = null,
) {
    init {
        require(objectRef == null || objectRef.entityId == entityIdOf(target)) {
            "Captured object does not identify the chosen target"
        }
        require(target !is ChosenTarget.Player || objectRef == null) {
            "A player target has no zone-visit reference"
        }
        require(dividedAmount == null || dividedAmount >= 0) { "Negative divided amount" }
    }

    /** Unknown historical visits are compared conservatively, never silently refreshed. */
    fun sameRulesObjectAs(other: TargetSlot): Boolean =
        if (objectRef != null && other.objectRef != null) objectRef == other.objectRef
        else target == other.target
}

/** Exactly one targeting instance: repetitions are forbidden inside it, not across instances. */
@Serializable
data class TargetInstanceSelection(val slots: List<TargetSlot>) {
    init {
        for (index in slots.indices) {
            require(slots.take(index).none { it.sameRulesObjectAs(slots[index]) }) {
                "Duplicate target within one targeting instance"
            }
        }
    }
}

/**
 * Lossless internal plan for exactly-one-slot redirection. This is NOT a legality checker or
 * a GameState mutation: the caller must supply original targeting groups and captured visits,
 * validate the original stack object's controller/source restrictions and every final relation,
 * then apply the accepted change to the stack object's modal/splice views and emit its events.
 *
 * No target count, division, original group, or unchanged object reference is reconstructed.
 * Keeping an empty optional group is essential: flattening first loses its instance index.
 */
@Serializable
data class TargetSlotPlan(val instances: List<TargetInstanceSelection>) {
    val size: Int get() = instances.sumOf { it.slots.size }

    fun addresses(): List<TargetSlotAddress> = instances.flatMapIndexed { instance, selection ->
        selection.slots.indices.map { TargetSlotAddress(instance, it) }
    }

    fun slot(address: TargetSlotAddress): TargetSlot {
        require(address.instanceIndex in instances.indices) { "Unknown targeting instance" }
        val group = instances[address.instanceIndex]
        require(address.targetIndex in group.slots.indices) { "Unknown target slot" }
        return group.slots[address.targetIndex]
    }

    /**
     * Return a new plan, the same plan for an exact no-op, or null for an intra-instance duplicate.
     * All nonselected slots and the selected slot's division are preserved verbatim. This method
     * deliberately does not read current GameState, so it cannot refresh another slot's history.
     */
    fun replacingRecipient(
        address: TargetSlotAddress,
        recipient: ChosenTarget,
        capturedRecipient: ObjectRef?,
    ): TargetSlotPlan? {
        val original = slot(address)
        val replacement = original.copy(target = recipient, objectRef = capturedRecipient)
        if (replacement == original) return this
        val group = instances[address.instanceIndex]
        if (group.slots.withIndex().any {
            it.index != address.targetIndex && it.value.sameRulesObjectAs(replacement)
        }) return null
        val changedGroup = group.copy(slots = group.slots.mapIndexed { index, value ->
            if (index == address.targetIndex) replacement else value
        })
        return copy(instances = instances.mapIndexed { index, value ->
            if (index == address.instanceIndex) changedGroup else value
        })
    }
}

private fun entityIdOf(target: ChosenTarget): EntityId = when (target) {
    is ChosenTarget.Permanent -> target.entityId
    is ChosenTarget.Player -> target.playerId
    is ChosenTarget.Card -> target.cardId
    is ChosenTarget.Spell -> target.spellEntityId
}
