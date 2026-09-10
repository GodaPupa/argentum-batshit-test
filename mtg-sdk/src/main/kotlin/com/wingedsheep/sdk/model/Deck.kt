package com.wingedsheep.sdk.model

import kotlinx.serialization.Serializable

/**
 * One card in a deck, optionally pinned to a specific [Printing].
 *
 * When [printing] is null, the engine uses whatever the registry resolves the name to (the
 * default printing, or whatever the card author registered). When [printing] is non-null
 * the engine looks the printing up in `PrintingRegistry` and overrides the per-entity art
 * URLs at game-init.
 */
@Serializable
data class CardEntry(
    val name: String,
    val printing: PrintingRef? = null,
)

/**
 * Represents a deck of Magic cards for game initialization.
 *
 * A deck is a list of card definition IDs (typically card names) that will be
 * instantiated into entity IDs when the game starts.
 *
 * ## Usage
 * ```kotlin
 * val deck = Deck(
 *     cards = listOf("Mountain", "Mountain", "Lightning Bolt", "Goblin Guide", ...)
 * )
 * ```
 *
 * The engine uses the [CardDefinition.name] to look up card definitions from
 * registered card sets.
 *
 * ## Multi-printing
 * The legacy [cards] field carries names only; pass [cardEntries] instead (or alongside) to
 * pin specific printings. When [cardEntries] is non-empty it is authoritative; the engine
 * derives names from it. When only [cards] is supplied (the historical case) the deck
 * behaves exactly as before. Helpers like [countOf] and [uniqueCards] always operate on
 * names so deck-construction rules (4-of, singleton, banlists) collapse identically across
 * different printings of the same card.
 */
@Serializable
data class Deck(
    /**
     * Card names in the main deck (the library at game start).
     * Duplicates are allowed (e.g., 4x Lightning Bolt). Does NOT include the commander —
     * for Commander/Brawl decks the commander begins in the command zone (CR 903.6a),
     * not in the library.
     */
    val cards: List<String>,
    /**
     * Optional commander card name. Set for Commander/Brawl/Standard Brawl decks; null
     * otherwise. The commander is stored separately because it begins the game in the
     * command zone, not the library.
     */
    val commander: String? = null,
    /**
     * Per-card entries with optional printing pinning. When non-empty, this is the
     * authoritative ordered library and [cards] is derived from it. When empty, the deck
     * is name-only and behaves exactly like a pre-printing deck.
     */
    val cardEntries: List<CardEntry> = emptyList(),
    /**
     * Optional commander printing reference. Honoured only when [commander] is non-null.
     */
    val commanderPrinting: PrintingRef? = null,
    /**
     * The player's sideboard — cards they own *outside the game* (CR 100.4). These begin the
     * game in [com.wingedsheep.sdk.core.Zone.SIDEBOARD] and are reachable only by "wish" effects
     * (Burning Wish, Living Wish, …). Empty for the vast majority of decks, so it does not count
     * toward [size] or [isEmpty] (those describe the in-game deck).
     *
     * Populated differently per format, but the engine consumes the same flat list either way:
     * constructed decks carry an explicit, player-curated sideboard (≤15, CR 100.4a); Limited
     * decks derive it as `pool − maindeck` at deck submission (CR 100.4b) — there is no separate
     * Limited sideboard editor, every opened/drafted card not in the deck is automatically here.
     */
    val sideboard: List<CardEntry> = emptyList(),
) {
    /**
     * Total number of cards in the deck (library + command zone). Excludes the sideboard, which
     * is outside the game.
     */
    val size: Int get() = cards.size + (if (commander != null) 1 else 0)

    /**
     * Check if the deck is empty.
     */
    val isEmpty: Boolean get() = cards.isEmpty() && commander == null

    /**
     * Count occurrences of a specific card in the main deck (library). Always operates on
     * names so two different printings of Lightning Bolt count as 2 toward the 4-of rule.
     */
    fun countOf(cardName: String): Int = cards.count { it == cardName }

    /**
     * Get unique card names in the deck (main deck + commander, if any).
     */
    fun uniqueCards(): Set<String> = cards.toSet() + listOfNotNull(commander)

    /**
     * Every entry in the library matching the given card name. Empty if [cardEntries] is
     * empty (the legacy name-only case) — callers that need per-card printing data should
     * gate on [cardEntries].isNotEmpty() first.
     */
    fun entriesOf(cardName: String): List<CardEntry> =
        cardEntries.filter { it.name == cardName }

    /**
     * Every distinct printing pinned by any entry in the deck (library + commander).
     * Useful for warming a printings cache before game-init.
     */
    fun printingsUsed(): Set<PrintingRef> = buildSet {
        cardEntries.forEach { entry -> entry.printing?.let(::add) }
        commanderPrinting?.let(::add)
    }

    companion object {
        /**
         * Create an empty deck.
         */
        val EMPTY = Deck(emptyList())

        /**
         * Create a deck from card name/count pairs.
         */
        fun of(vararg entries: Pair<String, Int>): Deck {
            val cards = entries.flatMap { (name, count) ->
                List(count) { name }
            }
            return Deck(cards)
        }

        /**
         * Create a deck from rich [CardEntry] entries. The legacy [cards] field is filled
         * automatically from the entry names so consumers that read the flat list keep
         * working unchanged.
         */
        fun fromEntries(
            entries: List<CardEntry>,
            commander: String? = null,
            commanderPrinting: PrintingRef? = null,
            sideboard: List<CardEntry> = emptyList(),
        ): Deck = Deck(
            cards = entries.map { it.name },
            commander = commander,
            cardEntries = entries,
            commanderPrinting = commanderPrinting,
            sideboard = sideboard,
        )

        /**
         * Create a simple test deck with basic lands and vanilla creatures.
         */
        fun testDeck(landName: String, landCount: Int, creatures: List<Pair<String, Int>>): Deck {
            val cards = mutableListOf<String>()
            repeat(landCount) { cards.add(landName) }
            creatures.forEach { (name, count) ->
                repeat(count) { cards.add(name) }
            }
            return Deck(cards)
        }
    }
}
