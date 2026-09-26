package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.battlefield.LastKnownPermanentComponent
import com.wingedsheep.engine.state.components.stack.ActivatedAbilityOnStackComponent
import com.wingedsheep.engine.state.components.stack.EntitySnapshot
import com.wingedsheep.engine.state.components.stack.TriggeredAbilityOnStackComponent
import com.wingedsheep.gym.contract.EntityFeatures
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/** Public identity of a rules object, copied without the authoritative identity tables. */
@Serializable
data class ActorObjectIdentity(val entityId: EntityId, val generation: Long)

@Serializable
enum class ActorSourceMode { LIVE_BATTLEFIELD, DEPARTED_BATTLEFIELD }

/** Only characteristics needed to interpret the public source; no definition, script or snapshot. */
@Serializable
data class ActorSourceCharacteristics(
    val name: String,
    val controllerId: EntityId?,
    val ownerId: EntityId,
    val power: Int?,
    val toughness: Int?,
    val colors: Set<String>,
    val types: Set<String>,
    val subtypes: Set<String>,
    val keywords: Set<String>,
    val faceDown: Boolean,
) {
    val deathtouch: Boolean get() = "DEATHTOUCH" in keywords
    val lifelink: Boolean get() = "LIFELINK" in keywords
}

@Serializable
data class ActorStackSource(
    val origin: ActorObjectIdentity,
    val mode: ActorSourceMode,
    val originalObjectIsCurrent: Boolean,
    /** Present only for an actor-visible visit to a public zone; never a hand/library generation. */
    val currentVisibleObject: ActorObjectIdentity?,
    /** No battlefield characteristics are fabricated for a source outside the qualified pool. */
    val characteristics: ActorSourceCharacteristics?,
)

/**
 * Trusted projection for an ability's actual source. SourceWasOnBattlefield/sourceSnapshot on
 * damage events describe past damage; pending stack abilities instead carry the captured origin,
 * battlefield timestamp and lastKnownSourceSnapshot read here. No event history or raw context is
 * exposed. The selected-source pool uses source damage; named other-source damage requires its
 * own qualified projection before a pilot reasons about that different source.
 */
internal fun projectStackSource(
    state: GameState,
    stackEntity: ComponentContainer,
    visibleCards: Map<EntityId, EntityFeatures>,
): ActorStackSource? {
    val activated = stackEntity.get<ActivatedAbilityOnStackComponent>()
    val triggered = stackEntity.get<TriggeredAbilityOnStackComponent>()
    if (activated == null && triggered == null) return null
    val sourceId = activated?.sourceId ?: triggered?.sourceId ?: return null
    val references = activated?.objectReferences ?: requireNotNull(triggered).objectReferences
    val origin = references.origin
        ?: fail(BoundaryFailure.INCOMPLETE_INPUT, "Stack ability has no captured source object identity")
    if (!references.captured || origin.entityId != sourceId) {
        fail(BoundaryFailure.INCOMPLETE_INPUT, "Stack ability source identity does not match its captured origin")
    }
    val isCurrent = state.isCurrentObject(origin)
    val currentZone = state.logicalZone(sourceId)?.zoneType
    val currentVisible = if (sourceId in visibleCards && currentZone in PUBLIC_SOURCE_IDENTITY_ZONES)
        state.objectRef(sourceId)?.actorCopy() else null
    val snapshot = listOfNotNull(
        activated?.lastKnownSourceSnapshot ?: triggered?.lastKnownSourceSnapshot,
        state.getEntity(sourceId)?.get<LastKnownPermanentComponent>()?.snapshot,
    ).firstOrNull { it.objectRef == origin }
    val battlefieldTimestamp = activated?.sourceBattlefieldTimestamp ?: triggered?.sourceBattlefieldTimestamp
    val wasBattlefield = battlefieldTimestamp != null || snapshot != null || (isCurrent && currentZone == Zone.BATTLEFIELD)
    // A hand/library object's global generation can encode private acquisition history. The
    // stack ability publicly identifies its physical source, but does not reveal that history.
    // Until a separate public identity scheme is qualified for these sources, expose no summary.
    if (!wasBattlefield) return null
    if (isCurrent && currentZone == Zone.BATTLEFIELD) {
        val card = visibleCards[sourceId]
            ?: fail(BoundaryFailure.INCOMPLETE_INPUT, "Live battlefield source has no actor-visible projection")
        val owner = card.ownerId
            ?: fail(BoundaryFailure.INCOMPLETE_INPUT, "Live battlefield source lacks public ownership")
        val controller = card.controllerId
            ?: fail(BoundaryFailure.INCOMPLETE_INPUT, "Live battlefield source lacks public controller")
        return ActorStackSource(origin.actorCopy(), ActorSourceMode.LIVE_BATTLEFIELD, true, currentVisible,
            ActorSourceCharacteristics(card.name, controller, owner, card.power, card.toughness,
                card.colors.toSortedSet(), card.types.toSortedSet(), card.subtypes.toSortedSet(),
                card.keywords.toSortedSet(), card.faceDown))
    }
    if (isCurrent) fail(BoundaryFailure.INCOMPLETE_INPUT, "Battlefield source origin remained current outside the battlefield")
    val departed = snapshot
        ?: fail(BoundaryFailure.INCOMPLETE_INPUT, "Departed battlefield source lacks its exact last-known snapshot")
    return ActorStackSource(origin.actorCopy(), ActorSourceMode.DEPARTED_BATTLEFIELD, false, currentVisible,
        departed.publicCharacteristics())
}

private fun EntitySnapshot.publicCharacteristics(): ActorSourceCharacteristics {
    val publicTypes = typeLine?.cardTypes?.map { it.name }?.toSortedSet()
        ?: fail(BoundaryFailure.INCOMPLETE_INPUT, "Departed source snapshot lacks projected types")
    val publicColors = colors?.toSortedSet()
        ?: fail(BoundaryFailure.INCOMPLETE_INPUT, "Departed source snapshot lacks projected colors")
    val owner = ownerId ?: fail(BoundaryFailure.INCOMPLETE_INPUT, "Departed source snapshot lacks ownership")
    val controller = controllerId ?: fail(BoundaryFailure.INCOMPLETE_INPUT, "Departed battlefield source snapshot lacks controller")
    val publicName = if (wasFaceDown) "Face-down creature" else name
        ?: fail(BoundaryFailure.INCOMPLETE_INPUT, "Departed source snapshot lacks its public name")
    return ActorSourceCharacteristics(publicName, controller, owner, power, toughness, publicColors,
        publicTypes, subtypes.toSortedSet(), keywords.toSortedSet(), wasFaceDown)
}

private fun ObjectRef.actorCopy() = ActorObjectIdentity(entityId, generation)

private val PUBLIC_SOURCE_IDENTITY_ZONES = setOf(Zone.BATTLEFIELD, Zone.GRAVEYARD, Zone.EXILE, Zone.STACK, Zone.COMMAND)
