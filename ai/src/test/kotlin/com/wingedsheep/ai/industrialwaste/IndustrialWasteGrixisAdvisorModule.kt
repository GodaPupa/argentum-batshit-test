package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.advisor.CardAdvisor
import com.wingedsheep.ai.engine.advisor.CardAdvisorModule
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/**
 * Narrow, test-only policy for the exact Gate 6 Grixis Affinity opponent.
 *
 * This module exists only to make non-generic strategic choices deterministic before matchup
 * sampling. It does not change either frozen deck and is not itself matchup evidence.
 */
internal object IndustrialWasteGrixisAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) {
        registry.register(GrixisAffinityDeployAdvisor)
        registry.register(GrixisGalvanicBlastAdvisor)
        registry.register(GrixisNihilSpellbombAdvisor)
    }
}

private object GrixisAffinityDeployAdvisor : CardAdvisor {
    override val cardNames = setOf("Refurbished Familiar", "Utrom Monitor", "Myr Enforcer")

    override fun evaluateCast(context: CastContext): Double =
        context.passScore + 12.0
}

private object GrixisGalvanicBlastAdvisor : CardAdvisor {
    override val cardNames = setOf("Galvanic Blast")

    override fun evaluateCast(context: CastContext): Double? {
        val cast = context.action as? CastSpell ?: return null
        val targetsOpponent = cast.targets.any { target ->
            val player = target as? com.wingedsheep.engine.state.components.stack.ChosenTarget.Player
            player != null && context.state.isOpponentOf(player.playerId, context.playerId)
        }
        if (!targetsOpponent) return null

        val opponent = context.state.turnOrder.firstOrNull {
            context.state.isOpponentOf(it, context.playerId)
        } ?: return null
        val blastsInHand = context.state.getZone(context.playerId, Zone.HAND)
            .count { context.state.cardName(it) == "Galvanic Blast" }
        val artifacts = context.state.projectedState.getBattlefieldControlledBy(context.playerId)
            .count { context.state.cardName(it) in GRIXIS_ARTIFACTS }
        val damagePerBlast = if (artifacts >= 3) 4 else 2
        val visibleReach = blastsInHand * damagePerBlast
        return if (visibleReach >= context.state.lifeTotal(opponent)) {
            context.passScore + 10.0
        } else {
            context.passScore - 100.0
        }
    }
}

private object GrixisNihilSpellbombAdvisor : CardAdvisor {
    override val cardNames = setOf("Nihil Spellbomb")

    override fun evaluateCast(context: CastContext): Double? {
        val opposingGraveyardHasCards = context.state.turnOrder
            .filter { context.state.isOpponentOf(it, context.playerId) }
            .any { context.state.getZone(it, Zone.GRAVEYARD).isNotEmpty() }
        return if (opposingGraveyardHasCards) {
            context.passScore + 8.0
        } else {
            context.passScore - 100.0
        }
    }
}

private val GRIXIS_ARTIFACTS = setOf(
    "Drossforge Bridge",
    "Great Furnace",
    "Mistvault Bridge",
    "Seat of the Synod",
    "Silverbluff Bridge",
    "Vault of Whispers",
    "Myr Enforcer",
    "Refurbished Familiar",
    "Utrom Monitor",
    "Blood Fountain",
    "Ichor Wellspring",
    "Nihil Spellbomb",
)

private fun com.wingedsheep.engine.state.GameState.cardName(id: EntityId): String? =
    getEntity(id)?.get<CardComponent>()?.name
