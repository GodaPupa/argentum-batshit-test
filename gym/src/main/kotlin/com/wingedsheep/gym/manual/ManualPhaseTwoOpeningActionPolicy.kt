package com.wingedsheep.gym.manual

import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.sdk.core.Zone

/** Prospective first executable action seam. Each complete gear and opponent pilot remains open. */
internal enum class ManualPhaseTwoPilotRole { CRUISE, SPORT, RACE, OPPONENT }

/**
 * Chooses a uniquely legal physical opening land from the masked seat's own hand. The policy
 * refuses strategic forks it has not qualified, including an opponent stack, pending decision,
 * multiple distinct land offers, mulligan, and bottoming. It never receives the raw GameState.
 */
internal class ManualPhaseTwoOpeningActionPolicy(
    private val role: ManualPhaseTwoPilotRole,
) : PhaseTwoPilotPolicy {
    override fun choose(observation: TrainingObservation, prompt: PhaseTwoPilotPrompt): PhaseTwoPilotChoice {
        require(observation.players.size == 4 &&
            observation.agentToAct == observation.perspectivePlayerId) {
            "Four-seat actor observation required for $role"
        }
        require(prompt == PhaseTwoPilotPrompt.Engine &&
            observation.pendingDecision == null && observation.stack.isEmpty()) {
            "Unqualified $role decision window"
        }
        val actor = observation.perspectivePlayerId
        val hand = observation.zones.single { it.ownerId == actor && it.zoneType == Zone.HAND }
        require(!hand.hidden && hand.cards.size == hand.size &&
            hand.cards.map { it.entityId }.distinct().size == hand.size) {
            "Physical own hand is incomplete for $role"
        }
        val ownLands = hand.cards.filter { "LAND" in it.types }.map { it.entityId }.toSet()
        // PlayLand is a known non-target engine action. Its generic LegalAction targetCount/minTargets
        // fields currently retain the shared constructor default, so physical land identity—not those
        // generic cardinality fields—is the reviewed public boundary for this seam.
        val landActions = observation.legalActions.filter {
            it.kind == "PlayLand" && it.affordable && it.sourceEntityId in ownLands
        }
        if (observation.phase.isMainPhase && observation.activePlayerId == actor &&
            landActions.size == 1) {
            return PhaseTwoPilotChoice.Action(landActions.single().actionId)
        }
        require(observation.legalActions.none { it.affordable && it.kind != "PassPriority" }) {
            "Unqualified $role strategic action fork"
        }
        return PhaseTwoPilotChoice.Action(
            observation.legalActions.single { it.kind == "PassPriority" }.actionId)
    }
}
