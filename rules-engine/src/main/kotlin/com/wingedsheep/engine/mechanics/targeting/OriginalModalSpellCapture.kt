package com.wingedsheep.engine.mechanics.targeting

import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.stack.SpellOnStackComponent
import com.wingedsheep.sdk.model.EntityId
import kotlinx.serialization.Serializable

/** Original engine-collected provenance, never accepted as part of a public CastSpell action. */
@Serializable
data class OriginalModalSpellCapture(
    val chosenModeIndices: List<Int>,
    val plans: List<TargetSlotPlan>,
)

/** Attach only complete original provenance after successful stack placement; never recapture visits. */
internal fun attachOriginalModalSpellCapture(
    state: GameState, spellId: EntityId, capture: OriginalModalSpellCapture?
): GameState {
    if (capture == null || capture.plans.size != capture.chosenModeIndices.size || spellId !in state.stack) return state
    val spell = state.getEntity(spellId)?.get<SpellOnStackComponent>() ?: return state
    return state.updateEntity(spellId) {
        val captured = it.with(spell.copy(originalModalCapture = capture))
        if (spell.splicedCardNames.isEmpty() && spell.chosenModes == capture.chosenModeIndices) {
            val plan = CurrentTargetPlan(TargetSlotPlan(capture.plans.flatMap { plan -> plan.instances }),
                modeInstanceCounts = capture.plans.map { plan -> plan.instances.size })
            captured.withCurrentTargetPlan(null, plan) ?: captured
        } else captured
    }
}
