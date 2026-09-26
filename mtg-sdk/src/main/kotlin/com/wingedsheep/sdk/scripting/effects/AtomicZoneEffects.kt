package com.wingedsheep.sdk.scripting.effects

import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.scripting.GameObjectFilter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Atomically moves the resolving source card plus exactly [additionalCount] matching cards from
 * [additionalSourceZone] to [destination]. If every required object cannot be moved, nothing moves.
 * This effect is intentionally card-agnostic.
 */
@SerialName("MoveSourceAndExactCards")
@Serializable
data class MoveSourceAndExactCardsEffect(
    val sourceRequiredZone: Zone,
    val additionalSourceZone: Zone,
    val additionalFilter: GameObjectFilter,
    val additionalCount: Int,
    val destination: Zone = Zone.EXILE,
    /**
     * Optional pipeline collection published only after the complete transaction commits.
     * A surrounding Gate.DoAction can score this with SuccessCriterion.CollectionNonEmpty
     * without inferring success from partial zone changes.
     */
    val storeMovedAs: String? = null,
) : Effect {
    init { require(additionalCount > 0) { "additionalCount must be positive" } }
    override val description: String =
        "move this card and exactly $additionalCount ${additionalFilter.description} cards from " +
            additionalSourceZone.displayName + " to " + destination.displayName
}
