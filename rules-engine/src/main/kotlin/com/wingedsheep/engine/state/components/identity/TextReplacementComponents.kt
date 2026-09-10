package com.wingedsheep.engine.state.components.identity

import com.wingedsheep.engine.state.Component
import com.wingedsheep.sdk.core.Color
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.scripting.Duration
import com.wingedsheep.sdk.scripting.text.TextReplacer
import kotlinx.serialization.Serializable

/**
 * Category of text replacement for Layer 3 text-changing effects.
 */
@Serializable
enum class TextReplacementCategory {
    CREATURE_TYPE,
    COLOR_WORD,
    BASIC_LAND_TYPE
}

/**
 * A single text replacement rule (e.g., "Elf" -> "Goblin", "Forest" -> "Island", "Red" -> "Blue").
 *
 * [duration] controls when the rule expires. Artificial Evolution-style creature-type
 * changes are [Duration.Permanent] ("this effect lasts indefinitely"); Crystal Spray's
 * color/land-type changes are [Duration.EndOfTurn] and are stripped during cleanup.
 */
@Serializable
data class TextReplacement(
    val fromWord: String,
    val toWord: String,
    val category: TextReplacementCategory,
    val duration: Duration = Duration.Permanent
)

/**
 * Stores text replacement rules for Layer 3 text-changing effects.
 *
 * Used by cards like Artificial Evolution ("replacing all instances of one creature type
 * with another") and Crystal Spray ("one color word with another or one basic land type
 * with another").
 *
 * Multiple replacements can stack (e.g., two Artificial Evolutions on the same permanent,
 * or a creature-type change plus a color-word change).
 */
@Serializable
data class TextReplacementComponent(
    val replacements: List<TextReplacement> = emptyList()
) : Component, TextReplacer {

    /**
     * Apply any subtype replacements (creature types AND basic land types — both are
     * subtypes) to a single subtype string. Chains across multiple matching rules.
     */
    fun applyToCreatureType(subtype: String): String {
        var result = subtype
        for (r in replacements) {
            if ((r.category == TextReplacementCategory.CREATURE_TYPE ||
                    r.category == TextReplacementCategory.BASIC_LAND_TYPE) &&
                result.equals(r.fromWord, ignoreCase = true)) {
                result = r.toWord
            }
        }
        return result
    }

    override fun replaceCreatureType(subtype: String): String = applyToCreatureType(subtype)

    fun applyToSubtype(subtype: Subtype): Subtype {
        val replaced = applyToCreatureType(subtype.value)
        return if (replaced == subtype.value) subtype else Subtype(replaced)
    }

    override fun replaceSubtype(subtype: Subtype): Subtype = applyToSubtype(subtype)

    /**
     * Apply any color-word replacements to a [Color]. Color words are matched
     * case-insensitively against the color's display name ("Red", "Blue", ...).
     * Returns the input color unchanged when no rule applies.
     */
    override fun replaceColor(color: Color): Color {
        var result = color
        for (r in replacements) {
            if (r.category == TextReplacementCategory.COLOR_WORD &&
                result.displayName.equals(r.fromWord, ignoreCase = true)) {
                result = Color.entries.firstOrNull { it.displayName.equals(r.toWord, ignoreCase = true) }
                    ?: result
            }
        }
        return result
    }

    fun withReplacement(replacement: TextReplacement): TextReplacementComponent =
        copy(replacements = replacements + replacement)
}
