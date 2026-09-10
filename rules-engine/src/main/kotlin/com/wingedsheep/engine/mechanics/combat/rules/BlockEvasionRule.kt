package com.wingedsheep.engine.mechanics.combat.rules

import com.wingedsheep.engine.mechanics.layers.ProjectedState
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.sdk.model.EntityId

/**
 * Context passed to block evasion rules for evaluating whether a blocker can block an attacker.
 */
data class BlockCheckContext(
    val state: GameState,
    val projected: ProjectedState,
    val attackerId: EntityId,
    val blockerId: EntityId,
    val blockingPlayer: EntityId,
    val cardRegistry: CardRegistry
)

/**
 * A combat rule that determines whether a specific blocker can block a specific attacker.
 *
 * Each implementation encapsulates one evasion ability or blocking restriction.
 * Returns an error message if the block is illegal, null if this rule doesn't prevent it.
 *
 * MTG Golden Rule: Restrictions ("can't") beat requirements ("must").
 * The engine iterates all rules; if ANY returns non-null, the block is illegal.
 */
interface BlockEvasionRule {
    fun check(ctx: BlockCheckContext): String?
}
