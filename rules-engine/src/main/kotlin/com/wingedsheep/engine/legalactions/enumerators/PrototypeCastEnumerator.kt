package com.wingedsheep.engine.legalactions.enumerators

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.legalactions.ActionEnumerator
import com.wingedsheep.engine.legalactions.EnumerationContext
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.sdk.scripting.KeywordAbility

/**
 * Enumerates ordinary hand casts using Prototype characteristics (CR 702.160).
 *
 * Prototype is a cast mode, not an alternative cost. This enumerator owns the ordinary hand-cast
 * rail, where Prototype's own mana cost is the payable base. True alternative/free-cost casts keep
 * their existing enumerators/resolution rails and preserve Prototype separately; Cascade, in
 * particular, offers an explicit normal-vs-Prototype free-cast choice.
 *
 * Multi-face and cast-payload shapes that this compact hand rail cannot faithfully describe remain
 * fail-closed here rather than emitting an incomplete UI action. The shared CastSpell/stack
 * implementation itself is zone-agnostic and does not prohibit alternative/free costs.
 */
class PrototypeCastEnumerator : ActionEnumerator {
    override fun enumerate(context: EnumerationContext): List<LegalAction> {
        if (context.cantPlayCardsFromHand) return emptyList()

        val state = context.state
        val playerId = context.playerId
        val result = mutableListOf<LegalAction>()
        val cachedSources = context.availableManaSources

        for (cardId in state.getHand(playerId)) {
            val cardComponent = state.getEntity(cardId)?.get<CardComponent>() ?: continue
            if (context.cantCastSpell(cardId)) continue

            val cardDef = context.cardRegistry.getCard(cardComponent.name) ?: continue
            val prototype =
                cardDef.keywordAbilities.filterIsInstance<KeywordAbility.Prototype>().firstOrNull()
                    ?: continue

            // This enumerator is the ordinary primary-face hand rail. Other zones/alternative
            // costs are owned by their existing cast-permission enumerators/resolution flows.
            if (!cardComponent.typeLine.isPermanent || cardComponent.typeLine.isLand) continue
            if (cardDef.cardFaces.isNotEmpty() || cardDef.backFace != null) continue
            if (cardDef.script.targetRequirements.isNotEmpty() || cardDef.script.auraTarget != null) continue
            if (cardDef.script.additionalCosts.isNotEmpty()) continue
            if (cardDef.script.selfAlternativeCost != null) continue

            if (!context.castPermissionUtils.checkCastRestrictions(
                    state, playerId, cardDef.script.castRestrictions
                )
            ) continue

            // Prototype grants no timing permission.
            val isInstant = cardComponent.typeLine.isInstant
            val hasFlash = cardDef.keywords.contains(Keyword.FLASH) ||
                context.castPermissionUtils.hasGrantedFlash(state, cardId)
            if (!isInstant && !hasFlash && !context.canPlaySorcerySpeed) continue

            val effectiveCost = context.costCalculator.calculateEffectiveCostWithAlternativeBase(
                state, cardDef, prototype.cost, playerId
            )
            if (!context.manaSolver.canPay(
                    state, playerId, effectiveCost, precomputedSources = cachedSources
                )
            ) continue

            val autoTapPreview = if (context.skipAutoTapPreview) null else {
                context.manaSolver
                    .solve(state, playerId, effectiveCost, precomputedSources = cachedSources)
                    ?.sources
                    ?.map { it.entityId }
            }

            result += LegalAction(
                actionType = "CastSpell",
                description = "Prototype ${cardComponent.name}",
                action = CastSpell(
                    playerId = playerId,
                    cardId = cardId,
                    castForPrototype = true,
                ),
                manaCostString = effectiveCost.toString(),
                autoTapPreview = autoTapPreview,
            )
        }

        return result
    }
}
