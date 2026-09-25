package com.wingedsheep.engine.legalactions.enumerators

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.legalactions.ActionEnumerator
import com.wingedsheep.engine.legalactions.EnumerationContext
import com.wingedsheep.engine.legalactions.LegalAction
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Keyword
import com.wingedsheep.engine.mechanics.mana.spellPaymentContextFor
import com.wingedsheep.sdk.dsl.Targets
import com.wingedsheep.sdk.scripting.KeywordAbility

/**
 * Enumerates Bestow casts (CR 702.103).
 *
 * Bestow grants no timing permission. It replaces the spell's mana cost, makes the spell an Aura
 * enchantment with enchant creature while the Bestow effect lasts, and therefore always adds one
 * creature target. The normal CastSpellEnumerator continues to surface the ordinary creature cast.
 */
class BestowCastEnumerator : ActionEnumerator {

    override fun enumerate(context: EnumerationContext): List<LegalAction> {
        val state = context.state
        val playerId = context.playerId
        if (context.cantPlayCardsFromHand) return emptyList()

        val result = mutableListOf<LegalAction>()
        val cachedSources = context.availableManaSources

        for (cardId in state.getHand(playerId)) {
            val card = state.getEntity(cardId)?.get<CardComponent>() ?: continue
            val cardDef = context.cardRegistry.getCard(card.name) ?: continue
            val bestow = cardDef.keywordAbilities.filterIsInstance<KeywordAbility.Bestow>().firstOrNull()
                ?: continue

            // CR 702.103d: all filtered cast prohibitions and permissions inspect the
            // chosen Aura characteristics, before deciding whether to enumerate this mode.
            val bestowType = com.wingedsheep.engine.state.components.identity.BestowComponent.auraType(card.typeLine)
            val bestowDef = cardDef.copy(typeLine = bestowType)
            val bestowCard = card.copy(typeLine = bestowType)
            val bestowState = state.updateEntity(cardId) { it.with(bestowCard) }
            if (context.castPermissionUtils.reasonCannotCast(bestowState, playerId, cardId) != null) continue

            // Bestow doesn't itself grant a timing permission.
            val isInstant = card.typeLine.isInstant
            val hasFlash = cardDef.keywords.contains(Keyword.FLASH) ||
                context.castPermissionUtils.hasGrantedFlash(bestowState, cardId)
            if (!isInstant && !hasFlash && !context.canPlaySorcerySpeed) continue
            if (!context.castPermissionUtils.checkCastRestrictions(
                    state, playerId, cardDef.script.castRestrictions
                )
            ) continue

            // CR 702.103b: once the bestow alternative cost is chosen, the spell is an
            // Enchantment — Aura (not a creature spell) before costs/targets are finalized.
            // Use a local characteristic view here; the real entity is rewritten when it is
            // actually put on the stack by StackResolver.
            val effectiveCost = context.costCalculator.calculateEffectiveCostWithAlternativeBase(
                state, bestowDef, bestow.cost, playerId
            )
            val spellContext = spellPaymentContextFor(bestowCard).copy(hasXInCost = effectiveCost.hasX)

            // A Bestow spell always targets one creature. The local Aura state matters for
            // protection-from-creatures / protection-from-subtype checks while choosing targets.
            val requirement = Targets.Creature
            val infos = context.targetUtils.buildTargetInfos(bestowState, playerId, listOf(requirement), cardId)
            val info = infos.firstOrNull() ?: continue
            if (!context.targetUtils.allRequirementsSatisfied(infos)) continue

            val maxX = if (effectiveCost.hasX) {
                val available = context.manaSolver.getAvailableManaCount(
                    state, playerId, precomputedSources = cachedSources, spellContext = spellContext
                )
                val roughUpper = ((available - effectiveCost.cmc) /
                    effectiveCost.xCount.coerceAtLeast(1)).coerceAtLeast(0)
                (roughUpper downTo 0).firstOrNull { x ->
                    context.manaSolver.canPay(
                        state, playerId, effectiveCost, xValue = x,
                        precomputedSources = cachedSources, spellContext = spellContext
                    )
                } ?: continue
            } else {
                if (!context.manaSolver.canPay(
                        state, playerId, effectiveCost, precomputedSources = cachedSources,
                        spellContext = spellContext
                    )
                ) continue
                null
            }

            val preview = if (context.skipAutoTapPreview) null else {
                context.manaSolver.solve(
                    state, playerId, effectiveCost, xValue = 0, precomputedSources = cachedSources,
                    spellContext = spellContext
                )?.sources?.map { it.entityId }
            }

            result.add(
                LegalAction(
                    actionType = "CastWithAlternativeCost",
                    description = "Bestow ${card.name} ($effectiveCost)",
                    action = CastSpell(
                        playerId = playerId,
                        cardId = cardId,
                        useAlternativeCost = true,
                        alternativeCostType = AlternativeCostType.BESTOW
                    ),
                    validTargets = info.validTargets,
                    requiresTargets = true,
                    targetCount = info.maxTargets,
                    minTargets = requirement.effectiveMinCount,
                    targetDescription = requirement.description,
                    hasXCost = effectiveCost.hasX,
                    maxAffordableX = maxX,
                    manaCostString = effectiveCost.toString(),
                    autoTapPreview = preview
                )
            )
        }

        return result
    }
}
