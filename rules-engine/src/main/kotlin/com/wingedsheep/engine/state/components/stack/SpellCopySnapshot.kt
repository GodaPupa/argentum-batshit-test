package com.wingedsheep.engine.state.components.stack

import com.wingedsheep.engine.handlers.TargetFinder
import com.wingedsheep.engine.handlers.TargetingSourceType
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.CantBeCopiedComponent
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.targets.TargetRequirement
import kotlinx.serialization.Serializable

/** Copiable spell data at a particular stack visit; never a mutable runtime entity container. */
@Serializable
data class SpellCopySnapshot(
    val reference: ObjectRef,
    val card: CardComponent,
    val spell: SpellOnStackComponent,
    val targets: TargetsComponent? = null,
    val cantBeCopied: Boolean = false
) {
    /** Evaluate a prospective copy's targeting with its own characteristics, not a later source. */
    fun legalTargets(state: GameState, finder: TargetFinder, requirement: TargetRequirement, controller: EntityId): List<EntityId> {
        val (id, allocated) = state.newEntity()
        val view = allocated.withEntity(id, ComponentContainer.of(
            card.copy(ownerId = controller), spell.copy(casterId = controller)
        ))
        return finder.findLegalTargets(view, requirement, controller, id,
            targetingSourceType = TargetingSourceType.SPELL).filter { it != id }
    }

    companion object {
        fun capture(state: GameState, id: EntityId): SpellCopySnapshot? {
            val entity = state.getEntity(id) ?: return null
            val spell = entity.get<SpellOnStackComponent>() ?: return null
            val card = entity.get<CardComponent>() ?: return null
            val reference = state.objectRef(id) ?: state.initializeObjectIdentities().objectRef(id) ?: return null
            return SpellCopySnapshot(reference, card, spell, entity.get<TargetsComponent>(), entity.has<CantBeCopiedComponent>())
        }

        fun resolve(state: GameState, id: EntityId, reference: ObjectRef? = null, captured: Boolean = false): SpellCopySnapshot? {
            if (reference != null) {
                if (reference.entityId != id) return null
                if (state.isCurrentObject(reference) && id in state.stack) return capture(state, id)
                return state.departedSpellCopies[reference.generation]?.takeIf { it.reference == reference }
            }
            if (captured) return null
            // Legacy/synthetic callers without an object binding retain the latest departure only.
            return if (id in state.stack) capture(state, id) else state.departedSpellCopies.values
                .filter { it.reference.entityId == id }.maxByOrNull { it.reference.generation }
        }
    }
}
