package com.wingedsheep.gym.telemetry

import com.wingedsheep.ai.engine.GameSimulator
import com.wingedsheep.ai.engine.SimulationResult
import com.wingedsheep.ai.engine.knowledge.IntentCatalog
import com.wingedsheep.ai.engine.knowledge.IntentTag
import com.wingedsheep.engine.core.PlayLand
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.handlers.PredicateContext
import com.wingedsheep.engine.handlers.effects.BattlefieldFilterUtils
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

enum class ManaConstraint { TOTAL_MANA, COLOR, TAPLAND }

/** A deduplicated, actionable inability to pay for one strategically relevant card. */
@Serializable
data class ActionableManaBottleneck(
    val cardId: EntityId,
    val cardName: String,
    val turn: Int,
    val constraint: ManaConstraint,
    val requiredMana: Int,
    val availableMana: Int,
)

/**
 * Reports mana constraints only after accounting for a legal land play and every mana source the
 * authoritative [ManaSolver] recognizes, including explicit sacrifice-for-mana sources.
 * Re-observing the same unresolved card/constraint produces no second record.
 */
class ActionableManaBottleneckTracker(private val registry: CardRegistry) {
    private val solver = ManaSolver(registry)
    private val simulator = GameSimulator(registry)
    private val intents = IntentCatalog.of(registry)
    private val seen = mutableSetOf<Pair<EntityId, ManaConstraint>>()

    fun observe(state: GameState, playerId: EntityId, turn: Int): List<ActionableManaBottleneck> {
        if (state.pendingDecision != null || state.stack.isNotEmpty() || state.priorityPlayerId != playerId) return emptyList()

        val landStates = legalLandPlayStates(state, playerId)
        val candidateStates = landStates.map(PossibleLandState::state).ifEmpty { listOf(state) }
        val records = mutableListOf<ActionableManaBottleneck>()

        for (cardId in state.getZone(playerId, Zone.HAND)) {
            val card = state.getEntity(cardId)?.get<CardComponent>() ?: continue
            if (card.isLand || card.manaCost.isEmpty() ||
                !isStrategicallyRelevant(state, playerId, cardId, card)
            ) continue
            if (candidateStates.any { solver.canPay(it, playerId, card.manaCost) }) continue

            val taplandState = landStates.firstOrNull { possible ->
                val landId = possible.playedLandId ?: return@firstOrNull false
                possible.state.getEntity(landId)?.has<TappedComponent>() == true &&
                    solver.canPay(
                        possible.state.updateEntity(landId) { it.without<TappedComponent>() },
                        playerId,
                        card.manaCost,
                    )
            }
            val bestState = candidateStates.maxByOrNull { solver.getAvailableManaCount(it, playerId) } ?: state
            val available = solver.getAvailableManaCount(bestState, playerId)
            val constraint = when {
                taplandState != null -> ManaConstraint.TAPLAND
                available < card.manaCost.cmc -> ManaConstraint.TOTAL_MANA
                else -> ManaConstraint.COLOR
            }
            if (seen.add(cardId to constraint)) {
                records += ActionableManaBottleneck(cardId, card.name, turn, constraint, card.manaCost.cmc, available)
            }
        }
        return records
    }

    private fun legalLandPlayStates(state: GameState, playerId: EntityId): List<PossibleLandState> =
        simulator.getLegalActions(state, playerId).mapNotNull { legal ->
            val play = legal.action as? PlayLand ?: return@mapNotNull null
            val result = simulator.simulate(state, play)
            if (result is SimulationResult.Illegal || result is SimulationResult.StoppedAtLimit) return@mapNotNull null
            PossibleLandState(result.state, play.cardId)
        }

    private fun isStrategicallyRelevant(
        state: GameState,
        playerId: EntityId,
        cardId: EntityId,
        card: CardComponent,
    ): Boolean {
        if (card.isPermanent) return true
        val opponents = state.turnOrder.filter { state.isOpponentOf(it, playerId) }
        intents.pureForcedSacrificeEffects(card.name)?.let { effects ->
            return effects.any { effect ->
                opponents.any { opponentId ->
                    BattlefieldFilterUtils.findMatchingOnBattlefield(
                        state,
                        effect.filter.youControl(),
                        PredicateContext(controllerId = opponentId, sourceId = cardId),
                    ).isNotEmpty()
                }
            }
        }
        val intent = intents.forName(card.name) ?: return true
        val answerTags = setOf(IntentTag.REMOVAL, IntentTag.EXILE_REMOVAL, IntentTag.NEUTRALIZE, IntentTag.FIGHT)
        val isPureAnswer = intent.tags.isNotEmpty() && intent.tags.all { it in answerTags }
        if (!isPureAnswer) return true
        val opposingPermanents = opponents.flatMap(state::controlledBattlefield).toSet()
        return simulator.getLegalActions(state, playerId).any { legal ->
            val cast = legal.action as? CastSpell ?: return@any false
            cast.cardId == cardId && legal.validTargets.orEmpty().any(opposingPermanents::contains)
        }
    }

    private data class PossibleLandState(val state: GameState, val playedLandId: EntityId?)
}
