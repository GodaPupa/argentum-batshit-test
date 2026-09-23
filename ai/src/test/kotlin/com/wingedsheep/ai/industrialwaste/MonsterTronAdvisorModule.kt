package com.wingedsheep.ai.industrialwaste

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
 * Narrow, test-only policy for the exact Gate 9 PinoIo_Cosmico Monster Tron opponent.
 *
 * The module does not alter cards or deck identity. It only supplies decisions that generic
 * one-ply evaluation can mis-sequence: preserving Tron while tutoring, stabilizing with Pulse,
 * cashing in Bonder's Ornament when mana is already available, and pointing Bojuka Bog at the
 * opponent's graveyard. Nyxborn Hydra keeps the engine's ordinary X expansion; its advisor only
 * makes a materially-sized Hydra attractive once the concrete X has already been chosen.
 */
internal object MonsterTronAdvisorModule : CardAdvisorModule {
    override fun register(registry: CardAdvisorRegistry) {
        registry.register(MonsterTronTutorAdvisor)
        registry.register(MonsterTronNyxbornHydraAdvisor)
        registry.register(MonsterTronPulseAdvisor)
        registry.register(MonsterTronOrnamentAdvisor)
        registry.register(MonsterTronBojukaBogAdvisor)
    }
}

private val MONSTER_TRON_LANDS = listOf("Urza's Mine", "Urza's Power Plant", "Urza's Tower")

private object MonsterTronTutorAdvisor : CardAdvisor {
    override val cardNames = setOf("Expedition Map", "Crop Rotation")

    override fun evaluateCast(context: CastContext): Double? {
        if (monsterMissingTronLands(context.state, context.playerId).isEmpty()) return null

        val cast = context.action.action as? CastSpell
        if (cast != null && context.state.cardName(cast.cardId) == "Crop Rotation") {
            val sacrificed = cast.additionalCostPayment?.sacrificedPermanents?.singleOrNull()
            val sacrificedName = sacrificed?.let(context.state::cardName)
            if (sacrificedName in MONSTER_TRON_LANDS) {
                // Do not turn an assembled/partial Tron piece into the tutor unless generic
                // simulation proves some exceptional reason; the ordinary resource-race line
                // preserves the live piece and sacrifices a spare/fixing land.
                return context.passScore - 25.0
            }
        }
        return context.passScore + 35.0
    }

    override fun respondToDecision(context: AdvisorDecisionContext) = when (val decision = context.decision) {
        is SearchLibraryDecision -> {
            val missing = monsterMissingTronLands(context.state, context.playerId)
            val selected = missing.asSequence().mapNotNull { wanted ->
                decision.options.firstOrNull { id -> decision.cards[id]?.name == wanted }
            }.firstOrNull()
                ?: decision.options.firstOrNull { id -> decision.cards[id]?.name in MONSTER_TRON_LANDS }
            selected?.let { CardsSelectedResponse(decision.id, listOf(it)) }
        }
        is SelectCardsDecision -> {
            val info = decision.cardInfo
            if (info == null && context.sourceCardName == "Crop Rotation") {
                // Additional-cost choice: preserve every live Tron piece when a spare/fixing land
                // is available. This is decision-local and makes no claim that Crop Rotation
                // should always be fired immediately.
                val selected = decision.options.firstOrNull { id ->
                    context.state.cardName(id) !in MONSTER_TRON_LANDS
                } ?: return null
                CardsSelectedResponse(decision.id, listOf(selected))
            } else if (info != null) {
                // Library searches currently surface through SelectCardsDecision.
                val missing = monsterMissingTronLands(context.state, context.playerId)
                val selected = missing.asSequence().mapNotNull { wanted ->
                    decision.options.firstOrNull { id -> info[id]?.name == wanted }
                }.firstOrNull()
                    ?: decision.options.firstOrNull { id -> info[id]?.name in MONSTER_TRON_LANDS }
                selected?.let { CardsSelectedResponse(decision.id, listOf(it)) }
            } else {
                null
            }
        }
        else -> null
    }
}

private object MonsterTronNyxbornHydraAdvisor : CardAdvisor {
    override val cardNames = setOf("Nyxborn Hydra")

    override fun evaluateCast(context: CastContext): Double? {
        val cast = context.action.action as? CastSpell ?: return null
        val x = cast.xValue ?: return null
        // X has already been expanded by Strategist; this does not select or inflate X.
        // It simply recognizes that a 3+ counter Hydra is a real threat worth developing.
        return if (x >= 3) context.passScore + 10.0 + x else null
    }
}

private object MonsterTronPulseAdvisor : CardAdvisor {
    override val cardNames = setOf("Pulse of Murasa")

    override fun evaluateCast(context: CastContext): Double? {
        val lowLife = context.state.lifeTotal(context.playerId) <= 10
        val graveyard = context.state.getZone(context.playerId, Zone.GRAVEYARD)
        val hasPremiumReturn = graveyard.any {
            context.state.cardName(it) in setOf(
                "Nyxborn Hydra", "Writhing Chrysalis", "Bramble Wurm",
                "Urza's Mine", "Urza's Power Plant", "Urza's Tower"
            )
        }
        return if (lowLife && hasPremiumReturn) context.passScore + 18.0 else null
    }

    override fun targetPreference(state: GameState, targetId: EntityId, playerId: EntityId): Double? =
        when (state.cardName(targetId)) {
            "Nyxborn Hydra" -> 100.0
            "Writhing Chrysalis" -> 95.0
            "Bramble Wurm" -> 90.0
            "Urza's Tower" -> 85.0
            "Urza's Power Plant" -> 84.0
            "Urza's Mine" -> 83.0
            "Generous Ent" -> 70.0
            else -> null
        }
}

private object MonsterTronOrnamentAdvisor : CardAdvisor {
    override val cardNames = setOf("Bonder's Ornament")

    override fun evaluateCast(context: CastContext): Double? {
        if (context.action.action !is ActivateAbility) return null
        val handSize = context.state.getZone(context.playerId, Zone.HAND).size
        val otherMana = context.state.projectedState.getBattlefieldControlledBy(context.playerId)
            .count { id ->
                id != (context.action.action as ActivateAbility).sourceId &&
                    context.state.cardName(id) in (
                        MONSTER_TRON_LANDS + listOf(
                            "Forest", "Conduit Pylons", "Wooded Ridgeline",
                            "Bonder's Ornament"
                        )
                    )
            }
        // When four other mana sources are already present and the hand is depleted, cashing
        // the Ornament into a card is the intended value line instead of hoarding its mana mode.
        return if (handSize <= 2 && otherMana >= 4) context.passScore + 14.0 else null
    }
}

private object MonsterTronBojukaBogAdvisor : CardAdvisor {
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

private fun monsterMissingTronLands(state: GameState, playerId: EntityId): List<String> {
    val accessible = state.projectedState.getBattlefieldControlledBy(playerId) +
        state.getZone(playerId, Zone.HAND)
    val names = accessible.mapNotNull(state::cardName).toSet()
    return MONSTER_TRON_LANDS.filterNot(names::contains)
}

private fun GameState.cardName(id: EntityId): String? =
    getEntity(id)?.get<CardComponent>()?.name
