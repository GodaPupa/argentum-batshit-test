package com.wingedsheep.gym.pest

import com.wingedsheep.engine.core.CardEntityFactory
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.ComponentContainer
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.LifeTotalComponent
import com.wingedsheep.engine.state.components.identity.PlayerComponent
import com.wingedsheep.engine.state.components.player.LandDropsComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.model.GameRng

internal data class PestPhaseBDeterministicFixture(
    val state: GameState,
    val roster: List<EntityId>,
    val orderedHandIds: List<EntityId>,
    val canonicalLibraryIds: List<EntityId>,
)

/**
 * Builds the exact own 60 without GameInitializer or fresh entropy.
 *
 * The library is a canonical inventory required by the frozen raw M5 existence checks. Its order is
 * deliberately non-authoritative: the current raw controller selects a typecycling target by
 * intrinsic definition identity rather than hidden position. Future review must preserve that
 * property; this helper does not grant permission to use hidden library order.
 */
internal object PestPhaseBDeterministicFixtureFactory {
    private const val FIXTURE_RNG_SEED = -202610010216L

    fun build(
        registry: CardRegistry,
        row: PestPhaseBPlannedRow,
    ): PestPhaseBDeterministicFixture {
        val spec = PestPhaseBPhysicalPlan.spec(row.context.deck)
        require(row.orderedHand.size == 7 && row.orderedHand.toSet().size == 7)
        require(row.orderedHand.all { card ->
            spec.entries.getOrNull(card.entryIndex)?.first == card.name &&
                card.copyOrdinal in 1..spec.entries[card.entryIndex].second
        })

        val roster = listOf(EntityId("pest-phase-b-seat-0"), EntityId("pest-phase-b-seat-1"))
        val owner = roster[row.context.seat]
        val opponent = roster[1 - row.context.seat]
        val active = if (row.context.onPlay) owner else opponent

        var state = GameState(format = Format.Standard, rng = GameRng.seeded(FIXTURE_RNG_SEED))
        roster.forEachIndexed { index, player ->
            state = state.withEntity(player, ComponentContainer.of(
                PlayerComponent("Phase-B-$index"),
                LifeTotalComponent(20),
                ManaPoolComponent(),
                LandDropsComponent(remaining = 1, maxPerTurn = 1),
            ))
        }
        state = state.copy(
            turnOrder = roster,
            activePlayerId = active,
            priorityPlayerId = active,
            phase = Phase.BEGINNING,
            step = Step.UNTAP,
            turnNumber = 1,
        )

        val ids = linkedMapOf<Pair<Int, Int>, EntityId>()
        spec.entries.forEachIndexed { entryIndex, (name, count) ->
            val definition = registry.requireCard(name)
            repeat(count) { zero ->
                val copy = zero + 1
                val id = EntityId("pest-phase-b-${row.context.deck}-$entryIndex-$copy")
                require(id !in ids.values)
                ids[entryIndex to copy] = id
                state = state.withEntity(id, CardEntityFactory.create(definition, owner))
            }
        }
        require(ids.size == 60)

        val hand = row.orderedHand.map { ids.getValue(it.entryIndex to it.copyOrdinal) }
        require(hand.distinct().size == 7)
        val handSet = hand.toSet()
        val library = ids.values.filterNot(handSet::contains)
        require(library.size == 53)

        fun zone(player: EntityId, type: Zone, values: List<EntityId>) {
            state = state.copy(zones = state.zones + (ZoneKey(player, type) to values))
        }
        zone(owner, Zone.HAND, hand)
        zone(owner, Zone.LIBRARY, library)
        zone(owner, Zone.GRAVEYARD, emptyList())
        zone(owner, Zone.BATTLEFIELD, emptyList())
        zone(opponent, Zone.HAND, emptyList())
        zone(opponent, Zone.LIBRARY, emptyList())
        zone(opponent, Zone.GRAVEYARD, emptyList())
        zone(opponent, Zone.BATTLEFIELD, emptyList())

        require(state.stack.isEmpty() && state.pendingDecision == null && state.getBattlefield().isEmpty())
        return PestPhaseBDeterministicFixture(state, roster, hand, library)
    }
}
