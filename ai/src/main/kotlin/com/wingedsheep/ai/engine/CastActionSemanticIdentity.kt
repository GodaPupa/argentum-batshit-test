package com.wingedsheep.ai.engine

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.PaymentStrategy
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent

/**
 * Strategic equality for two currently legal casts whose physical source objects may differ.
 *
 * Exact entity identity remains authoritative for execution. This predicate exists only for
 * applying a sequencing decision already proved for one physical copy to another interchangeable
 * copy. It normalizes the source [CastSpell.cardId] and concrete mana-source choice, while retaining
 * every announced cast choice plus the enumerator's resolved zone, cost, permission and target
 * shape. A mode, target, X, alternative/additional cost, cast permission, payable cost, or
 * copy-specific resource difference therefore prevents a match.
 *
 * SHARED ARGENTUM CHANGE: yes
 */
internal object CastActionSemanticIdentity {
    fun equivalent(state: GameState, submitted: LegalAction, selected: LegalAction): Boolean {
        val submittedCast = submitted.action as? CastSpell ?: return false
        val selectedCast = selected.action as? CastSpell ?: return false
        val submittedCard = state.getEntity(submittedCast.cardId)?.get<CardComponent>() ?: return false
        val selectedCard = state.getEntity(selectedCast.cardId)?.get<CardComponent>() ?: return false
        if (submittedCard.cardDefinitionId != selectedCard.cardDefinitionId) return false

        val normalizedSubmitted = submittedCast.copy(
            cardId = selectedCast.cardId,
            paymentStrategy = PaymentStrategy.AutoPay,
        )
        val normalizedSelected = selectedCast.copy(paymentStrategy = PaymentStrategy.AutoPay)
        if (normalizedSubmitted != normalizedSelected) return false

        return submitted.actionType == selected.actionType &&
            submitted.affordable == selected.affordable &&
            submitted.sourceZone == selected.sourceZone &&
            submitted.manaCostString == selected.manaCostString &&
            submitted.manaCostPerExtraTarget == selected.manaCostPerExtraTarget &&
            submitted.hasXCost == selected.hasXCost &&
            submitted.maxAffordableX == selected.maxAffordableX &&
            submitted.minX == selected.minX &&
            submitted.additionalCostInfo == selected.additionalCostInfo &&
            submitted.requiresTargets == selected.requiresTargets &&
            submitted.validTargets == selected.validTargets &&
            submitted.targetCount == selected.targetCount &&
            submitted.minTargets == selected.minTargets &&
            submitted.targetRequirements == selected.targetRequirements &&
            submitted.hasConvoke == selected.hasConvoke &&
            submitted.convokeCreatures == selected.convokeCreatures &&
            submitted.hasDelve == selected.hasDelve &&
            submitted.delveCards == selected.delveCards &&
            submitted.minDelveNeeded == selected.minDelveNeeded &&
            submitted.hasTapForGeneric == selected.hasTapForGeneric &&
            submitted.tapForGenericPermanents == selected.tapForGenericPermanents &&
            submitted.tapForGenericAmount == selected.tapForGenericAmount &&
            submitted.tapForGenericRequired == selected.tapForGenericRequired &&
            submitted.hasHarmonize == selected.hasHarmonize &&
            submitted.harmonizeCreatures == selected.harmonizeCreatures
    }
}
