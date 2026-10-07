package com.wingedsheep.gym.izzet

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import kotlinx.serialization.json.Json
import java.nio.file.Path

/** Privileged result for the trusted fixture owner, never a policy input. Not durable evidence. */
internal data class IzzetSyntheticSubmission(val action: GameAction, val result: ExecutionResult)

/**
 * One-submission synthetic runner, composing the accepted attempt barrier and four-action window.
 * No loop, continuation, journal or policy competence is claimed. The initializer and registry
 * remain trusted provenance boundaries. A policy receives only the detached masked menu.
 */
internal class IzzetSyntheticSingleSubmission private constructor(
    private val state: GameState,
    private val registry: CardRegistry,
    private val identity: IzzetSyntheticAttemptIdentity,
) {
    private var consumed = false

    @Synchronized
    fun executeOnce(policy: (IzzetPilotMenu) -> IzzetNumberedProposal): IzzetSyntheticSubmission {
        check(!consumed) { "Synthetic submission already consumed" }
        consumed = true // Includes projection, policy, resolution and engine failures.
        val boundary = IzzetTrustedProposalBoundary.open(registry, state, identity.pins, identity.attemptId, 0)
        val proposed = policy(boundary.pilotMenu())
        val action = boundary.resolve(proposed, state, identity.pins, identity.attemptId, 0)
        val result = ActionProcessor(registry).process(state, action).result
        require(result.error == null) { "Synthetic engine rejected proposal: ${result.error}" }
        return IzzetSyntheticSubmission(action, result)
    }

    companion object {
        private val JSON = Json {
            serializersModule = engineSerializersModule
            encodeDefaults = true
            allowStructuredMapKeys = true
        }
        /** Caller cannot obtain the runner before initial envelope and completion marker are forced. */
        fun create(root: Path, identity: IzzetSyntheticAttemptIdentity, registry: CardRegistry,
                   trustedInitialize: () -> GameState): IzzetSyntheticSingleSubmission =
            IzzetSyntheticAttempt.initializeOnce(root, identity, {
                val encoded = JSON.encodeToString(GameState.serializer(), trustedInitialize())
                val detached = JSON.decodeFromString(GameState.serializer(), encoded)
                IzzetSyntheticSingleSubmission(detached, registry, identity)
            }, { JSON.encodeToString(GameState.serializer(), it.state).toByteArray(Charsets.UTF_8) })
    }
}
