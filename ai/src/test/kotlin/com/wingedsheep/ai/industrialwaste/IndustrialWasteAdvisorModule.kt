package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.advisor.AdvisorDecisionContext
import com.wingedsheep.ai.engine.advisor.CardAdvisor
import com.wingedsheep.ai.engine.advisor.CardAdvisorModule
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.SearchLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/** Test-only policy for the decisions the stock arena agent cannot infer from board value. */
internal object IndustrialWasteAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) {
        registry.register(TronTutorAdvisor)
        registry.register(MyrRetrieverAdvisor)
        registry.register(AshnodsAltarAdvisor)
    }
}

private val TRON_LANDS = listOf("Urza's Mine", "Urza's Power Plant", "Urza's Tower")

private object TronTutorAdvisor : CardAdvisor {
    override val cardNames = setOf("Expedition Map", "Crop Rotation")

    override fun evaluateCast(context: CastContext): Double? {
        if (missingTronLands(context.state, context.playerId).isEmpty()) return null
        return context.passScore + 8.0
    }

    override fun respondToDecision(context: AdvisorDecisionContext) =
        (context.decision as? SearchLibraryDecision)?.let { decision ->
            val missing = missingTronLands(context.state, context.playerId)
            val selected = decision.options.firstOrNull { id ->
                decision.cards[id]?.name == missing.firstOrNull()
            } ?: decision.options.firstOrNull { id -> decision.cards[id]?.name in missing }
            selected?.let { CardsSelectedResponse(decision.id, listOf(it)) }
        }
}

private object MyrRetrieverAdvisor : CardAdvisor {
    override val cardNames = setOf("Myr Retriever")

    override fun evaluateCast(context: CastContext): Double? {
        val battlefield = context.state.projectedState.getBattlefieldControlledBy(context.playerId)
        val hasAltar = battlefield.any { context.state.cardName(it) == "Ashnod's Altar" }
        val hasPayoff = battlefield.any {
            context.state.cardName(it) in setOf("Pactdoll Terror", "Golem Foundry")
        }
        val retrieverInGraveyard = context.state.getZone(context.playerId, Zone.GRAVEYARD)
            .any { context.state.cardName(it) == "Myr Retriever" }
        return if (hasAltar && hasPayoff && retrieverInGraveyard) context.passScore + 20.0 else null
    }

    override fun respondToDecision(context: AdvisorDecisionContext) =
        (context.decision as? ChooseTargetsDecision)?.let { decision ->
            val requirement = decision.targetRequirements.singleOrNull() ?: return@let null
            val retriever = decision.legalTargets[requirement.index]
                ?.firstOrNull { context.state.cardName(it) == "Myr Retriever" }
                ?: return@let null
            TargetsResponse(decision.id, mapOf(requirement.index to listOf(retriever)))
        }
}

private object AshnodsAltarAdvisor : CardAdvisor {
    override val cardNames = setOf("Ashnod's Altar")

    override fun evaluateCast(context: CastContext): Double? {
        return if (retrieverLoopIsAvailable(context.state, context.playerId)) {
            context.passScore + 20.0
        } else null
    }

    override fun respondToDecision(context: AdvisorDecisionContext) =
        (context.decision as? SelectCardsDecision)?.let { decision ->
            if (!retrieverLoopIsAvailable(context.state, context.playerId)) return@let null
            val retriever = decision.options
                .firstOrNull { context.state.cardName(it) == "Myr Retriever" }
                ?: return@let null
            CardsSelectedResponse(decision.id, listOf(retriever))
        }
}

private fun missingTronLands(
    state: com.wingedsheep.engine.state.GameState,
    playerId: EntityId,
): List<String> {
    val accessible = state.projectedState.getBattlefieldControlledBy(playerId) +
        state.getZone(playerId, Zone.HAND)
    val names = accessible.mapNotNull(state::cardName).toSet()
    return TRON_LANDS.filterNot(names::contains)
}

private fun retrieverLoopIsAvailable(
    state: com.wingedsheep.engine.state.GameState,
    playerId: EntityId,
): Boolean {
    val battlefield = state.projectedState.getBattlefieldControlledBy(playerId)
    return battlefield.any { state.cardName(it) == "Myr Retriever" } &&
        battlefield.any { state.cardName(it) == "Ashnod's Altar" } &&
        battlefield.any { state.cardName(it) in setOf("Pactdoll Terror", "Golem Foundry") } &&
        state.getZone(playerId, Zone.GRAVEYARD).any { state.cardName(it) == "Myr Retriever" }
}

private fun com.wingedsheep.engine.state.GameState.cardName(id: EntityId): String? =
    getEntity(id)?.get<CardComponent>()?.name
