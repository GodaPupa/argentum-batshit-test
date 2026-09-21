package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.advisor.AdvisorDecisionContext
import com.wingedsheep.ai.engine.advisor.CardAdvisor
import com.wingedsheep.ai.engine.advisor.CardAdvisorModule
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.ChooseColorDecision
import com.wingedsheep.engine.core.ColorChosenResponse
import com.wingedsheep.engine.core.SearchLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/** Narrow, test-only policy for the sourced Boros opponent's non-generic decisions. */
internal object IndustrialWasteBorosAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) {
        registry.register(BattleScreechAdvisor)
        registry.register(PrismaticStrandsAdvisor)
        registry.register(PerilousLandscapeAdvisor)
        registry.register(ThrillingDiscoveryAdvisor)
    }
}

private object BattleScreechAdvisor : CardAdvisor {
    override val cardNames = setOf("Battle Screech")

    override fun evaluateCast(context: CastContext): Double = context.passScore + 8.0
}

private object PrismaticStrandsAdvisor : CardAdvisor {
    override val cardNames = setOf("Prismatic Strands")

    override fun evaluateCast(context: CastContext): Double = context.passScore + 6.0

    override fun respondToDecision(context: AdvisorDecisionContext) =
        (context.decision as? ChooseColorDecision)?.let { decision ->
            val opponentColors = context.state.turnOrder
                .filter { context.state.isOpponentOf(it, context.playerId) }
                .flatMap { context.state.projectedState.getBattlefieldControlledBy(it) }
                .flatMap { id -> context.state.getEntity(id)?.get<CardComponent>()?.colors.orEmpty() }
            val color = decision.availableColors.maxByOrNull { candidate ->
                opponentColors.count { it == candidate }
            } ?: Color.WHITE
            ColorChosenResponse(decision.id, color)
        }
}

private object PerilousLandscapeAdvisor : CardAdvisor {
    override val cardNames = setOf("Perilous Landscape")

    override fun evaluateCast(context: CastContext): Double? {
        val battlefieldNames = context.state.projectedState
            .getBattlefieldControlledBy(context.playerId)
            .mapNotNull(context.state::cardName)
        return if ("Mountain" !in battlefieldNames) context.passScore + 5.0 else null
    }

    override fun respondToDecision(context: AdvisorDecisionContext) = when (val decision = context.decision) {
        is SearchLibraryDecision -> {
            val battlefieldNames = context.state.projectedState
                .getBattlefieldControlledBy(context.playerId)
                .mapNotNull(context.state::cardName)
            val preferred = if ("Mountain" !in battlefieldNames) "Mountain" else "Plains"
            val selected = decision.options.firstOrNull { decision.cards[it]?.name == preferred }
                ?: decision.options.firstOrNull()
            CardsSelectedResponse(decision.id, listOfNotNull(selected))
        }
        is SelectCardsDecision -> {
            val battlefieldNames = context.state.projectedState
                .getBattlefieldControlledBy(context.playerId)
                .mapNotNull(context.state::cardName)
            val preferred = if ("Mountain" !in battlefieldNames) "Mountain" else "Plains"
            val selected = decision.options.firstOrNull { decision.cardInfo?.get(it)?.name == preferred }
                ?: decision.options.firstOrNull()
            CardsSelectedResponse(decision.id, listOfNotNull(selected))
        }
        else -> null
    }
}

private object ThrillingDiscoveryAdvisor : CardAdvisor {
    override val cardNames = setOf("Thrilling Discovery")

    override fun evaluateCast(context: CastContext): Double? {
        val handSize = context.state.getZone(context.playerId, Zone.HAND).size
        return if (handSize >= 3) context.passScore + 7.0 else null
    }

    override fun respondToDecision(context: AdvisorDecisionContext) = when (val decision = context.decision) {
        is YesNoDecision -> YesNoResponse(decision.id, choice = true)
        is SelectCardsDecision -> {
            val ranked = decision.options.sortedBy { id ->
                when (context.state.cardName(id)) {
                    "Sneaky Snacker" -> 0
                    "Faithless Looting" -> 1
                    "Mountain", "Plains" -> 2
                    else -> 3
                }
            }
            CardsSelectedResponse(decision.id, ranked.take(decision.maxSelections))
        }
        else -> null
    }
}

private fun com.wingedsheep.engine.state.GameState.cardName(id: EntityId): String? =
    getEntity(id)?.get<CardComponent>()?.name
