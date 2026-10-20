package com.wingedsheep.gym.manual

import com.wingedsheep.gym.contract.TrainingObservation

/**
 * An opt-in, strictly PARTIAL adapter for the three fixed-hardware Manual gear labels.
 *
 * This joins only separately fixture-tested, actor-masked seams. It is NOT a whole-game pilot
 * or a Phase-2 gameplay admission: mulligans, bottoming, many strategic action forks, target
 * decisions, opponent pilots and the seven Race overlay *actions* remain unimplemented.
 * An unsupported window throws; there is no catch-and-pass, auto-responder or fallback.
 */
internal class ManualPhaseTwoPartialGearPilot(
    private val role: ManualPhaseTwoPilotRole,
) : PhaseTwoPilotPolicy {
    init {
        require(role != ManualPhaseTwoPilotRole.OPPONENT) {
            "An opponent cannot impersonate one of the frozen Manual gear pilots"
        }
    }

    private val opening = ManualPhaseTwoOpeningActionPolicy(role)
    private val commander = ManualPhaseTwoUniqueCommanderCastPolicy(role)
    private val conservation = if (role == ManualPhaseTwoPilotRole.CRUISE) null
        else ManualPhaseTwoPriorityConservationPolicy(role)

    override fun choose(
        observation: TrainingObservation,
        prompt: PhaseTwoPilotPrompt,
    ): PhaseTwoPilotChoice {
        require(prompt == PhaseTwoPilotPrompt.Engine && observation.pendingDecision == null) {
            "Unqualified $role mulligan/bottom/typed decision; no fallback"
        }
        val actor = observation.perspectivePlayerId
        require(!observation.terminated &&
            observation.players.size == 4 &&
            observation.players.map { it.id }.distinct().size == 4 &&
            observation.players.count { it.id == actor && it.isPerspective } == 1 &&
            observation.agentToAct == actor &&
            observation.priorityPlayerId == actor) {
            "Unqualified $role observation authority or four-player state"
        }

        // Bind only the seven accepted overlays and only for Race. This is a public-identity
        // guard, NOT an implementation of the overlays' strategic actions. Cruise/Sport never
        // invoke it. Unknown, incomplete or hidden opponent command identities fail closed.
        if (role == ManualPhaseTwoPilotRole.RACE) {
            val axes = ManualPhaseTwoRaceOverlayRouter.bind(role, observation)
            require(axes.keys == observation.players.map { it.id }
                .filter { it != actor }.toSet()) {
                "Race does not have exactly three accepted public opponent identities"
            }
        }

        // The narrow tested R3-P priority window is the only supported nonempty-stack
        // response, and it belongs ONLY to Sport/Race. Do not silently pass other windows.
        if (observation.stack.isNotEmpty()) {
            return requireNotNull(conservation) {
                "Cruise does not inherit Sport/Race priority conservation"
            }.choose(observation, prompt)
        }

        val strategic = observation.legalActions.filter {
            it.affordable && it.kind != "PassPriority" && !it.isManaAbility
        }
        require(strategic.size <= 1) {
            "Competing $role strategic actions require an unimplemented gear policy"
        }
        return when (strategic.singleOrNull()?.kind) {
            null, "PlayLand" -> opening.choose(observation, prompt)
            "CastSpell" -> commander.choose(observation, prompt)
            else -> throw IllegalArgumentException(
                "Unqualified $role action kind; no automatic pass or generic AI substitution"
            )
        }
    }
}
