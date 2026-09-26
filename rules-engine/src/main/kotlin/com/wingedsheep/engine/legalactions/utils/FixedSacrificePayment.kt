package com.wingedsheep.engine.legalactions.utils

import com.wingedsheep.engine.legalactions.EnumerationContext
import com.wingedsheep.engine.mechanics.mana.ManaPaymentFeasibility
import com.wingedsheep.engine.mechanics.mana.ManaPaymentFeasibilityResult
import com.wingedsheep.engine.mechanics.mana.ManaPaymentRequest
import com.wingedsheep.engine.mechanics.mana.SpellPaymentContext
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.AdditionalCost
import com.wingedsheep.sdk.scripting.costs.CostAtom

/**
 * Joint mana/material feasibility for a single fixed mandatory spell sacrifice. Null means this
 * helper did not qualify the shape; it never converts an unsupported search to unaffordable.
 * No material is selected here. The actor still chooses and binds the final sacrifice.
 */
internal object FixedSacrificePayment {
    fun assess(context: EnumerationContext, cardId: EntityId, cost: ManaCost,
        additionalCosts: List<AdditionalCost>, spellContext: SpellPaymentContext? = null): Boolean? {
        val sacrifice = (additionalCosts.singleOrNull() as? AdditionalCost.Atom)?.atom as? CostAtom.Sacrifice
            ?: return null
        return when (ManaPaymentFeasibility(context.cardRegistry).assess(context.state, context.playerId,
            ManaPaymentRequest(cost, context = spellContext, finalSacrificeCost = sacrifice, costSourceId = cardId))) {
            is ManaPaymentFeasibilityResult.Payable -> true
            is ManaPaymentFeasibilityResult.Impossible -> false
            is ManaPaymentFeasibilityResult.Unsupported -> null
        }
    }
}
