package com.wingedsheep.sdk.core

import kotlinx.serialization.Serializable

/**
 * Constructed deck-construction formats whose legality data is sourced from Scryfall.
 *
 * A card's [com.wingedsheep.sdk.model.CardDefinition.legalFormats] is the set of formats in
 * which the card is currently legal (i.e. not banned and printed in a legal set). Format-
 * specific deck-construction rules beyond per-card legality (Commander singleton/100, Vintage
 * restricted-list, etc.) are enforced separately by the deck validator.
 */
@Serializable
enum class DeckFormat {
    STANDARD,
    PIONEER,
    MODERN,
    LEGACY,
    VINTAGE,
    COMMANDER,
    BRAWL,            // 100-card singleton (Arena/Historic Brawl). Scryfall key: "brawl".
    STANDARD_BRAWL,   // 60-card singleton, Standard pool.            Scryfall key: "standardbrawl".
    PAUPER,
    PREMODERN;

    /**
     * Lower-case identifier matching Scryfall's `legalities.<key>` field. Most enum names map
     * directly (`STANDARD` → `"standard"`); `STANDARD_BRAWL` is special-cased because Scryfall
     * concatenates words.
     */
    val scryfallKey: String get() = when (this) {
        STANDARD_BRAWL -> "standardbrawl"
        else -> name.lowercase()
    }

    /** Human-readable label for UI surfaces. */
    val displayName: String get() = when (this) {
        STANDARD_BRAWL -> "Standard Brawl"
        else -> name.lowercase().replaceFirstChar { it.uppercase() }
    }

    /**
     * True for singleton formats with a designated commander (Commander, Brawl, Standard Brawl).
     * These formats add structural rules beyond the constructed baseline: a commander card lives
     * in the command zone, every other card's color identity must fit within the commander's,
     * and (for partner-less decks) the commander itself must be a legal commander.
     */
    val isCommanderShape: Boolean get() = when (this) {
        COMMANDER, BRAWL, STANDARD_BRAWL -> true
        else -> false
    }

    companion object {
        fun fromScryfallKey(key: String): DeckFormat? =
            entries.firstOrNull { it.scryfallKey == key.lowercase() }
    }
}
