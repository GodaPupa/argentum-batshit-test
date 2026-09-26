package com.wingedsheep.gym.ferocity

import com.wingedsheep.engine.core.ChooseOptionDecision
import com.wingedsheep.engine.core.DecisionContext
import com.wingedsheep.engine.core.DecisionPhase
import com.wingedsheep.engine.core.PendingDecision
import com.wingedsheep.engine.core.Suspension
import com.wingedsheep.engine.core.TriggerOrderingContinuation
import com.wingedsheep.engine.event.PendingTrigger
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.LastKnownPermanentComponent
import com.wingedsheep.gym.contract.EntityFeatures
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/**
 * Trusted, nonserializable proof. Its constructor is private; no arbitrary sourceName argument
 * can bless source-free metadata. Neither this proof nor its continuation enters ActorInput.
 */
internal class VerifiedTriggerOrderQuestion private constructor(private val question: ChooseOptionDecision) {
    fun sourceNameFor(current: PendingDecision): String {
        if (current != question) fail(BoundaryFailure.INCOMPLETE_INPUT, "Trigger-order proof belongs to another question")
        return LABEL
    }

    companion object {
        private const val LABEL = "Triggered abilities"
        private const val PROMPT = "Choose the next triggered ability to put on the stack (first chosen resolves last)"
        private val PUBLIC_CARD_ZONES = setOf(Zone.BATTLEFIELD, Zone.GRAVEYARD, Zone.EXILE, Zone.STACK)

        fun inspect(
            state: GameState,
            decision: PendingDecision,
            visible: Map<EntityId, EntityFeatures>,
        ): VerifiedTriggerOrderQuestion? {
            val suspension = state.continuationStack.lastOrNull() as? Suspension
            val ordering = suspension?.answer as? TriggerOrderingContinuation
            if (ordering == null) {
                if (decision.context.sourceId == null && decision.context.sourceName == LABEL) {
                    fail(BoundaryFailure.INCOMPLETE_INPUT, "Unproved source-free trigger-order label")
                }
                return null
            }
            if (suspension?.question != decision || state.pendingDecision != decision || decision !is ChooseOptionDecision) {
                fail(BoundaryFailure.INCOMPLETE_INPUT, "Trigger-order question does not match its active suspension")
            }
            val all = ordering.chosen + ordering.remaining
            if (ordering.remaining.size < 2 || decision.playerId !in state.turnOrder ||
                all.any { it.controllerId != decision.playerId }) {
                fail(BoundaryFailure.INCOMPLETE_INPUT, "Trigger-order group or controller is inconsistent")
            }
            // The current engine's generator uses public source/event names and the actual
            // pending ability's rule description. Reconstruct its complete choice shape only
            // after proving each source name is public; never accept a caller-supplied label.
            val options = ordering.remaining.mapIndexed { index, trigger ->
                val source = publicSourceName(state, trigger, visible)
                val eventObject = trigger.triggerContext.triggeringEntityId?.takeIf { it in state.getBattlefield() }
                val eventName = eventObject?.let { id ->
                    val card = visible[id] ?: fail(BoundaryFailure.INCOMPLETE_INPUT, "Trigger event object has no public view")
                    if (card.faceDown) "Face-down creature" else card.name
                }
                "${index + 1}. $source${eventName?.let { " ($it)" } ?: ""}: ${trigger.ability.description}"
            }
            val previews = ordering.remaining.mapIndexedNotNull { index, trigger ->
                trigger.sourceId?.takeIf { it in state.getBattlefield() }?.let { index to listOf(it) }
            }.toMap().takeIf { it.isNotEmpty() }
            val expected = ChooseOptionDecision(
                decision.id, decision.playerId, PROMPT,
                DecisionContext(sourceId = null, sourceName = LABEL, phase = DecisionPhase.CASTING),
                options, optionCardIds = previews,
            )
            if (decision != expected) {
                fail(BoundaryFailure.INCOMPLETE_INPUT, "Trigger-order public question differs from its typed choice group")
            }
            return VerifiedTriggerOrderQuestion(decision)
        }

        private fun publicSourceName(state: GameState, trigger: PendingTrigger, visible: Map<EntityId, EntityFeatures>): String {
            val id = trigger.sourceId
                ?: fail(BoundaryFailure.UNSUPPORTED_PUBLIC_MECHANIC, "Inherent trigger ordering needs separate public-source qualification")
            val origin = trigger.objectReferences.origin
            if (!trigger.objectReferences.captured || origin == null || origin.entityId != id) {
                fail(BoundaryFailure.INCOMPLETE_INPUT, "Trigger-order source has no exact captured object")
            }
            if (trigger.carriedPipeline != null || trigger.consumesDelayedTriggerId != null || trigger.sagaChapterInfo != null) {
                fail(BoundaryFailure.UNSUPPORTED_PUBLIC_MECHANIC, "Dynamic trigger-order descriptions need separate qualification")
            }
            val current = state.isCurrentObject(origin)
            val zone = state.logicalZone(id)?.zoneType
            val snapshot = listOfNotNull(trigger.triggerContext.triggeringSnapshot,
                state.getEntity(id)?.get<LastKnownPermanentComponent>()?.snapshot)
                .firstOrNull { it.objectRef == origin }
            val name = if (current && zone in PUBLIC_CARD_ZONES) {
                val card = visible[id]
                    ?: fail(BoundaryFailure.INCOMPLETE_INPUT, "Trigger-order source has no authorized public card view")
                if (card.faceDown) fail(BoundaryFailure.UNSUPPORTED_PUBLIC_MECHANIC, "Hidden trigger-source text is not qualified")
                card.name
            } else if (!current && snapshot != null) {
                if (snapshot.wasFaceDown) fail(BoundaryFailure.UNSUPPORTED_PUBLIC_MECHANIC, "Hidden departed trigger-source text is not qualified")
                snapshot.name ?: fail(BoundaryFailure.INCOMPLETE_INPUT, "Departed trigger-order source lacks a public name")
            } else {
                fail(BoundaryFailure.INCOMPLETE_INPUT, "Trigger-order source is private, missing or lacks exact public departure proof")
            }
            if (name.isBlank() || name != trigger.sourceName) {
                fail(BoundaryFailure.INCOMPLETE_INPUT, "Trigger-order source label contradicts its public identity")
            }
            return name
        }
    }
}
