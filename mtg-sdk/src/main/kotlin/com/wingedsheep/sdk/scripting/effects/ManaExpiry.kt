package com.wingedsheep.sdk.scripting.effects

import kotlinx.serialization.Serializable
import com.wingedsheep.sdk.dsl.firebending

/**
 * When mana produced by an effect leaves its owner's pool.
 *
 * This is the *duration* axis of mana, orthogonal to [ManaRestriction] (which controls
 * *where* the mana may be spent) and [ManaSpellRider] (which controls *what happens to a
 * spell* the mana is spent on).
 *
 * The engine empties mana pools at end of turn (cleanup), so [END_OF_TURN] is the default
 * and describes every ordinary mana source. [END_OF_COMBAT] is for mana that must be gone
 * once the combat phase ends — firebending (Avatar: The Last Airbender, CR 702.189):
 * "Until end of combat, you don't lose this mana as steps and phases end. Any of this mana
 * you still have as combat ends will be lost." Combat-duration mana is held as an
 * [ManaRestriction.AnySpend] restricted entry (so it flows through the normal spend logic)
 * tagged with this expiry, and cleared by `CombatManager.endCombat`.
 */
@Serializable
enum class ManaExpiry {
    /** Mana persists until the normal end-of-turn pool emptying (the default for all mana). */
    END_OF_TURN,

    /** Mana is discarded when the combat phase ends (firebending). */
    END_OF_COMBAT,
}
