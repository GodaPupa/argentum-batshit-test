package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.engine.advisor.CardAdvisor
import com.wingedsheep.ai.engine.advisor.CardAdvisorModule
import com.wingedsheep.ai.engine.advisor.CardAdvisorRegistry
import com.wingedsheep.ai.engine.advisor.CastContext
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/**
 * Narrow, test-only policy for the exact Gate 8 manohito Jund Wildfire opponent.
 *
 * These advisors encode identity-critical choices that one-ply generic board value may miss.
 * They are qualification tooling only: they do not change either frozen Industrial Waste list.
 */
internal object JundWildfireAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) {
        registry.register(JundCleansingWildfireAdvisor)
        registry.register(JundValueSacrificeDrawAdvisor)
        registry.register(JundCastDownAdvisor)
        registry.register(JundGalvanicBlastAdvisor)
        registry.register(JundMakeshiftMunitionsAdvisor)
    }
}

private val JUND_BRIDGES = setOf("Drossforge Bridge", "Slagwoods Bridge")
private val JUND_ARTIFACTS = setOf(
    "Refurbished Familiar",
    "Nihil Spellbomb",
    "Ichor Wellspring",
    "Lembas",
    "Drossforge Bridge",
    "Slagwoods Bridge",
    "Vault of Whispers",
)

private object JundCleansingWildfireAdvisor : CardAdvisor {
    override val cardNames = setOf("Cleansing Wildfire")

    override fun evaluateCast(context: CastContext): Double? {
        val hasOwnBridge = context.state.projectedState
            .getBattlefieldControlledBy(context.playerId)
            .any { context.state.cardName(it) in JUND_BRIDGES }
        return if (hasOwnBridge) context.passScore + 14.0 else null
    }

    override fun targetPreference(state: GameState, targetId: EntityId, playerId: EntityId): Double? {
        val name = state.cardName(targetId)
        val controller = state.projectedState.getController(targetId)
        return when {
            controller == playerId && name in JUND_BRIDGES -> 100.0
            controller != null && state.isOpponentOf(controller, playerId) -> 20.0
            else -> null
        }
    }
}

private object JundValueSacrificeDrawAdvisor : CardAdvisor {
    override val cardNames = setOf("Reckoner's Bargain", "Eviscerator's Insight")

    override fun evaluateCast(context: CastContext): Double? {
        val battlefield = context.state.projectedState.getBattlefieldControlledBy(context.playerId)
        return if (battlefield.any { context.state.cardName(it) == "Ichor Wellspring" }) {
            context.passScore + 12.0
        } else null
    }
}

private object JundCastDownAdvisor : CardAdvisor {
    override val cardNames = setOf("Cast Down")

    override fun evaluateCast(context: CastContext): Double? {
        val hasPactdoll = context.state.getBattlefield()
            .any { context.state.cardName(it) == "Pactdoll Terror" }
        return if (hasPactdoll) context.passScore + 12.0 else null
    }

    override fun targetPreference(state: GameState, targetId: EntityId, playerId: EntityId): Double? =
        when (state.cardName(targetId)) {
            "Pactdoll Terror" -> 100.0
            "Carrier Thrall" -> 25.0
            else -> null
        }
}

private object JundGalvanicBlastAdvisor : CardAdvisor {
    override val cardNames = setOf("Galvanic Blast")

    override fun evaluateCast(context: CastContext): Double? {
        val opponent = context.state.turnOrder.firstOrNull {
            context.state.isOpponentOf(it, context.playerId)
        } ?: return null
        val damage = if (context.state.jundArtifactCount(context.playerId) >= 3) 4 else 2
        return if (context.state.lifeTotal(opponent) <= damage) context.passScore + 20.0 else null
    }

    override fun targetPreference(state: GameState, targetId: EntityId, playerId: EntityId): Double? {
        if (targetId !in state.turnOrder || !state.isOpponentOf(targetId, playerId)) return null
        val damage = if (state.jundArtifactCount(playerId) >= 3) 4 else 2
        return if (state.lifeTotal(targetId) <= damage) 100.0 else null
    }
}

private object JundMakeshiftMunitionsAdvisor : CardAdvisor {
    override val cardNames = setOf("Makeshift Munitions")

    override fun evaluateCast(context: CastContext): Double? {
        val opponent = context.state.turnOrder.firstOrNull {
            context.state.isOpponentOf(it, context.playerId)
        } ?: return null
        return if (context.state.lifeTotal(opponent) <= 1) context.passScore + 20.0 else null
    }

    override fun targetPreference(state: GameState, targetId: EntityId, playerId: EntityId): Double? =
        if (targetId in state.turnOrder &&
            state.isOpponentOf(targetId, playerId) &&
            state.lifeTotal(targetId) <= 1
        ) 100.0 else null
}

private fun GameState.jundArtifactCount(playerId: EntityId): Int =
    projectedState.getBattlefieldControlledBy(playerId)
        .count { cardName(it) in JUND_ARTIFACTS }

private fun GameState.cardName(id: EntityId): String? =
    getEntity(id)?.get<CardComponent>()?.name
