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
 * Prototype is a cast mode, not an alternative cost. This first shared implementation is
 * deliberately bounded to the rules-complete path required by Boulderbranch Golem: a primary-face
 * permanent spell from hand with no targets or additional costs. Combinations with another true
 * alternative cost, free-cast permission, split/DFC faces, or target/additional-cost machinery are
 * not surfaced until separately validated.
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

            // Current reviewed support boundary: ordinary primary-face permanent from hand only.
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
                actionType = "CastPrototype",
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
