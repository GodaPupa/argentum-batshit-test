package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.AbilitySourceCharacteristics
import com.wingedsheep.engine.handlers.TargetingSourceType
import com.wingedsheep.engine.state.*
import com.wingedsheep.engine.state.components.battlefield.TargetedByControllerThisTurnComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.ControllerComponent
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.engine.state.components.stack.*
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/** Server-authored occurrence menu. A response chooses an address, never a flattened target. */
@Serializable
internal data class FixedRecipientOffer(
    val stackRef: ObjectRef,
    val expected: CurrentTargetPlan,
    val recipient: ChosenTarget,
    val recipientRef: ObjectRef?,
    val chooserId: EntityId,
    val addresses: List<TargetSlotAddress>,
)

/** One existing authoritative occurrence is changed; no original declaration is reconstructed. */
internal object FixedRecipientRetargeting {
    private val validator = TargetValidator()

    fun offer(state: GameState, stackId: EntityId, recipient: ChosenTarget, chooserId: EntityId): FixedRecipientOffer? {
        val ref = state.objectRef(stackId) ?: return null
        val container = state.getEntity(stackId) ?: return null
        val plan = container.currentTargetPlan() ?: return null
        if (stackId !in state.stack || !plan.isCoherent(container)) return null
        val recipientRef = if (recipient is ChosenTarget.Player) null else state.objectRef(targetEntityId(recipient)) ?: return null
        val offer = FixedRecipientOffer(ref, plan, recipient, recipientRef, chooserId, plan.selections.addresses())
        val addresses = offer.addresses.filter { replacement(state, offer, it) != null }
        return offer.copy(addresses = addresses).takeIf { addresses.isNotEmpty() }
    }

    fun apply(state: GameState, offer: FixedRecipientOffer, address: TargetSlotAddress): EffectResult {
        val replacement = replacement(state, offer, address) ?: return EffectResult.success(state)
        val stackId = offer.stackRef.entityId
        val container = state.getEntity(stackId) ?: return EffectResult.success(state)
        val context = context(state, stackId) ?: return EffectResult.success(state)
        val updated = container.withCurrentTargetPlan(offer.expected, replacement) ?: return EffectResult.success(state)
        var result = state.withEntity(stackId, updated)
        val sourceName = container.get<CardComponent>()?.name
            ?: container.get<TriggeredAbilityOnStackComponent>()?.sourceName
            ?: container.get<ActivatedAbilityOnStackComponent>()?.sourceName ?: "Ability"
        val events = mutableListOf<GameEvent>(TargetsChosenEvent(offer.chooserId, stackId, sourceName))
        val recipientSlot = replacement.selections.slot(address)
        // Being targeted a second time by the same stack object is not newly becoming its target.
        val alreadyTargeted = offer.expected.slots.any { it.sameRulesObjectAs(recipientSlot) }
        if (!alreadyTargeted && offer.recipient !is ChosenTarget.Card) {
            val targetId = targetEntityId(offer.recipient)
            val entity = result.getEntity(targetId)
            val tracking = entity?.get<TargetedByControllerThisTurnComponent>() ?: TargetedByControllerThisTurnComponent()
            val isSpell = offer.recipient is ChosenTarget.Spell
            val isPlayer = offer.recipient is ChosenTarget.Player
            events.add(BecomesTargetEvent(targetId,
                if (isPlayer) entity?.get<PlayerComponent>()?.name ?: "Player" else entity?.get<CardComponent>()?.name ?: "Object",
                stackId, context.controller, isSpell || !tracking.hasBeenTargetedBy(context.controller),
                targetIsSpell = isSpell, sourceIsSpell = context.kind == TargetingSourceType.SPELL, targetIsPlayer = isPlayer))
            if (!isSpell) result = result.updateEntity(targetId) { it.with(tracking.withController(context.controller)) }
        }
        return EffectResult.success(result, events)
    }

    private fun replacement(state: GameState, offer: FixedRecipientOffer, address: TargetSlotAddress): CurrentTargetPlan? {
        val stackId = offer.stackRef.entityId
        if (stackId !in state.stack || !state.isCurrentObject(offer.stackRef) || address !in offer.addresses) return null
        val container = state.getEntity(stackId) ?: return null
        if (container.currentTargetPlan() != offer.expected || !offer.expected.isCoherent(container)) return null
        if (offer.recipient is ChosenTarget.Player) {
            if (offer.recipientRef != null || state.getEntity(offer.recipient.playerId)?.get<PlayerComponent>() == null) return null
        } else if (offer.recipientRef == null || offer.recipientRef.entityId != targetEntityId(offer.recipient) ||
            !state.isCurrentObject(offer.recipientRef)) return null
        if (targetEntityId(offer.recipient) == stackId) return null
        if (address !in offer.expected.selections.addresses()) return null
        val selection = offer.expected.selections.replacingRecipient(address, offer.recipient, offer.recipientRef) ?: return null
        if (selection == offer.expected.selections) return null
        val context = context(state, stackId) ?: return null
        val requirements = container.get<TargetsComponent>()?.targetRequirements ?: return null
        if (!validator.replacementIsLegal(state, offer.expected.selections, selection, address, requirements,
                context.controller, context.sourceId, context.colors, context.subtypes, context.cardTypes, context.kind, context.xValue)) return null
        val replacement = offer.expected.copy(selections = selection)
        // The accepted atomic adapter also checks legacy mode/division representations.
        if (container.withCurrentTargetPlan(offer.expected, replacement) == null) return null
        return replacement
    }

    private data class Context(
        val controller: EntityId, val sourceId: EntityId, val kind: TargetingSourceType,
        val colors: Set<Color>, val subtypes: Set<String>, val cardTypes: Set<String>, val xValue: Int?,
    )

    private fun context(state: GameState, stackId: EntityId): Context? {
        val entity = state.getEntity(stackId) ?: return null
        entity.get<SpellOnStackComponent>()?.let { spell ->
            val card = entity.get<CardComponent>() ?: return null
            return Context(entity.get<ControllerComponent>()?.playerId ?: spell.casterId, stackId, TargetingSourceType.SPELL,
                card.colors, card.typeLine.subtypes.map { it.value }.toSet(), card.typeLine.cardTypes.map { it.name }.toSet(), spell.xValue)
        }
        entity.get<TriggeredAbilityOnStackComponent>()?.let { ability ->
            val source = AbilitySourceCharacteristics.resolve(state, ability.sourceId, ability.objectReferences,
                ability.sourceBattlefieldTimestamp, ability.lastKnownSourceSnapshot)
            return Context(ability.controllerId, ability.sourceId, TargetingSourceType.ABILITY,
                source.colors, source.subtypes, source.cardTypes, ability.xValue)
        }
        entity.get<ActivatedAbilityOnStackComponent>()?.let { ability ->
            val source = AbilitySourceCharacteristics.resolve(state, ability.sourceId, ability.objectReferences,
                ability.sourceBattlefieldTimestamp, ability.lastKnownSourceSnapshot)
            return Context(ability.controllerId, ability.sourceId, TargetingSourceType.ABILITY,
                source.colors, source.subtypes, source.cardTypes, ability.xValue)
        }
        return null
    }
}
