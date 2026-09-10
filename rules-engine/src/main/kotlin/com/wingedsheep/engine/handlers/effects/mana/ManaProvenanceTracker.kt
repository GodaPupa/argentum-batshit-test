package com.wingedsheep.engine.handlers.effects.mana

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.ManaPoolComponent
import com.wingedsheep.sdk.model.EntityId

/**
 * Tags mana added to a player's pool with its provenance — which source produced it and what
 * subtypes that source had — so payoffs can later ask "which kind of source produced the mana spent
 * to cast this?".
 *
 * The [ManaPoolComponent.manaBySubtype] / [ManaPoolComponent.manaBySource] counters record, per
 * subtype and per producing source, how many mana units in the pool came from there. When mana is
 * spent for a spell, [com.wingedsheep.engine.handlers.actions.spell.CastPaymentProcessor] consumes
 * from those counters proportional to the unrestricted mana taken from the pool and records what was
 * consumed on the spell (`SpentManaProvenance`).
 *
 * Generalizes the old Treasure-only counter (Treasure is now just `manaBySubtype[Subtype.TREASURE]`)
 * and powers Alchemist's Talent level 3 ("if mana from a Treasure was spent"), Bat Colony ("a Bat
 * for each mana from a Cave spent to cast it"), and the LCI mana-source lands (Tecutlan / Barracks /
 * Myriad Pools — "cast … using mana produced by this land"). The set of mana-producing executors
 * that call into here is: [AddManaExecutor], [AddColorlessManaExecutor], [AddManaOfChoiceExecutor]
 * (both the immediate and the post-color-choice resumer paths).
 */
object ManaProvenanceTracker {

    /**
     * Increment the producing player's provenance counters when [sourceId] produced [amount] mana.
     * Snapshots the source's subtypes from its base [CardComponent.typeLine] — the source may already
     * be in the graveyard (a Treasure's `{T}, Sacrifice this` pays the cost before the mana effect
     * resolves), but the entity persists with its base type line intact. A source with no card
     * component (or a null [sourceId]) still tags the source id if present, contributing no subtypes.
     */
    fun tagAddedMana(state: GameState, playerId: EntityId, sourceId: EntityId?, amount: Int): GameState {
        if (amount <= 0 || sourceId == null) return state
        val subtypes = state.getEntity(sourceId)?.get<CardComponent>()?.typeLine?.subtypes?.toSet() ?: emptySet()
        return state.updateEntity(playerId) { container ->
            val pool = container.get<ManaPoolComponent>() ?: ManaPoolComponent()
            container.with(pool.withProvenance(sourceId, subtypes, amount))
        }
    }
}
