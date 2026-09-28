package com.wingedsheep.gym.manual

import com.wingedsheep.gym.contract.ActionParams
import com.wingedsheep.gym.contract.StackItemKind
import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.sdk.core.Zone

/**
 * Prospective R3-P helper-first action seam for SPORT/RACE on the existing masked boundary.
 * It waits behind an actual public helper counter, then answers actual public protection.
 * This is not a complete gear or opponent policy; all other windows fail closed.
 */
internal class ManualPhaseTwoPriorityConservationPolicy(
    private val role: ManualPhaseTwoPilotRole,
) : PhaseTwoPilotPolicy {
    override fun choose(observation: TrainingObservation, prompt: PhaseTwoPilotPrompt): PhaseTwoPilotChoice {
        require(role in setOf(ManualPhaseTwoPilotRole.SPORT, ManualPhaseTwoPilotRole.RACE)) {
            "R3-P is not inherited by Cruise or an unspecified opponent"
        }
        val actor = observation.perspectivePlayerId
        require(observation.players.size == 4 && observation.players.map { it.id }.distinct().size == 4 &&
            observation.agentToAct == actor && observation.priorityPlayerId == actor &&
            !observation.terminated && prompt == PhaseTwoPilotPrompt.Engine &&
            observation.pendingDecision == null)
        val hand = observation.zones.single { it.ownerId == actor && it.zoneType == Zone.HAND }
        require(!hand.hidden && hand.cards.size == hand.size &&
            hand.cards.map { it.entityId }.distinct().size == hand.size)
        require(observation.stack.size in 2..3)
        val threat = observation.stack[0]
        val helper = observation.stack[1]
        require(threat.kind == StackItemKind.SPELL &&
            threat.name in setOf("Kinnan, Bonder Prodigy", "Enduring Vitality", "Cryptolith Rite") &&
            threat.controllerId != null && threat.controllerId != actor)
        require(helper.kind == StackItemKind.SPELL &&
            helper.name in setOf("Counterspell", "Pact of Negation", "Force of Will") &&
            helper.controllerId != null && helper.controllerId != actor &&
            helper.controllerId != threat.controllerId && helper.targets == listOf(threat.entityId))
        val pass = observation.legalActions.single { it.kind == "PassPriority" && it.affordable }
        if (observation.stack.size == 2) return PhaseTwoPilotChoice.Action(pass.actionId)
        val protection = observation.stack[2]
        require(protection.kind == StackItemKind.SPELL &&
            protection.name in setOf("Fierce Guardianship", "Pact of Negation") &&
            protection.controllerId == threat.controllerId &&
            protection.targets == listOf(helper.entityId))
        // This seam has no qualified ability/Coatl expenditure comparison yet.
        require(observation.zones.filter { it.ownerId == actor &&
            it.zoneType in setOf(Zone.HAND, Zone.BATTLEFIELD) }.flatMap { it.cards }.none {
                it.name in setOf("Hope-Ender Coatl", "Malevolent Hermit", "Glen Elendra Archmage")
            })
        val options = observation.legalActions.mapNotNull { action ->
            val card = hand.cards.singleOrNull { it.entityId == action.sourceEntityId }
            if (action.kind == "CastSpell" && action.affordable && !action.hasXCost &&
                action.minTargets == 1 && action.maxTargets == 1 &&
                protection.entityId in action.targetEntityIds &&
                card?.name in setOf("An Offer You Can't Refuse", "Pact of Negation")) {
                (if (card!!.name == "Pact of Negation") 1 else 0) to action
            } else null
        }
        require(options.isNotEmpty()) { "No qualified public protection answer" }
        val debt = options.minOf { it.first }
        val action = options.filter { it.first == debt }.single().second
        return PhaseTwoPilotChoice.Action(action.actionId,
            ActionParams(targets = listOf(protection.entityId)))
    }
}
