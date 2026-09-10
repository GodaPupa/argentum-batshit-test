package com.wingedsheep.sdk.scripting.conditions

import com.wingedsheep.sdk.scripting.conditions.Condition
import com.wingedsheep.sdk.scripting.text.TextReplacer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// =============================================================================
// Composite Conditions
// =============================================================================

/**
 * Condition: All of the sub-conditions must be met (AND)
 */
@SerialName("All")
@Serializable
data class AllConditions(val conditions: List<Condition>) : Condition {
    override val description: String = conditions.joinToString(" and ") { it.description }
    override fun applyTextReplacement(replacer: TextReplacer): Condition {
        val newConditions = conditions.map { it.applyTextReplacement(replacer) }
        return if (newConditions.zip(conditions).all { (new, old) -> new === old }) this else copy(conditions = newConditions)
    }
}

/**
 * Condition: Any of the sub-conditions must be met (OR)
 */
@SerialName("Any")
@Serializable
data class AnyCondition(val conditions: List<Condition>) : Condition {
    override val description: String = conditions.joinToString(" or ") { it.description }
    override fun applyTextReplacement(replacer: TextReplacer): Condition {
        val newConditions = conditions.map { it.applyTextReplacement(replacer) }
        return if (newConditions.zip(conditions).all { (new, old) -> new === old }) this else copy(conditions = newConditions)
    }
}

/**
 * Condition: The sub-condition must NOT be met
 */
@SerialName("Not")
@Serializable
data class NotCondition(val condition: Condition) : Condition {
    override val description: String = "if not (${condition.description})"
    override fun applyTextReplacement(replacer: TextReplacer): Condition {
        val newCondition = condition.applyTextReplacement(replacer)
        return if (newCondition === condition) this else copy(condition = newCondition)
    }
}
