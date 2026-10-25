package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.targets.TargetRequirement
import kotlinx.serialization.Serializable

/** Authoritative current occurrences. Never reconstruct this from flattened or original targets. */
@Serializable
data class CurrentTargetPlan(
    val selections: TargetSlotPlan,
    val modeInstanceCounts: List<Int> = emptyList(),
    val spliceInstanceCounts: List<Int> = emptyList(),
) {
    init {
        require(modeInstanceCounts.all { it >= 0 } && spliceInstanceCounts.all { it >= 0 })
        require(spliceInstanceCounts.sum() <= selections.instances.size)
        require(modeInstanceCounts.isEmpty() || modeInstanceCounts.sum() == mainInstanceCount)
    }
    val mainInstanceCount: Int get() = selections.instances.size - spliceInstanceCounts.sum()
    val slots: List<TargetSlot> get() = selections.instances.flatMap { it.slots }
    val targets: List<ChosenTarget> get() = slots.map { it.target }
    fun requirementIndices(): List<Int> = selections.instances.flatMapIndexed { i, group -> group.slots.map { i } }
    fun slice(start: Int, count: Int): TargetSlotPlan = TargetSlotPlan(selections.instances.drop(start).take(count))
    fun modePlans(): List<TargetSlotPlan> = slices(modeInstanceCounts, 0)
    fun splicePlans(): List<TargetSlotPlan> = slices(spliceInstanceCounts, mainInstanceCount)
    private fun slices(counts: List<Int>, start: Int): List<TargetSlotPlan> {
        var offset = start
        return counts.map { count -> slice(offset, count).also { offset += count } }
    }
    /** Reject stale flat or dependent views instead of silently falling back to legacy interpretation. */
    fun isCoherent(container: ComponentContainer): Boolean {
        val component = container.get<TargetsComponent>() ?: return false
        if (targets != component.targets || selections.instances.size != component.targetRequirements.size) return false
        val spell = container.get<SpellOnStackComponent>()
        val trigger = container.get<TriggeredAbilityOnStackComponent>()
        val modes = spell?.chosenModes ?: trigger?.chosenModes ?: emptyList()
        val modeTargets = spell?.modeTargetsOrdered ?: trigger?.modeTargetsOrdered ?: emptyList()
        if (modes.size != modeInstanceCounts.size || modeTargets != modePlans().map { it.flatTargets() }) return false
        val requirements = spell?.modeTargetRequirements ?: trigger?.modeTargetRequirements ?: emptyMap()
        var offset = 0
        modes.forEachIndexed { ordinal, mode ->
            val reqs = requirements[mode] ?: return false
            val count = modeInstanceCounts[ordinal]
            if (count != reqs.size || component.targetRequirements.drop(offset).take(count) != reqs) return false
            offset += count
        }
        if ((spell?.splicedCardNames?.size ?: 0) != spliceInstanceCounts.size) return false
        if (spell != null && spell.splicedTargetsOrdered != splicePlans().map { it.flatTargets() }) return false
        return true
    }
}

internal fun TargetSlotPlan.flatTargets(): List<ChosenTarget> = instances.flatMap { it.slots.map { s -> s.target } }

/** Frozen resolution result, carried through modal/splice pauses without reselecting or rechecking. */
@Serializable
data class ResolvedTargetView(
    val aligned: List<ChosenTarget?>,
    val named: Map<String, ChosenTarget>,
    val division: Map<EntityId, Int>?,
)

internal fun TargetSlotPlan.resolvedView(requirements: List<TargetRequirement>, aligned: List<ChosenTarget?>): ResolvedTargetView {
    require(requirements.size == instances.size && aligned.size == size)
    var offset = 0
    val named = linkedMapOf<String, ChosenTarget>()
    val division = linkedMapOf<EntityId, Int>()
    var divided = false
    instances.forEachIndexed { i, group ->
        group.slots.forEachIndexed { j, slot ->
            val live = aligned[offset + j]
            if (live != null) {
                requirements[i].id?.let { id -> named[if (requirements[i].count == 1) id else "$id[$j]"] = live }
                slot.dividedAmount?.let { amount ->
                    val id = targetEntityId(live)
                    division[id] = (division[id] ?: 0) + amount
                }
            }
            if (slot.dividedAmount != null) divided = true
        }
        offset += group.slots.size
    }
    return ResolvedTargetView(aligned, named, if (divided) division else null)
}

internal fun targetEntityId(target: ChosenTarget): EntityId = when (target) {
    is ChosenTarget.Permanent -> target.entityId
    is ChosenTarget.Player -> target.playerId
    is ChosenTarget.Card -> target.cardId
    is ChosenTarget.Spell -> target.spellEntityId
}

/**
 * Internal adapter for a caller that has already validated a target change. This supplies no
 * action, legality decision or targeting events. Expected-plan equality rejects serialized stale
 * choices. Original provenance is untouched; divisions and all dependent views move atomically.
 */
internal fun ComponentContainer.withCurrentTargetPlan(
    expected: CurrentTargetPlan?, replacement: CurrentTargetPlan,
): ComponentContainer? {
    val targets = get<TargetsComponent>() ?: return null
    if (targets.currentTargetPlan != expected) return null
    if (expected == null) {
        if (targets.targets != replacement.targets) return null
    } else {
        if (!expected.isCoherent(this)) return null
        if (expected.modeInstanceCounts != replacement.modeInstanceCounts ||
            expected.spliceInstanceCounts != replacement.spliceInstanceCounts) return null
        if (expected.selections.instances.map { it.slots.size } != replacement.selections.instances.map { it.slots.size }) return null
        if (expected.slots.map { it.dividedAmount } != replacement.slots.map { it.dividedAmount }) return null
    }
    var result = with(targets.copy(targets = replacement.targets, currentTargetPlan = replacement))
    fun division(plan: TargetSlotPlan): Map<EntityId, Int>? {
        if (plan.instances.flatMap { it.slots }.none { it.dividedAmount != null }) return null
        return plan.instances.flatMap { it.slots }.filter { it.dividedAmount != null }
            .groupBy { targetEntityId(it.target) }.mapValues { (_, slots) -> slots.sumOf { it.dividedAmount!! } }
    }
    get<SpellOnStackComponent>()?.let { spell ->
        val modeDivisions = linkedMapOf<Int, Map<EntityId, Int>>()
        replacement.modePlans().forEachIndexed { ordinal, mode ->
            division(mode)?.let { allocation ->
                val index = spell.chosenModes.getOrNull(ordinal) ?: return null
                if (modeDivisions.containsKey(index) && modeDivisions[index] != allocation) return null
                modeDivisions[index] = allocation
            }
        }
        result = result.with(spell.copy(
            modeTargetsOrdered = replacement.modePlans().map { it.flatTargets() },
            splicedTargetsOrdered = replacement.splicePlans().map { it.flatTargets() },
            damageDistribution = division(replacement.slice(0, replacement.mainInstanceCount)) ?: spell.damageDistribution,
            modeDamageDistribution = modeDivisions.ifEmpty { spell.modeDamageDistribution },
        ))
    }
    get<TriggeredAbilityOnStackComponent>()?.let { trigger ->
        result = result.with(trigger.copy(modeTargetsOrdered = replacement.modePlans().map { it.flatTargets() },
            damageDistribution = division(replacement.selections) ?: trigger.damageDistribution))
    }
    get<ActivatedAbilityOnStackComponent>()?.let { ability ->
        result = result.with(ability.copy(damageDistribution = division(replacement.selections) ?: ability.damageDistribution))
    }
    return result.takeIf { replacement.isCoherent(it) }
}
