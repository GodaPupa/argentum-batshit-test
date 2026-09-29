package com.wingedsheep.gym.manual

import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.sdk.core.Zone

/**
 * Narrow same-hardware commander deployment seam for the three Manual gears.
 *
 * It acts only when Animar is publicly identifiable in the actor command zone and its cast is the
 * sole affordable non-pass action. Any competing land, spell, ability, target or other strategic
 * fork remains unqualified.
 */
internal class ManualPhaseTwoUniqueCommanderCastPolicy(
    private val role: ManualPhaseTwoPilotRole,
) : PhaseTwoPilotPolicy {
    override fun choose(observation: TrainingObservation, prompt: PhaseTwoPilotPrompt): PhaseTwoPilotChoice {
        require(role in setOf(
            ManualPhaseTwoPilotRole.CRUISE,
            ManualPhaseTwoPilotRole.SPORT,
            ManualPhaseTwoPilotRole.RACE,
        )) { "This exact Manual commander seam is not an opponent policy" }
        val actor = observation.perspectivePlayerId
        require(observation.players.size == 4 &&
            observation.agentToAct == actor &&
            observation.priorityPlayerId == actor &&
            observation.activePlayerId == actor &&
            observation.phase.isMainPhase &&
            observation.pendingDecision == null &&
            observation.stack.isEmpty() &&
            !observation.terminated &&
            prompt == PhaseTwoPilotPrompt.Engine) {
            "Unqualified $role commander window"
        }

        val command = observation.zones.single {
            it.ownerId == actor && it.zoneType == Zone.COMMAND
        }
        require(!command.hidden && command.cards.size == command.size) {
            "Own command identity is incomplete"
        }
        val animar = command.cards.singleOrNull { it.name == "Animar, Soul of Elements" }
            ?: throw IllegalArgumentException("Exact Animar command identity is absent")

        val strategic = observation.legalActions.filter {
            it.affordable && it.kind != "PassPriority" && !it.isManaAbility
        }
        require(strategic.size == 1) { "Competing strategic action fork" }
        val cast = strategic.single()
        require(cast.kind == "CastSpell" && cast.sourceEntityId == animar.entityId) {
            "Sole action is not the physical Animar command-zone cast"
        }
        return PhaseTwoPilotChoice.Action(cast.actionId)
    }
}
