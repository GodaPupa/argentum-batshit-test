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
        registry.register(AncientGrudgeAdvisor)
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


private object AncientGrudgeAdvisor : CardAdvisor {
    override val cardNames = setOf("Ancient Grudge")

    override fun evaluateCast(context: CastContext): Double? {
        val cast = context.action.action as? com.wingedsheep.engine.core.CastSpell ?: return null
        val targetName = cast.targets.firstOrNull()?.let { target ->
            when (target) {
                is com.wingedsheep.engine.state.components.stack.ChosenTarget.Permanent ->
                    context.state.cardName(target.entityId)
                is com.wingedsheep.engine.state.components.stack.ChosenTarget.Card ->
                    context.state.cardName(target.cardId)
                else -> null
            }
        }
        val targetBonus = when (targetName) {
            "Myr Enforcer" -> 30.0
            "Refurbished Familiar" -> 28.0
            "Utrom Monitor" -> 26.0
            "Makeshift Munitions" -> 24.0
            "Nihil Spellbomb" -> 12.0
            "Blood Fountain" -> 10.0
            "Ichor Wellspring" -> 8.0
            "Drossforge Bridge", "Mistvault Bridge", "Silverbluff Bridge" -> 5.0
            "Great Furnace", "Seat of the Synod", "Vault of Whispers" -> 4.0
            else -> 1.0
        }
        return context.passScore + 12.0 + targetBonus
    }

    override fun respondToDecision(context: AdvisorDecisionContext) =
        (context.decision as? ChooseTargetsDecision)?.let { decision ->
            val requirement = decision.targetRequirements.singleOrNull() ?: return@let null
            val legal = decision.legalTargets[requirement.index].orEmpty()
            val preferred = legal.maxByOrNull { id ->
                when (context.state.cardName(id)) {
                    "Myr Enforcer" -> 100
                    "Refurbished Familiar" -> 95
                    "Utrom Monitor" -> 90
                    "Makeshift Munitions" -> 85
                    "Nihil Spellbomb" -> 55
                    "Blood Fountain" -> 50
                    "Ichor Wellspring" -> 45
                    "Drossforge Bridge", "Mistvault Bridge", "Silverbluff Bridge" -> 35
                    "Great Furnace", "Seat of the Synod", "Vault of Whispers" -> 30
                    else -> 10
                }
            } ?: return@let null
            TargetsResponse(decision.id, mapOf(requirement.index to listOf(preferred)))
        }
}

private object AshnodsAltarAdvisor : CardAdvisor {
    override val cardNames = setOf("Ashnod's Altar")

    override fun evaluateCast(context: CastContext): Double? {
        return if (retrieverEngineCanAdvance(context.state, context.playerId)) {
            context.passScore + 20.0
        } else null
    }

    override fun respondToDecision(context: AdvisorDecisionContext) =
        (context.decision as? SelectCardsDecision)?.let { decision ->
            if (!retrieverEngineCanAdvance(context.state, context.playerId)) return@let null
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

private fun retrieverEngineCanAdvance(
    state: com.wingedsheep.engine.state.GameState,
    playerId: EntityId,
): Boolean {
    val battlefield = state.projectedState.getBattlefieldControlledBy(playerId)
    val accessible = battlefield + state.getZone(playerId, Zone.HAND) +
        state.getZone(playerId, Zone.GRAVEYARD)
    val retrieverCount = accessible.count { state.cardName(it) == "Myr Retriever" }
    return retrieverCount >= 2 &&
        battlefield.any { state.cardName(it) == "Myr Retriever" } &&
        battlefield.any { state.cardName(it) == "Ashnod's Altar" } &&
        battlefield.any { state.cardName(it) in setOf("Pactdoll Terror", "Golem Foundry") }
}

private fun com.wingedsheep.engine.state.GameState.cardName(id: EntityId): String? =
    getEntity(id)?.get<CardComponent>()?.name
