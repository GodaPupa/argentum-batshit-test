package com.wingedsheep.gameserver.protocol

import com.wingedsheep.sdk.model.PrintingRef
import kotlinx.serialization.Serializable

/**
 * Wire-format entry for a single deck slot, optionally pinned to a specific printing.
 *
 * Decks travel between client and server as either a flat `deckList: Map<String, Int>`
 * (legacy, name + count) or as an ordered `cardEntries: List<DeckEntryDTO>` (rich,
 * one row per card, optional [PrintingRef]). When both are present, [DeckEntryDTO] is
 * authoritative and the flat map is ignored. When [printing] is null the engine resolves
 * the card's default printing as before.
 */
@Serializable
data class DeckEntryDTO(
    val name: String,
    val printing: PrintingRef? = null,
)
