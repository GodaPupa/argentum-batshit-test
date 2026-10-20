package com.wingedsheep.gym.manual

import com.wingedsheep.gym.contract.TrainingObservation
import com.wingedsheep.sdk.core.Zone

/**
 * R8: the forced-choice part of the four-player London mulligan boundary.
 *
 * This is deliberately NOT an implementation of the accepted R3 Balanced Mulligan
 * policy. No voluntary keep, voluntary mulligan or contested bottom ordering can
 * be inferred from the historical Phase-1 policy label. The only positive branches
 * limited to the engine saying further mulligans are disallowed. Bottoming any
 * positive number of cards remains strategic, including all seven: the order in
 * [BottomCards] determines the physical order at the bottom of the library.
 *
 * Uses only an actor-masked TrainingObservation. No raw GameState, hidden cards,
 * future deck order, raw action IDs, generic AI or arbitrary fallback.
 */
internal class ManualPhaseTwoForcedPregamePolicy(
    private val role: ManualPhaseTwoPilotRole,
) : PhaseTwoPilotPolicy {
    override fun choose(
        observation: TrainingObservation,
        prompt: PhaseTwoPilotPrompt,
    ): PhaseTwoPilotChoice {
        require(role in setOf(
            ManualPhaseTwoPilotRole.CRUISE,
            ManualPhaseTwoPilotRole.SPORT,
            ManualPhaseTwoPilotRole.RACE,
        )) { "Pregame seam does not qualify opponent pilots" }
        require(prompt is PhaseTwoPilotPrompt.Mulligan ||
            prompt is PhaseTwoPilotPrompt.Bottom) { "Pregame seam received ordinary engine prompt" }

        val actor = observation.perspectivePlayerId
        require(!observation.terminated &&
            observation.players.size == 4 &&
            observation.players.map { it.id }.distinct().size == 4 &&
            observation.players.count { it.id == actor && it.isPerspective } == 1 &&
            observation.agentToAct == actor &&
            observation.pendingDecision == null &&
            observation.stack.isEmpty() &&
            observation.legalActions.isEmpty()) {
            "Unqualified $role pregame observation or actor authority"
        }
        // A pregame decision actor need not own the main game's priority pointer:
        // the trusted PhaseTwoPilotBoundary selects the outstanding mulligan seat.
        // Never depend on the hidden/private engine state to infer that actor.
        require(observation.zones.filter {
            it.ownerId != actor && it.zoneType in setOf(Zone.HAND, Zone.LIBRARY)
        }.all { it.hidden && it.cards.isEmpty() }) {
            "Opponent private hand or library unexpectedly exposed"
        }
        require(observation.zones.filter {
            it.ownerId == actor && it.zoneType == Zone.LIBRARY
        }.all { it.hidden && it.cards.isEmpty() }) {
            "Actor's unshuffled or future library was exposed"
        }

        val ownCommand = observation.zones.single {
            it.ownerId == actor && it.zoneType == Zone.COMMAND
        }
        require(!ownCommand.hidden && ownCommand.size == 1 &&
            ownCommand.cards.size == 1 &&
            ownCommand.cards.single().name == "Animar, Soul of Elements") {
            "Exact physical Animar commander identity required"
        }
        val hand = observation.zones.single {
            it.ownerId == actor && it.zoneType == Zone.HAND
        }
        require(!hand.hidden && hand.size == 7 &&
            hand.cards.size == hand.size &&
            hand.cards.all { it.ownerId == actor && it.zone == Zone.HAND } &&
            hand.cards.map { it.entityId }.distinct().size == hand.size) {
            "Exact visible seven physical own-hand cards required"
        }

        return when (prompt) {
            is PhaseTwoPilotPrompt.Mulligan -> {
                require(!prompt.canMulligan) {
                    "Voluntary R3 keep-versus-mulligan remains unimplemented"
                }
                PhaseTwoPilotChoice.Keep
            }
            is PhaseTwoPilotPrompt.Bottom -> throw IllegalArgumentException(
                "All London bottom selections retain strategic card/order choices; R3 unimplemented"
            )
            PhaseTwoPilotPrompt.Engine -> error("Rejected by pregame prompt guard")
        }
    }
}
