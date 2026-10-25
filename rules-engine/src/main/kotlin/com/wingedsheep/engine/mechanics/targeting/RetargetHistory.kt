package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.state.components.stack.TargetsComponent
import com.wingedsheep.sdk.model.EntityId

/**
 * Commit an already chosen positional replacement without re-stamping unchanged targets.
 * Equal positions mean "keep the original occurrence", including an unknown historical visit.
 * This adapter does not decide targeting legality or reconstruct targeting-instance boundaries.
 *
 * The legacy component can represent only one battlefield stamp per physical entity. If the
 * requested occurrences need different stamps (including known versus unknown), return null:
 * callers must leave the object unchanged until a lossless occurrence-aware resolver exists.
 * Deliberately selecting a newer visit with the same physical id is not expressible here.
 */
fun TargetsComponent.withRetargetedHistory(
    state: GameState,
    replacements: List<ChosenTarget>,
): TargetsComponent? {
    if (replacements.size != targets.size) return null
    if (replacements == targets) return this
    if (currentTargetPlan != null) return null // Requires atomic current-view transition.
    val freshlyCaptured = TargetsComponent.capture(state, replacements, targetRequirements)
    val occurrences = linkedMapOf<EntityId, Long?>()
    for ((index, replacement) in replacements.withIndex()) {
        if (replacement !is ChosenTarget.Permanent) continue
        val stamp = if (replacement == targets[index]) targetEntryStamps[replacement.entityId]
            else freshlyCaptured.targetEntryStamps[replacement.entityId]
        if (occurrences.containsKey(replacement.entityId) && occurrences[replacement.entityId] != stamp) {
            return null
        }
        occurrences[replacement.entityId] = stamp
    }
    return copy(
        targets = replacements,
        targetEntryStamps = occurrences.mapNotNull { (id, stamp) -> stamp?.let { id to it } }.toMap(),
    )
}
