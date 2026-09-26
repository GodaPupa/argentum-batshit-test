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

private object PestMonsterTronTutorAdvisor : CardAdvisor {
    override val cardNames = setOf("Expedition Map", "Crop Rotation", "Ancient Stirrings")

    override fun evaluateCast(context: CastContext): Double? {
        val cast = context.action.action as? CastSpell
        val sacrificed = cast?.additionalCostPayment?.sacrificedPermanents?.singleOrNull()
        return PestMonsterTronPublicPolicy.tutorCastScore(
            context.state.pestMonsterAccessibleNames(context.playerId),
            cast != null && context.state.pestMonsterCardName(cast.cardId) == "Crop Rotation",
            sacrificed?.let(context.state::pestMonsterCardName),
            context.passScore,
        )
    }

    override fun respondToDecision(context: AdvisorDecisionContext) = when (val decision = context.decision) {
        is SearchLibraryDecision -> {
            val selected = PestMonsterTronPublicPolicy.tutorSelection(
                context.state.pestMonsterAccessibleNames(context.playerId),
                decision.options,
                decision.options.associateWith { decision.cards[it]?.name },
            )
            selected?.let { CardsSelectedResponse(decision.id, listOf(it)) }
        }

        is SelectCardsDecision -> {
            val info = decision.cardInfo
            if (info == null && context.sourceCardName == "Crop Rotation") {
                val selected = PestMonsterTronPublicPolicy.cropRotationSacrifice(
                    decision.options,
                    decision.options.associateWith(context.state::pestMonsterCardName),
                ) ?: return null
                CardsSelectedResponse(decision.id, listOf(selected))
            } else if (info != null) {
                val selected = PestMonsterTronPublicPolicy.tutorSelection(
                    context.state.pestMonsterAccessibleNames(context.playerId),
                    decision.options,
                    decision.options.associateWith { info[it]?.name },
                )
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
                    PestMonsterTronPublicPolicy.isOrnamentManaName(context.state.pestMonsterCardName(id))
            }
        return PestMonsterTronPublicPolicy.ornamentCastScore(handSize, otherMana, context.passScore)
    }
}

private object PestMonsterTronBojukaBogAdvisor : CardAdvisor {
    override val cardNames = setOf("Bojuka Bog")

    override fun respondToDecision(context: AdvisorDecisionContext) =
        (context.decision as? ChooseTargetsDecision)?.let { decision ->
            val requirement = decision.targetRequirements.singleOrNull() ?: return@let null
            val legal = decision.legalTargets[requirement.index].orEmpty()
            val opponents = legal.filter { context.state.isOpponentOf(it, context.playerId) }
            val opponent = PestMonsterTronPublicPolicy.bojukaBogTarget(
                opponents,
                opponents.associateWith { context.state.getZone(it, Zone.GRAVEYARD).size },
            )
                ?: return@let null
            TargetsResponse(decision.id, mapOf(requirement.index to listOf(opponent)))
        }
}

private fun GameState.pestMonsterAccessibleNames(playerId: EntityId): List<String> {
    val accessible = projectedState.getBattlefieldControlledBy(playerId) + getZone(playerId, Zone.HAND)
    return accessible.mapNotNull(this::pestMonsterCardName)
}

private fun GameState.pestMonsterCardName(id: EntityId): String? =
    getEntity(id)?.get<CardComponent>()?.name
