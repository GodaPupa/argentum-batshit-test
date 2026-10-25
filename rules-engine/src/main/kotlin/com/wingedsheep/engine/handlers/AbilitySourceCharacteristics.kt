package com.wingedsheep.engine.handlers

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.BattlefieldEntryTimestampComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.EntitySnapshot
import com.wingedsheep.sdk.core.CardType
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId

/** Characteristics of the ability's original source, independent of the ability's controller. */
internal data class AbilitySourceCharacteristics(
    val colors: Set<Color> = emptySet(),
    val subtypes: Set<String> = emptySet(),
    val cardTypes: Set<String> = emptySet(),
    val supertypes: Set<String> = emptySet(),
    /** True only when captured identity proves the original source is no longer this visit. */
    val originalSourceDeparted: Boolean = false,
) {
    companion object {
        fun resolve(
            state: GameState,
            sourceId: EntityId,
            references: ObjectReferenceEnvironment,
            battlefieldTimestamp: Long?,
            snapshot: EntitySnapshot?,
        ): AbilitySourceCharacteristics {
            val entity = state.getEntity(sourceId)
            val ref = references.origin ?: snapshot?.objectRef
            val stamp = battlefieldTimestamp ?: snapshot?.battlefieldEntryTimestamp
            val liveOriginal = when {
                ref != null -> ref.entityId == sourceId && state.isCurrentObject(ref)
                stamp != null -> sourceId in state.getBattlefield() &&
                    entity?.get<BattlefieldEntryTimestampComponent>()?.timestamp == stamp
                snapshot != null || references.captured -> false // No proof that an unbound snapshot's source is still here.
                else -> entity != null // Legacy abilities without any captured source history.
            }
            if (liveOriginal) return live(state, sourceId)
            // A known departed visit without a snapshot has unknown characteristics. In particular,
            // a later visit must not fill missing fields in an old/partial serialized snapshot.
            return AbilitySourceCharacteristics(
                originalSourceDeparted = (ref != null && ref.entityId == sourceId && !state.isCurrentObject(ref)) ||
                    (ref == null && stamp != null && !liveOriginal),
                supertypes = snapshot?.supertypes ?: emptySet(),
                colors = snapshot?.colors?.mapNotNull { name -> Color.entries.find { it.name == name } }?.toSet() ?: emptySet(),
                subtypes = snapshot?.subtypes ?: emptySet(),
                cardTypes = snapshot?.typeLine?.cardTypes?.map { it.name }?.toSet() ?: emptySet(),
            )
        }

        private fun live(state: GameState, sourceId: EntityId): AbilitySourceCharacteristics {
            if (sourceId in state.getBattlefield()) {
                val projected = state.projectedState
                return AbilitySourceCharacteristics(
                    colors = projected.getColors(sourceId).mapNotNull { name -> Color.entries.find { it.name == name } }.toSet(),
                    subtypes = projected.getSubtypes(sourceId),
                    supertypes = projected.getSupertypes(sourceId),
                    cardTypes = projected.getTypes(sourceId).filter { name -> CardType.entries.any { it.name == name } }.toSet(),
                )
            }
            val card = state.getEntity(sourceId)?.get<CardComponent>()
            return AbilitySourceCharacteristics(
                colors = card?.colors ?: emptySet(),
                supertypes = card?.typeLine?.supertypes?.map { it.name }?.toSet() ?: emptySet(),
                subtypes = card?.typeLine?.subtypes?.map { it.value }?.toSet() ?: emptySet(),
                cardTypes = card?.typeLine?.cardTypes?.map { it.name }?.toSet() ?: emptySet(),
            )
        }
    }
}
