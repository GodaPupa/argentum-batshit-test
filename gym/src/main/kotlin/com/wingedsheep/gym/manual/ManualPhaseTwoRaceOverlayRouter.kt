package com.wingedsheep.gym.manual

import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.sdk.model.EntityId

/**
 * Exact composition router for the seven already-accepted Race overlays.
 *
 * This source chooses no action and changes no hardware. It binds only a public commander axis
 * already recognized by ManualPhaseTwoPublicOpponentAxes to the exact accepted overlay identity.
 * Cruise/Sport may not inherit Race-only overlays through this router.
 */
internal object ManualPhaseTwoRaceOverlayRouter {
    private val overlays = mapOf(
        ManualOpponentAxis.HASHATON to "R3-HT",
        ManualOpponentAxis.MAGDA to "R3-M",
        ManualOpponentAxis.BLUE_FARM to "R3-BF",
        ManualOpponentAxis.SHORIKAI to "R3-SH",
        ManualOpponentAxis.SISAY to "R3-SY",
        ManualOpponentAxis.ROGSI to "R3-RS",
        ManualOpponentAxis.KINNAN to "R3-KB",
    )

    fun bind(
        role: ManualPhaseTwoPilotRole,
        observation: TrainingObservation,
    ): Map<EntityId, String> {
        require(role == ManualPhaseTwoPilotRole.RACE) {
            "Accepted Race opponent overlays are not inherited by $role"
        }
        val axes = ManualPhaseTwoPublicOpponentAxes.detect(observation)
        return axes.mapValues { (_, axis) ->
            requireNotNull(overlays[axis]) { "No accepted Race overlay for $axis" }
        }
    }
}
