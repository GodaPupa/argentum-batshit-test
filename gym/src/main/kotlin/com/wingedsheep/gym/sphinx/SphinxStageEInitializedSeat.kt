package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.InitializationResult
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.identity.OwnerComponent
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId

/**
 * Own-list knowledge bound by the trusted initializer boundary to one actual opening seat.
 * Only the submitted list, actor and source/trial identity survive construction. No opening
 * hand, library entities/order, engine state, initialization seed or opponent data is retained.
 * This component cannot initialize or advance a game and grants no execution admission.
 */
class SphinxStageEInitializedSeat private constructor(
    val actorId: EntityId,
    val ownDeckSha256: String,
    private val sourceVersion: String,
    private val trialId: String,
    private val ownDeck: SphinxStageEOwnDeck,
) {
    /** The runner supplies its current epoch; callers cannot substitute a different frozen list. */
    fun decideCurrentCast(
        input: ActorInput,
        expectedEpoch: ActorEpoch,
        offerIndex: Int,
        componentCall: SphinxStageEComponentCall,
    ): SphinxStageEAdapterResult {
        require(expectedEpoch.sourceVersion == sourceVersion && expectedEpoch.trialId == trialId) {
            "Actor epoch belongs to a different initialized source or trial"
        }
        return SphinxStageEActorAdapter.decideCurrentCast(
            input, expectedEpoch, actorId, ownDeck, offerIndex, componentCall,
        )
    }

    /**
     * The opening seam remains a source candidate. A trusted runner must provide the current
     * canonical actor epoch; this method cannot read initialization state or advance the game.
     */
    fun decideOpening(input: ActorInput, expectedEpoch: ActorEpoch): SphinxStageEAdapterResult {
        require(expectedEpoch.sourceVersion == sourceVersion && expectedEpoch.trialId == trialId) {
            "Actor epoch belongs to a different initialized source or trial"
        }
        return SphinxStageEOpeningActor.decide(input, expectedEpoch, actorId)
    }

    /** Current actor-visible cantrip/search choice; later Ponder shuffle remains unqualified. */
    fun decideVisibleChoice(input: ActorInput, expectedEpoch: ActorEpoch): SphinxStageEAdapterResult {
        require(expectedEpoch.sourceVersion == sourceVersion && expectedEpoch.trialId == trialId) {
            "Actor epoch belongs to a different initialized source or trial"
        }
        return SphinxStageEVisibleChoice.decide(input, expectedEpoch, actorId, ownDeck)
    }

    /** Trusted runner calls only after it has accepted and journaled this exact reorder. */
    internal fun rememberAcceptedPonderReorder(input: ActorInput, expectedEpoch: ActorEpoch,
                                               accepted: com.wingedsheep.gym.actorinput.ActorProposal): SphinxStageEPonderMemory {
        require(expectedEpoch.sourceVersion == sourceVersion && expectedEpoch.trialId == trialId)
        return SphinxStageEPonderMemory.afterAcceptedReorder(input, expectedEpoch, actorId, ownDeck, accepted)
    }

    fun decidePonderShuffle(input: ActorInput, expectedEpoch: ActorEpoch,
                            memory: SphinxStageEPonderMemory): SphinxStageEAdapterResult {
        require(expectedEpoch.sourceVersion == sourceVersion && expectedEpoch.trialId == trialId)
        return memory.decide(input, expectedEpoch, actorId, ownDeck)
    }

    companion object {
        /**
         * Trusted-runner boundary, called on the actual GameInitializer result before mulligans.
         * The runner must bind that result, this trial identity and later ActorInput epochs to its
         * real initialized game. Authenticating the runner/source, replay and gameplay admission
         * remains a separate required gate; accepting a data object is not proof of those gates.
         *
         * The privileged scan checks only this actor's initialized cards. It compares the full
         * multiset with the exact CSV, then discards every hidden identity/order/reference.
         */
        internal fun bindOpening(
            initialized: InitializationResult,
            actorId: EntityId,
            ownCsvBytes: ByteArray,
            initialEpoch: ActorEpoch,
        ): SphinxStageEInitializedSeat {
            require(initialEpoch.step == 0L) { "Opening binding requires the initial actor epoch" }
            val ownDeck = SphinxStageEOwnDeck.fromFrozenCsv(ownCsvBytes)
            val state = initialized.state
            val players = initialized.playerIds
            require(players.size == 2 && players.distinct().size == 2 && actorId in players) {
                "Opening binding requires an actual seat in a two-player initialization"
            }
            require(state.turnOrder.size == 2 && state.turnOrder.toSet() == players.toSet())
            require(state.entities.filterValues { it.has<PlayerComponent>() }.keys == players.toSet())
            require(!state.gameOver && state.winnerId == null && state.turnNumber == 1 &&
                state.phase == Phase.BEGINNING && state.step == Step.UNTAP &&
                state.pendingDecision == null && state.stack.isEmpty()) {
                "Binding must use the actual initial opening before gameplay"
            }
            require(players.all { player ->
                state.getEntity(player)?.get<MulliganStateComponent>()?.let {
                    it.mulligansTaken == 0 && !it.hasKept && !it.leylinePhaseStarted &&
                        it.pendingLeylineCardIds.isEmpty()
                } == true
            }) { "Binding must precede every mulligan or opening-hand decision" }

            val hand = state.getHand(actorId)
            val library = state.getLibrary(actorId)
            val openingIds = hand + library
            require(hand.size == 7 && library.size == 53 && openingIds.distinct().size == 60) {
                "Actual initialized own zones must contain seven hand and 53 library cards"
            }
            val ownedEntities = state.entities.filterValues { entity ->
                entity.get<OwnerComponent>()?.playerId == actorId ||
                    entity.get<CardComponent>()?.ownerId == actorId
            }
            require(ownedEntities.keys == openingIds.toSet()) {
                "Initialized ownership differs from the exact own 60 in hand and library"
            }
            val allZoneOccurrences = state.zones.values.flatten().groupingBy { it }.eachCount()
            require(openingIds.all { allZoneOccurrences[it] == 1 }) {
                "An initialized own card is duplicated across zones"
            }
            require(state.zones.all { (zone, ids) ->
                ids.none { it in ownedEntities } ||
                    (zone.ownerId == actorId && zone.zoneType in setOf(Zone.HAND, Zone.LIBRARY))
            }) { "Initialized own cards appear in another seat or a nonopening zone" }
            val actualCounts = openingIds.map { id ->
                val entity = ownedEntities.getValue(id)
                val card = requireNotNull(entity.get<CardComponent>())
                require(entity.get<OwnerComponent>()?.playerId == actorId && card.ownerId == actorId) {
                    "Actual initialized card ownership fields disagree"
                }
                card.name
            }.groupingBy { it }.eachCount()
            require(actualCounts == ownDeck.cards) {
                "The supplied frozen CSV is not the deck actually initialized for this actor"
            }
            return SphinxStageEInitializedSeat(
                actorId, ownDeck.sha256, initialEpoch.sourceVersion, initialEpoch.trialId, ownDeck,
            )
        }
    }
}
