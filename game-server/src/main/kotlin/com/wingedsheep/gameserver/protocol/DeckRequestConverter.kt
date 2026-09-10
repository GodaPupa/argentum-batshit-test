package com.wingedsheep.gameserver.protocol

import com.wingedsheep.sdk.model.CardEntry
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.PrintingRef

/**
 * Builds an SDK [Deck] from the wire format. Two paths:
 *
 * 1. Rich entries (`cardEntries: List<DeckEntryDTO>`). When non-null/empty this is
 *    authoritative; the legacy [deckList] map is ignored. Per-card [PrintingRef]s pin
 *    specific printings; the engine resolves them at game-init.
 *
 * 2. Legacy flat map (`deckList: Map<String, Int>`). Names + counts only; no per-card
 *    printing pinning. Each `(name, count)` pair fans out to `count` library entries.
 *
 * For commander-shape formats, [commander] names the commander card and
 * [commanderPrinting] optionally pins its printing. The commander does not appear in the
 * library entries — engine `GameInitializer` puts it in the command zone separately.
 */
object DeckRequestConverter {

    fun toDeck(
        deckList: Map<String, Int>,
        cardEntries: List<DeckEntryDTO>? = null,
        commander: String? = null,
        commanderPrinting: PrintingRef? = null,
        sideboard: Map<String, Int> = emptyMap(),
    ): Deck {
        // Sideboard cards ("outside the game", CR 100.4) are name-only here — wish fetches don't
        // depend on printing, and the engine seeds the SIDEBOARD zone from these names.
        val sideboardEntries = sideboard.flatMap { (name, count) -> List(count) { CardEntry(name) } }
        val rich = cardEntries.takeUnless { it.isNullOrEmpty() }
        return if (rich != null) {
            Deck.fromEntries(
                entries = rich.map { CardEntry(it.name, it.printing) },
                commander = commander,
                commanderPrinting = commanderPrinting,
                sideboard = sideboardEntries,
            )
        } else {
            val cards = deckList.flatMap { (name, count) -> List(count) { name } }
            Deck(
                cards = cards,
                commander = commander,
                commanderPrinting = commanderPrinting,
                sideboard = sideboardEntries,
            )
        }
    }
}
