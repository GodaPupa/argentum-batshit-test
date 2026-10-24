package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.scripting.targets.TargetRequirement

/** Grouped intent is preserved, but this component does not change protected resolution mapping. */
internal fun publicTargetLayoutError(groups: List<List<ChosenTarget>>?, requirements: List<TargetRequirement>): String? {
    if (groups == null) return null
    if (groups.size != requirements.size) return "Target groups do not match requirements"
    // A changed trailing width does not shift a later chosen group. Cardinality remains
    // the validator's responsibility. An earlier changed width needs occurrence-aware resolution.
    if (groups.indices.any { index ->
        groups[index].size != requirements[index].count &&
            groups.drop(index + 1).any { it.isNotEmpty() }
    }) return "Grouped target layout requires occurrence-aware resolution"
    return null
}

/** Bind an already captured public plan without consulting current object visits. */
internal fun publicSpellTargetPlan(action: com.wingedsheep.engine.core.CastSpell): TargetSlotPlan? =
    action.originalTargetSlots?.takeIf { plan ->
        action.targetGroups?.flatten() == action.targets &&
            plan.instances.map { group -> group.slots.map { it.target } } == action.targetGroups
    }?.let { plan -> action.damageDistribution?.let { plan.withUnambiguousDivision(it) } ?: plan.takeIf { action.damageDistribution == null } }

internal fun attachPublicSpellTargetPlan(
    state: com.wingedsheep.engine.state.GameState,
    spellId: com.wingedsheep.sdk.model.EntityId,
    plan: TargetSlotPlan?,
): com.wingedsheep.engine.state.GameState {
    if (plan == null || spellId !in state.stack) return state
    val spell = state.getEntity(spellId)?.get<com.wingedsheep.engine.state.components.stack.SpellOnStackComponent>() ?: return state
    if (plan.instances.flatMap { group -> group.slots.map { it.target } } != (state.getEntity(spellId)?.get<com.wingedsheep.engine.state.components.stack.TargetsComponent>()?.targets ?: emptyList())) return state
    return state.updateEntity(spellId) { it.with(spell.copy(originalTargetSlots = plan)) }
}
