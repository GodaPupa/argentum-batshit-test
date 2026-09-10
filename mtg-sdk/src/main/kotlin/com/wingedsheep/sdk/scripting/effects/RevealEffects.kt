package com.wingedsheep.sdk.scripting.effects

import com.wingedsheep.sdk.scripting.GameObjectFilter
import com.wingedsheep.sdk.scripting.text.TextReplacer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =============================================================================
// Reveal Effects
// =============================================================================

/**
 * Atomic "you may reveal a card matching [filter] from your hand" choice.
 *
 * Encapsulates the full UX:
 *  - If the controller has no card in hand matching [filter], the choice is impossible
 *    and the executor falls through to [otherwise] without prompting.
 *  - Otherwise the controller is prompted; if they decline, [otherwise] runs.
 *  - If they confirm and pick a card, a `CardsRevealedEvent` is emitted and
 *    nothing else happens (the reveal itself is the entire payoff).
 *
 * Reusable for any "may reveal a [filter] from your hand" pattern — SOI shadowlands
 * (Game Trail, Choked Estuary, …), Cavern of Souls' creature-type reveal, etc. Compose
 * with other atoms (`Effects.Tap`, `Effects.Sacrifice`, …) via [otherwise] to express
 * "if you don't, X" rider clauses.
 */
@SerialName("MayRevealCardFromHand")
@Serializable
data class MayRevealCardFromHandEffect(
    val filter: GameObjectFilter,
    val otherwise: Effect? = null,
) : Effect {
    override val description: String = buildString {
        append("You may reveal a ${filter.description} card from your hand")
        if (otherwise != null) {
            append("; if you don't, ${otherwise.description.lowercase()}")
        }
    }

    override fun applyTextReplacement(replacer: TextReplacer): Effect {
        val newFilter = filter.applyTextReplacement(replacer)
        val newOtherwise = otherwise?.applyTextReplacement(replacer)
        return if (newFilter !== filter || newOtherwise !== otherwise)
            copy(filter = newFilter, otherwise = newOtherwise)
        else this
    }
}
