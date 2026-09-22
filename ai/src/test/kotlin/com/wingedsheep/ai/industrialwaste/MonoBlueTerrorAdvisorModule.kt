package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.advisor.CardAdvisor
import com.wingedsheep.ai.engine.advisor.CardAdvisorModule
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/**
 * Test-lab opponent policy for the sourced Joan Rubies Mono-Blue Terror list.
 *
 * The stock production profile already handles the deck's reduced-cost threats,
 * counters, and Mystic sequencing. This module supplies the one deck-specific
 * inference the generic evaluator cannot reliably recover from one-ply board value:
 * self-mill is valuable when a graveyard-discounted threat is already in hand.
 *
 * The advice uses only public information available to the player making the decision.
 */
internal object MonoBlueTerrorAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) {
        registry.register(ThoughtScourSelfMillAdvisor)
    }
}

private object ThoughtScourSelfMillAdvisor : CardAdvisor {
    override val cardNames = setOf("Thought Scour")

    override fun evaluateCast(context: CastContext): Double? {
        val cast = context.action.action as? CastSpell ?: return null
        val target = cast.targets.singleOrNull() as? ChosenTarget.Player ?: return null
        if (target.playerId != context.playerId) return null

        val handNames = context.state.getZone(context.playerId, Zone.HAND)
            .mapNotNull(context.state::monoBlueCardName)
        val selfMillAdvancesThreat = handNames.any {
            it == "Tolarian Terror" || it == "Cryptic Serpent"
        }
        if (!selfMillAdvancesThreat) return null

        return context.passScore + 20.0
    }
}

private fun com.wingedsheep.engine.state.GameState.monoBlueCardName(id: EntityId): String? =
    getEntity(id)?.get<CardComponent>()?.name
