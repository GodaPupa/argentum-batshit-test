package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.handlers.TargetingSourceType
import com.wingedsheep.engine.mechanics.stack.TargetingEvents
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId

/** A fixed destination can replace one announced slot without reselecting any other target. */
object FixedDestinationRetarget {
    fun legalSlots(state: GameState, stackObjectId: EntityId, destination: ChosenTarget): List<Int> {
        if (stackObjectId !in state.stack) return emptyList()
        val entity = state.getEntity(stackObjectId) ?: return emptyList()
        val previous = entity.get<TargetsComponent>() ?: return emptyList()
        val counts = AnnouncedTargetGroups.counts(previous.targetRequirements, previous.targets.size,
            previous.announcedTargetCounts) ?: return emptyList()
        val spell = entity.get<SpellOnStackComponent>()
        val activated = entity.get<ActivatedAbilityOnStackComponent>()
        val triggered = entity.get<TriggeredAbilityOnStackComponent>()
        val controller = spell?.casterId ?: activated?.controllerId ?: triggered?.controllerId ?: return emptyList()
        val source = if (spell != null) stackObjectId else activated?.sourceId ?: triggered?.sourceId ?: return emptyList()
        val snapshot = activated?.lastKnownSourceSnapshot ?: triggered?.lastKnownSourceSnapshot
        if (spell == null && snapshot == null) {
            if (!state.hasEntity(source)) return emptyList()
            val references = activated?.objectReferences ?: triggered?.objectReferences
            if (references?.captured == true &&
                (references.origin == null || !state.isCurrentObject(references.origin))) return emptyList()
            val entry = activated?.sourceBattlefieldTimestamp ?: triggered?.sourceBattlefieldTimestamp
            if (entry != null && state.getEntity(source)
                    ?.get<com.wingedsheep.engine.state.components.battlefield.BattlefieldEntryTimestampComponent>()?.timestamp != entry) {
                return emptyList()
            }
        }
        // Partial historical snapshots cannot establish source-type targeting legality.
        if (snapshot != null && (snapshot.colors == null || snapshot.typeLine == null)) return emptyList()
        val projected = state.projectedState.getProjectedValues(source)
        val card = state.getEntity(source)?.get<CardComponent>()
        val colors = snapshot?.colors ?: projected?.colors ?: card?.colors?.map { it.name }?.toSet().orEmpty()
        val subtypes = snapshot?.subtypes ?: projected?.subtypes ?: card?.typeLine?.subtypes?.map { it.value }?.toSet().orEmpty()
        val validator = TargetValidator()
        return previous.targets.indices.filter { slot ->
            if (previous.targets[slot] == destination && previous.isCurrentSlot(state, slot)) return@filter false
            val replaced = previous.targets.toMutableList().also { it[slot] = destination }
            validator.validateTargets(state, replaced, previous.targetRequirements, controller,
                sourceColors = colors.mapNotNullTo(mutableSetOf()) { name -> Color.entries.firstOrNull { it.name == name } },
                sourceSubtypes = subtypes, sourceId = source, xValue = spell?.xValue ?: activated?.xValue ?: triggered?.xValue,
                targetingSourceType = if (spell != null) TargetingSourceType.SPELL else TargetingSourceType.ABILITY,
                announcedTargetCounts = counts, validateOnlySlots = setOf(slot), sourceSnapshot = snapshot) == null &&
                TargetingEvents.replaceTargets(state, stackObjectId, replaced, setOf(slot)).error == null
        }
    }

    /** Revalidates immediately before committing; stale or illegal choices leave all structures intact. */
    fun replaceSlot(state: GameState, stackObjectId: EntityId, slot: Int, destination: ChosenTarget): EffectResult {
        if (slot !in legalSlots(state, stackObjectId, destination)) return EffectResult.success(state)
        val previous = state.getEntity(stackObjectId)!!.get<TargetsComponent>()!!
        return TargetingEvents.replaceTargets(state, stackObjectId,
            previous.targets.toMutableList().also { it[slot] = destination }, setOf(slot))
    }
}
