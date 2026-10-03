package com.wingedsheep.engine.mechanics.layers

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.battlefield.AttachedToComponent
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

@Serializable
data class ProtectionAttachmentGrantKey(
    val source: ObjectRef, val host: ObjectRef, val grantIndex: Int,
    val timestamp: Long, val color: String, val controller: EntityId
)

@Serializable
data class ProtectionAttachmentActivation(
    val key: ProtectionAttachmentGrantKey, val epoch: Long,
    val attachments: Set<ObjectRef>
)

/**
 * Capture at an atomic transition, before another instruction can attach a new object.
 * Composite children reconcile separately; an enclosing batch preserves their earlier captures.
 * A legacy active grant with no captured history fails closed instead of sampling at legality time.
 */
object ProtectionAttachmentLifecycle {
    // Projection uses the current game timestamp as a layer-order fallback for legacy
    // fixtures. That moving fallback is not the identity of a protection instance.
    private fun sourceTimestamp(state: GameState, source: EntityId): Long =
        state.getEntity(source)?.get<com.wingedsheep.engine.state.components.battlefield.TimestampComponent>()?.timestamp ?: 0L

    private fun hasRetainingGrant(state: GameState): Boolean = state.getBattlefield().any { id ->
        state.getEntity(id)?.get<ContinuousEffectSourceComponent>()?.effects
            ?.any { it.retainsPreexistingControlledAttachments } == true
    }

    private fun grants(state: GameState): Set<ProtectionAttachmentGrantKey> {
        if (!hasRetainingGrant(state)) return emptySet()
        val projected = state.projectedState
        return buildSet {
            for (host in state.getBattlefield()) for (grant in projected.colorProtectionGrants(host)) {
                if (!grant.retainsPreexistingControlledAttachments) continue
                val sourceId = grant.sourceId ?: continue
                val leftHost = state.getEntity(sourceId)?.get<com.wingedsheep.engine.state.components.battlefield.AttachmentHostLeftComponent>()
                if (leftHost?.lastKnownHostId == host) continue
                val source = state.objectRef(sourceId) ?: continue
                val hostRef = state.objectRef(host) ?: continue
                val controller = grant.controllerId ?: continue
                add(ProtectionAttachmentGrantKey(source, hostRef, grant.protectionGrantIndex,
                    sourceTimestamp(state, sourceId), grant.color, controller))
            }
        }
    }

    fun reconcile(before: GameState, after: GameState): GameState {
        if (before === after) return after
        if (after.protectionAttachmentActivations.isEmpty() && !hasRetainingGrant(before) && !hasRetainingGrant(after)) return after
        val active = grants(after)
        val formerlyActive = grants(before)
        val existing = after.protectionAttachmentActivations.associateBy { it.key }
        var epoch = after.nextProtectionAttachmentEpoch
        val captures = active.mapNotNull { key ->
            val old = existing[key]
            if (old != null) old.copy(attachments = old.attachments.filterTo(linkedSetOf()) { ref ->
                after.objectRef(ref.entityId) == ref &&
                    after.getEntity(ref.entityId)?.get<AttachedToComponent>()?.targetId == key.host.entityId
            }) else if (key !in formerlyActive) {
                val projected = after.projectedState
                val eligible = after.getBattlefield().filter { id ->
                    after.getEntity(id)?.get<AttachedToComponent>()?.targetId == key.host.entityId &&
                        projected.getController(id) == key.controller &&
                        key.color in projected.getColors(id) &&
                        (projected.hasSubtype(id, "Aura") || projected.hasSubtype(id, "Equipment"))
                }.mapNotNull(after::objectRef).toSet()
                ProtectionAttachmentActivation(key, epoch++, eligible)
            } else null
        }
        if (captures == after.protectionAttachmentActivations && epoch == after.nextProtectionAttachmentEpoch) return after
        return after.copy(protectionAttachmentActivations = captures, nextProtectionAttachmentEpoch = epoch)
    }

    fun retains(state: GameState, host: EntityId, attachment: EntityId, grant: ColorProtectionGrant): Boolean {
        if (!grant.retainsPreexistingControlledAttachments) return false
        val source = grant.sourceId?.let(state::objectRef) ?: return false
        val hostRef = state.objectRef(host) ?: return false
        val controller = grant.controllerId ?: return false
        if (state.projectedState.getController(attachment) != controller) return false
        val key = ProtectionAttachmentGrantKey(source, hostRef, grant.protectionGrantIndex,
            sourceTimestamp(state, grant.sourceId!!), grant.color, controller)
        val ref = state.objectRef(attachment) ?: return false
        return state.protectionAttachmentActivations.any { it.key == key && ref in it.attachments }
    }
}
