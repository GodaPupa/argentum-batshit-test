package com.wingedsheep.ai.industrialwaste

import com.wingedsheep.engine.core.ActivateAbility
import com.wingedsheep.engine.core.AttackersDeclaredEvent
import com.wingedsheep.engine.core.CountersAddedEvent
import com.wingedsheep.engine.core.CountersRemovedEvent
import com.wingedsheep.engine.core.CardsDrawnEvent
import com.wingedsheep.engine.core.CardsRevealedEvent
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.ExecutionResult
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameEvent
import com.wingedsheep.engine.core.LookedAtCardsEvent
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.SpellCastEvent
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.ZoneChangeEvent
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.identity.RevealedToComponent
import com.wingedsheep.engine.state.components.identity.TokenComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.engine.state.components.player.PlayerTurnsTakenComponent
import com.wingedsheep.sdk.core.Counters
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

@Serializable
internal data class IndustrialWasteV2LoopCycle(
    val sacrificeTransition: Int,
    val returnTransition: Int,
    val castTransition: Int,
    val entryTransition: Int,
    val ownTurn: Int,
    val altar: String,
    val sacrificedRetriever: String,
    val returnedRetriever: String,
    val manaBefore: List<Int>,
    val manaAfter: List<Int>,
)

/** Event-derived subset only; deliberately lacks the complete R1 decision-rule input flags. */
@Serializable
internal data class IndustrialWasteV2EventMetrics(
    val acceptedTransitions: Int,
    val mulligans: Int,
    val firstSeenTurnByOriginalCopy: Map<String, Int>,
    val cardsSeenByTurn: Map<Int, Int>,
    val firstAccessTurnByTronName: Map<String, Int>,
    val threeTronPieceAccessTurn: Int?,
    val fullTronTurn: Int?,
    val demonstratedLoopReadyTurn: Int?,
    val demonstratedLoopCycles: List<IndustrialWasteV2LoopCycle>,
    val artifactReturnsFollowedByRecast: Int,
    val actualLethalTurn: Int?,
    val certifiedFutureConversionTurn: Int?,
    val deterministicConversionTurn: Int?,
)

/**
 * Observer for accepted R1 transitions. It never chooses an action or exposes hidden input to a
 * pilot. Original-copy labels come from the separately verified initialization binding.
 * Seen cards require the acting player's hand/public zones, an actual reveal/draw event, or
 * explicit RevealedTo entitlement. Merely possessing a library mapping proves no observation.
 *
 * A loop is not inferred from inventory. It requires a real Altar sacrifice and +2 mana, an
 * intervening return of another Retriever, an accepted two-mana cast, actual battlefield entry,
 * swapped Retriever zones, and restored hand/mana/untapped-permanent resources in the same turn.
 * Passes and real decision responses may intervene; another development action abandons the
 * candidate proof. A Foundry future-conversion certificate is even stricter: the transcript must
 * contain three real charge placements on one controlled Golem Foundry, payment of exactly three
 * of those counters for its token ability, a real 3/3 artifact Golem token entry, an independently
 * demonstrated neutral Retriever loop, and a later accepted attack by that exact token against the
 * passive opponent after summoning sickness has cleared. Only then may the collector retrospectively
 * certify the earliest demonstrated loop turn as deterministic future conversion. No winner or
 * damage is synthesized. Mana-stranding flags, caps, artifact provenance and official execution
 * authority remain separate gates.
 */
internal class IndustrialWasteV2EventCollector(
    initial: GameState,
    private val player: EntityId,
    originalCopies: Map<EntityId, String>,
    initialEvents: List<GameEvent> = emptyList(),
) {
    private val copies = originalCopies.toMap()
    private val opponent = initial.getOpponents(player).single()
    private var expectedState = initial
    private var transitions = 0
    private var maxMulligans = 0
    private val seen = linkedMapOf<String, Int>()
    private val access = linkedMapOf<String, Int>()
    private var fullTron: Int? = null
    private var lethal: Int? = null
    private val returnedArtifacts = mutableSetOf<EntityId>()
    private var recasts = 0
    private val loops = mutableListOf<IndustrialWasteV2LoopCycle>()
    private var pending: PendingLoop? = null
    private val foundryChargeAdds = mutableMapOf<EntityId, Int>()
    private var pendingFoundryActivation: PendingFoundryActivation? = null
    private val foundryTokenProofs = mutableMapOf<EntityId, FoundryTokenProof>()
    private var certifiedFutureConversion: Int? = null

    init {
        require(copies.values.distinct().size == copies.size)
        copies.forEach { (id, label) ->
            val entity = initial.getEntity(id) ?: error("Unknown original card copy")
            val card = entity.get<CardComponent>() ?: error("Original copy is not a card")
            require(card.ownerId == player && !entity.has<TokenComponent>())
            require(label.substringBeforeLast('#') == card.name)
            require(label.substringAfterLast('#').toIntOrNull()?.let { it > 0 } == true)
        }
        observeVisibility(initial)
        observeEvents(initialEvents, initial)
    }

    fun record(before: GameState, action: GameAction, result: ExecutionResult) {
        require(before == expectedState) { "Telemetry transition is missing, duplicated or reordered" }
        require(result.error == null && (result.isSuccess || result.isPaused)) { "Rejected actions cannot become sampled evidence" }
        val after = result.newState
        transitions++
        val oldPending = pending
        if (oldPending != null && (before.turnNumber != oldPending.gameTurn ||
            (action !is PassPriority && action !is SubmitDecision &&
                !(action is CastSpell && action.cardId == oldPending.returnedId)))) {
            pending = null
        }
        if (action is ActivateAbility && action.playerId == player && before.name(action.sourceId) == "Ashnod's Altar") {
            beginLoop(before, action, after, result.events)
        }
        if (action is ActivateAbility && action.playerId == player && before.name(action.sourceId) == "Golem Foundry") {
            beginFoundryActivation(before, action, result.events)
        }
        observeEvents(result.events, after)
        result.events.filterIsInstance<ZoneChangeEvent>().forEach { event ->
            if (event.ownerId == player && event.fromZone == Zone.GRAVEYARD && event.toZone == Zone.HAND &&
                event.entityId in copies && after.getEntity(event.entityId)?.get<CardComponent>()?.typeLine?.isArtifact == true) {
                returnedArtifacts += event.entityId
                pending?.let { candidate ->
                    if (candidate.returnedId == null && event.entityId in candidate.alternates) {
                        pending = candidate.copy(returnedId = event.entityId, returnTransition = transitions)
                    }
                }
            }
        }
        result.events.filterIsInstance<SpellCastEvent>().forEach { event ->
            if (event.casterId == player && returnedArtifacts.remove(event.spellEntityId)) recasts++
            pending?.let { candidate ->
                if (action is CastSpell && action.playerId == player && action.cardId == candidate.returnedId &&
                    event.spellEntityId == candidate.returnedId && event.casterId == player && event.totalManaSpent == 2) {
                    pending = candidate.copy(castTransition = transitions)
                }
            }
        }
        finishLoop(after, result.events)
        observeFoundryTokenEntry(after, result.events)
        observeFoundryCombat(after, result.events)
        observeVisibility(after)
        if (lethal == null && after.gameOver && after.winnerId == player && after.life(opponent) <= 0) lethal = after.ownTurn()
        expectedState = after
    }

    fun snapshot() = IndustrialWasteV2EventMetrics(
        transitions, maxMulligans, seen.toMap(), seen.values.groupingBy { it }.eachCount().toSortedMap(), access.toMap(),
        if (TRON.all(access::containsKey)) access.values.maxOrNull() else null,
        fullTron, loops.firstOrNull()?.ownTurn, loops.toList(), recasts, lethal,
        certifiedFutureConversion,
        listOfNotNull(lethal, certifiedFutureConversion).minOrNull(),
    )

    private fun observeEvents(events: List<GameEvent>, state: GameState) {
        events.forEach { event ->
            when (event) {
                is CardsDrawnEvent -> if (event.playerId == player) markSeen(event.cardIds, state.ownTurn())
                is LookedAtCardsEvent -> if (event.playerId == player) markSeen(event.cardIds, state.ownTurn())
                is CardsRevealedEvent -> if (event.revealingPlayerId != player || event.revealToSelf) markSeen(event.cardIds, state.ownTurn())
                is ZoneChangeEvent -> if (event.ownerId == player && event.toZone in setOf(Zone.HAND, Zone.BATTLEFIELD)) {
                    markAccess(event.entityId, state.ownTurn())
                }
                is CountersAddedEvent -> if (
                    event.placedBy == player &&
                    event.entityName == "Golem Foundry" &&
                    event.counterType.equals(Counters.CHARGE, ignoreCase = true)
                ) {
                    foundryChargeAdds[event.entityId] =
                        (foundryChargeAdds[event.entityId] ?: 0) + event.amount
                }
                else -> Unit
            }
        }
    }

    private fun observeVisibility(state: GameState) {
        val turn = state.ownTurn()
        maxMulligans = maxOf(maxMulligans, state.getEntity(player)?.get<MulliganStateComponent>()?.mulligansTaken ?: 0)
        val ownBoard = state.projectedState.getBattlefieldControlledBy(player)
        markSeen(state.getHand(player) + ownBoard + state.getGraveyard(player), turn)
        copies.keys.filter { state.getEntity(it)?.get<RevealedToComponent>()?.isRevealedTo(player) == true }
            .let { markSeen(it, turn) }
        (state.getHand(player) + ownBoard).forEach { markAccess(it, turn) }
        if (fullTron == null && TRON.all { name -> ownBoard.any {
                (state.projectedState.getName(it) ?: state.name(it)) == name && state.projectedState.hasType(it, "LAND")
            } }) fullTron = turn
    }

    private fun markSeen(ids: Iterable<EntityId>, turn: Int) {
        ids.forEach { id -> copies[id]?.let { seen.putIfAbsent(it, turn) } }
    }

    private fun markAccess(id: EntityId, turn: Int) {
        val name = copies[id]?.substringBeforeLast('#') ?: return
        if (name in TRON) access.putIfAbsent(name, turn)
    }

    private fun beginLoop(before: GameState, action: ActivateAbility, after: GameState, events: List<GameEvent>) {
        val sacrificed = action.costPayment?.sacrificedPermanents?.singleOrNull() ?: return
        if (action.sourceId !in copies || sacrificed !in copies || before.name(sacrificed) != "Myr Retriever") return
        if (sacrificed !in before.projectedState.getBattlefieldControlledBy(player)) return
        val alternates = before.getGraveyard(player).filter { it in copies && before.name(it) == "Myr Retriever" }.toSet()
        if (alternates.isEmpty()) return
        if (events.none { it is ZoneChangeEvent && it.entityId == sacrificed && it.fromZone == Zone.BATTLEFIELD &&
                it.toZone == Zone.GRAVEYARD && it.wasSacrificed }) return
        val pool = before.pool()
        val expectedPool = pool.manaVector().toMutableList().also { it[5] += 2 }
        if (after.pool().manaVector() != expectedPool || pool.restrictedMana.isNotEmpty()) return
        val board = before.projectedState.getBattlefieldControlledBy(player).toSet() - sacrificed
        pending = PendingLoop(
            transitions, before.turnNumber, before.ownTurn(), action.sourceId, sacrificed, alternates,
            pool.manaVector(), before.getHand(player).toSet(), before.getLibrary(player).size,
            board, board.filterNot { before.getEntity(it)!!.has<TappedComponent>() }.toSet(), before.life(player),
        )
    }

    private fun beginFoundryActivation(
        before: GameState,
        action: ActivateAbility,
        events: List<GameEvent>,
    ) {
        if (action.sourceId !in before.projectedState.getBattlefieldControlledBy(player)) return
        if ((foundryChargeAdds[action.sourceId] ?: 0) < 3) return
        val removed = events.filterIsInstance<CountersRemovedEvent>().any { event ->
            event.entityId == action.sourceId &&
                event.counterType.equals(Counters.CHARGE, ignoreCase = true) &&
                event.amount == 3
        }
        if (!removed) return
        pendingFoundryActivation = PendingFoundryActivation(
            foundryId = action.sourceId,
            activationTransition = transitions,
            ownTurn = before.ownTurn(),
            earliestLoopTurn = loops.firstOrNull()?.ownTurn,
        )
    }

    private fun observeFoundryTokenEntry(after: GameState, events: List<GameEvent>) {
        val pendingActivation = pendingFoundryActivation ?: return
        val tokenEntry = events.filterIsInstance<ZoneChangeEvent>().firstOrNull { event ->
            if (event.fromZone != null || event.toZone != Zone.BATTLEFIELD || event.ownerId != player) return@firstOrNull false
            val entity = after.getEntity(event.entityId) ?: return@firstOrNull false
            entity.has<TokenComponent>() &&
                after.projectedState.isCreature(event.entityId) &&
                after.projectedState.hasType(event.entityId, "ARTIFACT") &&
                after.projectedState.hasSubtype(event.entityId, "Golem") &&
                (after.projectedState.getPower(event.entityId) ?: 0) == 3 &&
                (after.projectedState.getToughness(event.entityId) ?: 0) == 3
        } ?: return
        foundryTokenProofs[tokenEntry.entityId] = FoundryTokenProof(
            foundryId = pendingActivation.foundryId,
            activationTransition = pendingActivation.activationTransition,
            tokenEntryTransition = transitions,
            tokenCreatedOwnTurn = after.ownTurn(),
            earliestLoopTurn = pendingActivation.earliestLoopTurn,
        )
        pendingFoundryActivation = null
    }

    private fun observeFoundryCombat(after: GameState, events: List<GameEvent>) {
        if (certifiedFutureConversion != null) return
        for (event in events.filterIsInstance<AttackersDeclaredEvent>()) {
            if (event.attackingPlayerId != player) continue
            val token = event.attackers.firstOrNull { it in foundryTokenProofs && it in event.attackersAgainstPlayer }
                ?: continue
            val proof = foundryTokenProofs.getValue(token)
            val loopTurn = proof.earliestLoopTurn ?: continue
            if (after.ownTurn() <= proof.tokenCreatedOwnTurn) continue
            if (after.getEntity(token)?.has<com.wingedsheep.engine.state.components.battlefield.SummoningSicknessComponent>() == true) continue
            // The attack itself is accepted engine evidence that the token survived to a legal
            // future combat. Combined with an earlier neutral Retriever loop and three real
            // Foundry charge placements/token creation, the frozen passive fixture can be forced
            // to lethal by repeating the already-demonstrated loop enough times to mint attackers.
            certifiedFutureConversion = loopTurn
            return
        }
    }

    private data class PendingFoundryActivation(
        val foundryId: EntityId,
        val activationTransition: Int,
        val ownTurn: Int,
        val earliestLoopTurn: Int?,
    )

    private data class FoundryTokenProof(
        val foundryId: EntityId,
        val activationTransition: Int,
        val tokenEntryTransition: Int,
        val tokenCreatedOwnTurn: Int,
        val earliestLoopTurn: Int?,
    )

    private fun finishLoop(after: GameState, events: List<GameEvent>) {
        val candidate = pending ?: return
        val returned = candidate.returnedId ?: return
        if (candidate.castTransition == null || candidate.returnTransition == null) return
        if (events.none { it is ZoneChangeEvent && it.entityId == returned && it.fromZone == Zone.STACK && it.toZone == Zone.BATTLEFIELD }) return
        val board = after.projectedState.getBattlefieldControlledBy(player).toSet()
        if (after.turnNumber != candidate.gameTurn || returned !in board || candidate.sacrificed !in after.getGraveyard(player) ||
            candidate.altar !in board || !board.containsAll(candidate.retainedBoard) ||
            candidate.untapped.any { it !in board || after.getEntity(it)!!.has<TappedComponent>() } ||
            after.pool().manaVector() != candidate.mana || after.pool().restrictedMana.isNotEmpty() ||
            after.getHand(player).toSet() != candidate.hand || after.getLibrary(player).size != candidate.librarySize ||
            after.life(player) < candidate.life) {
            pending = null
            return
        }
        loops += IndustrialWasteV2LoopCycle(
            candidate.start, candidate.returnTransition, candidate.castTransition, transitions, candidate.ownTurn,
            copies.getValue(candidate.altar), copies.getValue(candidate.sacrificed), copies.getValue(returned),
            candidate.mana, after.pool().manaVector(),
        )
        pending = null
    }

    private data class PendingLoop(
        val start: Int, val gameTurn: Int, val ownTurn: Int, val altar: EntityId, val sacrificed: EntityId,
        val alternates: Set<EntityId>, val mana: List<Int>, val hand: Set<EntityId>, val librarySize: Int,
        val retainedBoard: Set<EntityId>, val untapped: Set<EntityId>, val life: Int,
        val returnedId: EntityId? = null, val returnTransition: Int? = null, val castTransition: Int? = null,
    )

    private fun GameState.ownTurn() = getEntity(player)!!.get<PlayerTurnsTakenComponent>()!!.count
    private fun GameState.pool() = getEntity(player)!!.get<ManaPoolComponent>()!!
    private fun GameState.life(id: EntityId) = getEntity(id)!!.get<LifeTotalComponent>()!!.life
    private fun GameState.name(id: EntityId) = getEntity(id)?.get<CardComponent>()?.name
    private fun ManaPoolComponent.manaVector() = listOf(white, blue, black, red, green, colorless)
    private companion object { val TRON = setOf("Urza's Mine", "Urza's Power Plant", "Urza's Tower") }
}
