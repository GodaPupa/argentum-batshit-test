package com.wingedsheep.engine.handlers.costs

import com.wingedsheep.engine.mechanics.BipartiteMatching
import com.wingedsheep.sdk.model.EntityId
import com.wingedsheep.sdk.scripting.GameObjectFilter

/**
 * Bipartite perfect-matching for heterogeneous (per-slot) Craft materials — CR 702.167.
 *
 * A slot-based craft names one material filter per slot ("Craft with a Dinosaur, a Merfolk, a
 * Pirate, and a Vampire") and each slot must be filled by a **distinct** material. Because a
 * single card can satisfy several slot filters (a Merfolk Pirate matches both the Merfolk and
 * Pirate slots), a plain per-slot count check is wrong: it would let one Merfolk Pirate satisfy
 * two slots at once, or accept four Vampires for the four subtypes. Correctly deciding whether a
 * set of materials can cover every slot is exactly a maximum bipartite matching (slots ↔ distinct
 * materials); every slot must be saturated.
 *
 * The matching itself is the shared [BipartiteMatching] routine (same code as
 * [com.wingedsheep.engine.mechanics.combat.BlockPhaseManager]'s must-be-blocked matching); this
 * object just adapts the craft domain (slots as the left side, materials as the right) onto it. It
 * is a pure function of the `(material, slotFilter) -> Boolean` edge predicate, so both the cost
 * handler (payment / `canPay`, matching against projected state) and the legal-action enumerator
 * (offering the ability) can share one definition of "these materials can satisfy these slots".
 */
object CraftSlotMatching {

    /**
     * True iff every slot in [slots] can be assigned its own distinct material from [materials]
     * under [matchesSlot] — i.e. there is a matching saturating all slots. When
     * `materials.size == slots.size` this is a perfect matching (each material used once).
     *
     * @param matchesSlot edge predicate: may this material fill this slot's filter?
     */
    fun canSatisfyAllSlots(
        slots: List<GameObjectFilter>,
        materials: List<EntityId>,
        matchesSlot: (EntityId, GameObjectFilter) -> Boolean
    ): Boolean = BipartiteMatching.canSaturateLeft(slots, materials) { slot, material ->
        matchesSlot(material, slot)
    }
}
