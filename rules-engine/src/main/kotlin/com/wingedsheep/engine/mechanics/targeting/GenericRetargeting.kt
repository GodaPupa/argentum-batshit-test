package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.core.EffectResult
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ObjectRef
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/** Immutable, server-authored single-occurrence choice, retained across the decision pause. */
@Serializable
data class GenericRetargetSnapshot(
    val stackRef: ObjectRef,
    val expected: CurrentTargetPlan,
    val chooserId: EntityId,
    val recipients: Map<EntityId, ChosenTarget>,
    val recipientRefs: Map<EntityId, ObjectRef>,
    val creaturesOnly: Boolean,
)

/** The generic redirect continuation delegates legality and mutation to the accepted adapter. */
internal object GenericRetargeting {
    fun capture(state: GameState, stackId: EntityId, chooserId: EntityId,
        playersOnly: Boolean = false, creaturesOnly: Boolean = false): GenericRetargetSnapshot? {
        val container = state.getEntity(stackId) ?: return null
        val expected = container.currentTargetPlan() ?: return null
        if (expected.slots.size != 1 || !expected.isCoherent(container)) return null
        val stackRef = state.objectRef(stackId) ?: return null
        val candidates = buildList<ChosenTarget> {
            if (!creaturesOnly) state.turnOrder.filter { state.hasEntity(it) }.forEach { add(ChosenTarget.Player(it)) }
            if (!playersOnly) {
                state.getBattlefield().filter { !creaturesOnly || state.projectedState.hasType(it, "CREATURE") }
                    .forEach { add(ChosenTarget.Permanent(it)) }
                if (!creaturesOnly) state.stack.forEach { add(ChosenTarget.Spell(it)) }
            }
        }
        val offers = candidates.mapNotNull { FixedRecipientRetargeting.offer(state, stackId, it, chooserId) }
            .filter { it.addresses.size == 1 }
        if (offers.isEmpty()) return null
        return GenericRetargetSnapshot(stackRef, expected, chooserId,
            offers.associate { targetEntityId(it.recipient) to it.recipient },
            offers.mapNotNull { offer -> offer.recipientRef?.let { targetEntityId(offer.recipient) to it } }.toMap(),
            creaturesOnly)
    }

    fun apply(state: GameState, snapshot: GenericRetargetSnapshot, selected: EntityId): EffectResult {
        val stackId = snapshot.stackRef.entityId
        if (stackId !in state.stack || !state.isCurrentObject(snapshot.stackRef)) return EffectResult.success(state)
        val container = state.getEntity(stackId) ?: return EffectResult.success(state)
        if (container.currentTargetPlan() != snapshot.expected || !snapshot.expected.isCoherent(container)) return EffectResult.success(state)
        val recipient = snapshot.recipients[selected] ?: return EffectResult.success(state)
        if (targetEntityId(recipient) != selected) return EffectResult.success(state)
        if (recipient !is ChosenTarget.Player &&
            snapshot.recipientRefs[selected]?.let { it.entityId == selected && state.isCurrentObject(it) } != true) return EffectResult.success(state)
        if (snapshot.creaturesOnly && !state.projectedState.hasType(selected, "CREATURE")) return EffectResult.success(state)
        // Revalidate legality now, but never recapture a newer declaration or a newer object visit.
        val offer = FixedRecipientRetargeting.offer(state, stackId, recipient, snapshot.chooserId) ?: return EffectResult.success(state)
        val address = offer.addresses.singleOrNull() ?: return EffectResult.success(state)
        return FixedRecipientRetargeting.apply(state, offer, address)
    }
}
