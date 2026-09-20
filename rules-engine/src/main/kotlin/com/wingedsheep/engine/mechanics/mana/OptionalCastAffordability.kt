package com.wingedsheep.engine.mechanics.mana

import com.wingedsheep.engine.core.GatedEffectContinuation
import com.wingedsheep.engine.core.Suspension
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.PlayWithCostIncreaseComponent
import com.wingedsheep.engine.state.components.identity.PlayWithFixedAlternativeManaCostComponent
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.effects.CardSource
import com.wingedsheep.sdk.scripting.effects.CastFromCollectionWithoutPayingCostEffect
import com.wingedsheep.sdk.scripting.effects.CompositeEffect
import com.wingedsheep.sdk.scripting.effects.Effect
import com.wingedsheep.sdk.scripting.effects.GatherCardsEffect
import com.wingedsheep.sdk.scripting.effects.Gate

/**
 * Affordability preflight for a pending pure-may decision whose accepted branch immediately casts
 * a card and requires its mana cost to be paid.
 *
 * The cast executor intentionally turns a cast that cannot initiate into the may-action's decline
 * path. That is correct resolution behavior, but a decision policy must not mistake the resulting
 * no-op for an affordable acceptance. This helper therefore recognizes the semantic effect shape,
 * resolves its concrete card from the already-carried pipeline (or a deterministic `Self` gather),
 * and asks the same [CostCalculator] and [ManaSolver] used by spell casting whether the current,
 * post-originating-action state can pay. It never predicts future mana or inspects hidden order.
 *
 * `null` means the pending decision is not this effect shape and should retain ordinary yes/no
 * scoring. `false` means accepting would immediately reach an unpayable mandatory mana cost.
 */
object OptionalCastAffordability {
    fun canPayPendingMayCast(
        state: GameState,
        playerId: EntityId,
        cardRegistry: CardRegistry,
    ): Boolean? {
        val suspension = state.peekContinuation() as? Suspension ?: return null
        val continuation = suspension.answer as? GatedEffectContinuation ?: return null
        if (continuation.gate !is Gate.MayDecide) return null

        val cast = continuation.then.firstPaidCollectionCast() ?: return null
        val context = continuation.effectContext
        val stored = context.pipeline.storedCollections[cast.from].orEmpty()
        val cardId = stored.singleOrNull()
            ?: continuation.then.selfGatherFor(cast.from)?.let { context.sourceId }
            ?: return null
        val card = state.getEntity(cardId)?.get<CardComponent>() ?: return false
        val definition = cardRegistry.getCard(card.cardDefinitionId) ?: return false
        val fromZone = state.zones.entries.firstOrNull { cardId in it.value }?.key?.zoneType

        val fixed = state.getEntity(cardId)
            ?.get<PlayWithFixedAlternativeManaCostComponent>()
            ?.takeIf { it.controllerId == playerId }
        var payable = fixed?.fixedCost
            ?: CostCalculator(cardRegistry).calculateEffectiveCost(
                state = state,
                cardDef = definition,
                casterId = playerId,
                fromZone = fromZone,
            )
        state.getEntity(cardId)
            ?.get<PlayWithCostIncreaseComponent>()
            ?.takeIf { it.controllerId == playerId }
            ?.let { payable += ManaCost.parse("{${it.amount}}") }

        return ManaSolver(cardRegistry).canPay(
            state = state,
            playerId = playerId,
            cost = payable,
            spellContext = spellPaymentContextFor(
                cardComponent = card,
                isFromExile = fromZone == com.wingedsheep.sdk.core.Zone.EXILE,
                isFromHand = fromZone == com.wingedsheep.sdk.core.Zone.HAND,
            ),
        )
    }

    private fun Effect.firstPaidCollectionCast(): CastFromCollectionWithoutPayingCostEffect? = when (this) {
        is CastFromCollectionWithoutPayingCostEffect -> takeIf { it.payManaCost }
        is CompositeEffect -> effects.firstNotNullOfOrNull { it.firstPaidCollectionCast() }
        else -> null
    }

    private fun Effect.selfGatherFor(collection: String): GatherCardsEffect? = when (this) {
        is GatherCardsEffect -> takeIf { it.storeAs == collection && it.source == CardSource.Self }
        is CompositeEffect -> effects.firstNotNullOfOrNull { it.selfGatherFor(collection) }
        else -> null
    }
}
