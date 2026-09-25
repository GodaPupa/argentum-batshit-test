package com.wingedsheep.ai.engine.advisor.modules

import com.wingedsheep.ai.engine.advisor.AdvisorDecisionContext
import com.wingedsheep.ai.engine.advisor.CardAdvisor
import com.wingedsheep.ai.engine.advisor.CardAdvisorModule
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ChooseTargetsDecision
import com.wingedsheep.engine.core.SearchLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.TargetsResponse
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/**
 * Qualified opponent policy for the exact mehanske Monster Tron 60 frozen by
 * PEST_CONTROL_V10_VS_MEHANSKE_MONSTER_TRON_2026_09_21_PREBOARD_V1.
 *
 * This module does not alter either deck and is not an execution authorization. It supplies only
 * the deterministic public-state decisions generic one-ply evaluation is known to mis-sequence.
 */
object PestMonsterTronAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) {
        registry.register(PestMonsterTronTutorAdvisor)
        registry.register(PestMonsterTronOrnamentAdvisor)
        registry.register(PestMonsterTronBojukaBogAdvisor)
    }
}

private val PEST_MONSTER_TRON_LANDS =
    listOf("Urza's Mine", "Urza's Power Plant", "Urza's Tower")

private object PestMonsterTronTutorAdvisor : CardAdvisor {
    override val cardNames = setOf("Expedition Map", "Crop Rotation", "Ancient Stirrings")

    override fun evaluateCast(context: CastContext): Double? {
        if (pestMonsterMissingTronLands(context.state, context.playerId).isEmpty()) return null

        val cast = context.action.action as? CastSpell
        if (cast != null && context.state.pestMonsterCardName(cast.cardId) == "Crop Rotation") {
            val sacrificed = cast.additionalCostPayment?.sacrificedPermanents?.singleOrNull()
            val sacrificedName = sacrificed?.let(context.state::pestMonsterCardName)
            if (sacrificedName in PEST_MONSTER_TRON_LANDS) {
                return context.passScore - 25.0
            }
        }
        return context.passScore + 35.0
    }

    override fun respondToDecision(context: AdvisorDecisionContext) = when (val decision = context.decision) {
        is SearchLibraryDecision -> {
            val missing = pestMonsterMissingTronLands(context.state, context.playerId)
            val selected = missing.asSequence().mapNotNull { wanted ->
                decision.options.firstOrNull { id -> decision.cards[id]?.name == wanted }
            }.firstOrNull()
                ?: decision.options.firstOrNull { id -> decision.cards[id]?.name in PEST_MONSTER_TRON_LANDS }
            selected?.let { CardsSelectedResponse(decision.id, listOf(it)) }
        }

        is SelectCardsDecision -> {
            val info = decision.cardInfo
            if (info == null && context.sourceCardName == "Crop Rotation") {
                val selected = decision.options.firstOrNull { id ->
                    context.state.pestMonsterCardName(id) !in PEST_MONSTER_TRON_LANDS
                } ?: return null
                CardsSelectedResponse(decision.id, listOf(selected))
            } else if (info != null) {
                val missing = pestMonsterMissingTronLands(context.state, context.playerId)
                val selected = missing.asSequence().mapNotNull { wanted ->
                    decision.options.firstOrNull { id -> info[id]?.name == wanted }
                }.firstOrNull()
                    ?: decision.options.firstOrNull { id -> info[id]?.name in PEST_MONSTER_TRON_LANDS }
                selected?.let { CardsSelectedResponse(decision.id, listOf(it)) }
            } else null
        }

        else -> null
    }
}

private object PestMonsterTronOrnamentAdvisor : CardAdvisor {
    override val cardNames = setOf("Bonder's Ornament")

    override fun evaluateCast(context: CastContext): Double? {
        val activation = context.action.action as? ActivateAbility ?: return null
        val handSize = context.state.getZone(context.playerId, Zone.HAND).size
        val otherMana = context.state.projectedState.getBattlefieldControlledBy(context.playerId)
            .count { id ->
                id != activation.sourceId &&
                    context.state.pestMonsterCardName(id) in (
                        PEST_MONSTER_TRON_LANDS +
                            listOf("Forest", "Conduit Pylons", "Bonder's Ornament")
                    )
            }
        return if (handSize <= 2 && otherMana >= 4) context.passScore + 14.0 else null
    }
}

private object PestMonsterTronBojukaBogAdvisor : CardAdvisor {
    override val cardNames = setOf("Bojuka Bog")

    override fun respondToDecision(context: AdvisorDecisionContext) =
        (context.decision as? ChooseTargetsDecision)?.let { decision ->
            val requirement = decision.targetRequirements.singleOrNull() ?: return@let null
            val legal = decision.legalTargets[requirement.index].orEmpty()
            val opponent = legal
                .filter { context.state.isOpponentOf(it, context.playerId) }
                .maxByOrNull { context.state.getZone(it, Zone.GRAVEYARD).size }
                ?: return@let null
            TargetsResponse(decision.id, mapOf(requirement.index to listOf(opponent)))
        }
}

private fun pestMonsterMissingTronLands(state: GameState, playerId: EntityId): List<String> {
    val accessible = state.projectedState.getBattlefieldControlledBy(playerId) +
        state.getZone(playerId, Zone.HAND)
    val names = accessible.mapNotNull(state::pestMonsterCardName).toSet()
    return PEST_MONSTER_TRON_LANDS.filterNot(names::contains)
}

private fun GameState.pestMonsterCardName(id: EntityId): String? =
    getEntity(id)?.get<CardComponent>()?.name
