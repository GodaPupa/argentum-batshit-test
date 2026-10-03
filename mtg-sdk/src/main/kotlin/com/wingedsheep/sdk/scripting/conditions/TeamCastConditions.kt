package com.wingedsheep.sdk.scripting.conditions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * True once you or any teammate has cast a spell earlier this turn.
 *
 * This is the shared eligibility condition for surge (CR 702.117). It intentionally reads cast
 * history rather than the current stack, so the qualifying spell still counts after resolving or
 * being countered. The engine's GameState.teamOf() makes a non-team player a singleton team.
 */
@SerialName("YouOrTeammateCastSpellThisTurn")
@Serializable
data object YouOrTeammateCastSpellThisTurn : Condition {
    override val description: String = "if you or a teammate cast another spell this turn"
}
