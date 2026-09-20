package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.ai.arena.ArenaTrainingObserver
import com.wingedsheep.engine.core.CardsDrawnEvent
import com.wingedsheep.engine.core.DamageDealtEvent
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.LifeChangeReason
import com.wingedsheep.engine.core.LifeChangedEvent
import com.wingedsheep.engine.mechanics.mana.ManaSolver
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.engine.state.components.player.PlayerTurnsTakenComponent
import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/** Metrics available from an inert-opponent game without claiming matchup evidence. */
@Serializable
data class IndustrialWasteGoldfishMetrics(
    val mulligans: Int,
    val tronTurn: Int?,
    val tronByTurn3: Boolean,
    val tronByTurn4: Boolean,
    val tronByTurn5: Boolean,
    val coloredManaFailureTurns: Int,
    val retrieverLoopAvailableTurn: Int?,
    val comboReadyTurn: Int?,
    val lethalTurn: Int?,
    val redundantPayoffDraws: Int,
    val nonInfinitePactdollLifeLoss: Int,
    val combatDamageToOpponent: Int,
)

/**
 * Event-backed collector for seat 0 of an Industrial Waste goldfish.
 *
 * `nonInfinitePactdollLifeLoss` is valid only against the protocol's inert Forest opponent: in
 * that environment Pactdoll Terror is the sole noncombat source of opponent life loss. The count
 * excludes transitions where the Retriever loop was already available.
 */
class IndustrialWasteGoldfishObserver(registry: CardRegistry) : ArenaTrainingObserver {
    private val manaSolver = ManaSolver(registry)
    private lateinit var player: EntityId
    private lateinit var opponent: EntityId
    private var mulligans = 0
    private var tronTurn: Int? = null
    private var loopTurn: Int? = null
    private var comboTurn: Int? = null
    private var lethalTurn: Int? = null
    private var redundantPayoffDraws = 0
    private var nonInfinitePactdollLifeLoss = 0
    private var combatDamageToOpponent = 0
    private val coloredFailureTurns = mutableSetOf<Int>()
    private val inspectedMainPhases = mutableSetOf<Int>()

    override fun gameStarted(state: GameState, seats: List<EntityId>) {
        require(seats.size == 2) { "Industrial Waste goldfish telemetry requires a duel" }
        player = seats[0]
        opponent = seats[1]
        observePosition(state)
    }

    override fun quietRoot(state: GameState, actingPlayer: EntityId) {
        observePosition(state)
        val turn = ownTurn(state)
        if (actingPlayer != player || state.activePlayerId != player ||
            state.step != Step.PRECOMBAT_MAIN || !inspectedMainPhases.add(turn)
        ) return

        val strandedForColor = state.getZone(player, Zone.HAND)
            .mapNotNull { state.getEntity(it)?.get<CardComponent>() }
            .filter { it.manaCost.colors.isNotEmpty() }
            .any { card ->
                val genericEquivalent = ManaCost.parse("{${card.manaCost.cmc}}")
                manaSolver.canPay(state, player, genericEquivalent) &&
                    !manaSolver.canPay(state, player, card.manaCost)
            }
        if (strandedForColor) coloredFailureTurns += turn
    }

    override fun transition(
        before: GameState,
        acceptedAction: GameAction,
        after: GameState,
        events: List<GameEvent>,
    ) {
        observeMulligans(before)
        observeMulligans(after)

        val loopWasAvailable = retrieverLoopAvailable(before)
        val hadPactdoll = battlefieldNames(before).contains("Pactdoll Terror")
        events.forEach { event ->
            when (event) {
                is CardsDrawnEvent -> countRedundantPayoffs(before, event)
                is LifeChangedEvent -> if (
                    event.playerId == opponent && event.reason == LifeChangeReason.LIFE_LOSS &&
                    hadPactdoll && !loopWasAvailable
                ) {
                    nonInfinitePactdollLifeLoss += (event.oldLife - event.newLife).coerceAtLeast(0)
                }
                is DamageDealtEvent -> if (
                    event.targetId == opponent && event.targetIsPlayer && event.isCombatDamage
                ) combatDamageToOpponent += event.amount
                else -> Unit
            }
        }
        if (before.lifeTotal(opponent) > 0 && after.lifeTotal(opponent) <= 0) {
            lethalTurn = lethalTurn ?: ownTurn(after)
        }
        observePosition(after)
    }

    fun snapshot(): IndustrialWasteGoldfishMetrics = IndustrialWasteGoldfishMetrics(
        mulligans = mulligans,
        tronTurn = tronTurn,
        tronByTurn3 = tronTurn?.let { it <= 3 } == true,
        tronByTurn4 = tronTurn?.let { it <= 4 } == true,
        tronByTurn5 = tronTurn?.let { it <= 5 } == true,
        coloredManaFailureTurns = coloredFailureTurns.size,
        retrieverLoopAvailableTurn = loopTurn,
        comboReadyTurn = comboTurn,
        lethalTurn = lethalTurn,
        redundantPayoffDraws = redundantPayoffDraws,
        nonInfinitePactdollLifeLoss = nonInfinitePactdollLifeLoss,
        combatDamageToOpponent = combatDamageToOpponent,
    )

    private fun observePosition(state: GameState) {
        if (!::player.isInitialized) return
        observeMulligans(state)
        val turn = ownTurn(state)
        val battlefield = battlefieldNames(state)
        if (tronTurn == null && TRON_NAMES.all(battlefield::contains)) tronTurn = turn
        val loop = retrieverLoopAvailable(state)
        if (loopTurn == null && loop) loopTurn = turn
        if (comboTurn == null && loop && battlefield.any(PAYOFF_NAMES::contains)) comboTurn = turn
    }

    private fun observeMulligans(state: GameState) {
        val count = state.getEntity(player)?.get<MulliganStateComponent>()?.mulligansTaken ?: 0
        mulligans = maxOf(mulligans, count)
    }

    private fun ownTurn(state: GameState): Int =
        state.getEntity(player)?.get<PlayerTurnsTakenComponent>()?.count ?: 0

    private fun battlefieldNames(state: GameState): List<String> = state.controlledBattlefield(player)
        .mapNotNull { state.getEntity(it)?.get<CardComponent>()?.name }

    private fun retrieverLoopAvailable(state: GameState): Boolean {
        val battlefield = battlefieldNames(state)
        val graveyard = state.getZone(player, Zone.GRAVEYARD)
            .mapNotNull { state.getEntity(it)?.get<CardComponent>()?.name }
        return "Ashnod's Altar" in battlefield && "Myr Retriever" in battlefield &&
            "Myr Retriever" in graveyard
    }

    private fun countRedundantPayoffs(before: GameState, event: CardsDrawnEvent) {
        if (event.playerId != player || ownTurn(before) == 0) return
        var accessiblePayoffs = (
            before.getZone(player, Zone.HAND) + before.controlledBattlefield(player)
        ).count { id -> stateName(before, id) in PAYOFF_NAMES }
        event.cardNames.forEach { name ->
            if (name in PAYOFF_NAMES) {
                if (accessiblePayoffs > 0) redundantPayoffDraws++
                accessiblePayoffs++
            }
        }
    }

    private fun stateName(state: GameState, id: EntityId): String? =
        state.getEntity(id)?.get<CardComponent>()?.name

    private companion object {
        val TRON_NAMES = setOf("Urza's Mine", "Urza's Power Plant", "Urza's Tower")
        val PAYOFF_NAMES = setOf("Pactdoll Terror", "Golem Foundry")
    }
}
