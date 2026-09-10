package com.wingedsheep.sdk.model

import kotlinx.serialization.Serializable

/**
 * Lightweight reference to a specific printing of a card.
 *
 * Decks attach a `PrintingRef` to a card entry to pin which printing the player picked
 * (e.g. M10 Lightning Bolt vs. 2X2 Lightning Bolt). The card's oracle identity is implied
 * by the entry's name — `PrintingRef` only carries what's needed to resolve a `Printing`
 * out of `PrintingRegistry`.
 *
 * The pair `(setCode, collectorNumber)` is unique within a set and stable across
 * Scryfall data refreshes.
 */
@Serializable
data class PrintingRef(
    val setCode: String,
    val collectorNumber: String,
) {
    /**
     * Stable string form, suitable for cache keys and logs. Format: `"SET-CN"`
     * (e.g. `"M10-146"`). Not the same shape as the legacy
     * `"Name#SetCode-CollectorNumber"` registry key — that's a `CardRegistry` concern.
     */
    fun identifier(): String = "$setCode-$collectorNumber"
}
