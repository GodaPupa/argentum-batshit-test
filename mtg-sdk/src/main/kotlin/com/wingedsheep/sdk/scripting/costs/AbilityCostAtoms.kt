package com.wingedsheep.sdk.scripting.costs

import com.wingedsheep.sdk.core.ManaCost
import com.wingedsheep.sdk.scripting.AbilityCost

/**
 * Read-side helpers for the atom-backed [AbilityCost.Atom]. Since the shared payable things
 * (mana, life, sacrifice, tap, return, discard, exile) now live on [CostAtom] and are carried
 * by [AbilityCost.Atom], engine consumers that used to match `is AbilityCost.Mana` (etc.) read
 * the wrapped atom through these accessors instead of unwrapping by hand at every call site.
 */

/** The wrapped [CostAtom] when this ability cost is atom-backed, else null. */
val AbilityCost.atomOrNull: CostAtom?
    get() = (this as? AbilityCost.Atom)?.atom

/** The mana cost when this ability cost is an atom-backed [CostAtom.Mana], else null. */
val AbilityCost.manaCostOrNull: ManaCost?
    get() = (atomOrNull as? CostAtom.Mana)?.cost
