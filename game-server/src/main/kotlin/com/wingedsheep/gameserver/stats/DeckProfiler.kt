package com.wingedsheep.gameserver.stats

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.core.Color
import org.springframework.stereotype.Component

/** A deck's color identity + sets, derived for stats from its card list. */
data class DeckProfile(
    /** Color identity in canonical WUBRG order (e.g. "WU"); empty string = colorless. */
    val colors: String,
    /** Distinct set codes the deck draws from, comma-separated (e.g. "DSK,BLB"). */
    val setCodes: String,
)

/**
 * Derives the color/set profile of a deck for match-history stats. Pure lookups against the
 * [CardRegistry] — no game state — so it is cheap to call once per seat at game-over.
 */
@Component
class DeckProfiler(private val cardRegistry: CardRegistry) {

    /**
     * Profile [deck] (a list of card names, possibly "name#collector"). [fallbackSetCode] is used as
     * the deck's set when no card resolves to a printing (e.g. quick games whose generated deck list
     * carries no per-card set).
     */
    fun profile(deck: List<String>, fallbackSetCode: String? = null): DeckProfile {
        val colors = sortedSetOf<Color>(compareBy { WUBRG.indexOf(it) })
        val sets = linkedSetOf<String>()
        for (name in deck) {
            // Deck lists may carry a "name#collector" printing pin; fall back to the canonical name.
            val card = cardRegistry.getCard(name) ?: cardRegistry.getCard(name.substringBefore('#')) ?: continue
            colors.addAll(card.colorIdentity)
            card.setCode?.let { sets.add(it) }
        }
        if (sets.isEmpty()) fallbackSetCode?.let { sets.add(it) }
        return DeckProfile(
            colors = colors.joinToString("") { it.symbol.toString() },
            setCodes = sets.joinToString(","),
        )
    }

    private companion object {
        val WUBRG = listOf(Color.WHITE, Color.BLUE, Color.BLACK, Color.RED, Color.GREEN)
    }
}
