package com.wingedsheep.engine.state.components.identity

import com.wingedsheep.engine.state.Component
import com.wingedsheep.sdk.core.TypeLine
import kotlinx.serialization.Serializable

/**
 * Marks a spell/permanent currently under Bestow's type-changing effect (CR 702.103).
 *
 * While present, the object's CardComponent has been rewritten to "Enchantment — Aura".
 * [originalTypeLine] is restored if the bestow target becomes illegal before resolution,
 * when the Aura becomes unattached on the battlefield, or whenever the object leaves the
 * battlefield. The marker is therefore the exact lifetime of the Bestow effect, not merely
 * a record that the alternative cost was once paid.
 */
@Serializable
data class BestowComponent(
    val originalTypeLine: TypeLine
) : Component {
    companion object {
        /** Bestow removes other card types/subtypes, while preserving supertypes (CR 205.4). */
        fun auraType(original: TypeLine): TypeLine = TypeLine.aura().copy(supertypes = original.supertypes)
    }
}
