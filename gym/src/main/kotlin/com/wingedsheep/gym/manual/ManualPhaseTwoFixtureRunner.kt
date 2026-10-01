package com.wingedsheep.gym.manual

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.model.EntityId

/**
 * Dormant, fixture-only composition of the existing four-seat masked boundary and telemetry.
 * Does not initialize games or create claims. The prospective trusted caller must separately
 * bind its fixture source/decks/policies/runtime and durable initialization barrier.
 * No official admission, opponent competence, complete policy coverage, or fresh replay is
 * established by this adapter. Exceptions propagate with the loop consumed; no fallback.
 */
internal class ManualPhaseTwoFixtureRunner(
    private val telemetry: PhaseTwoTelemetryAdapter,
    registry: CardRegistry,
    policies: Map<EntityId, PhaseTwoPilotPolicy>,
    budget: ManualFixtureStepBudget,
) {
    private val boundary = PhaseTwoPilotBoundary(telemetry, registry, policies.toMap())
    private val loop = ManualPhaseTwoFixtureRunLoop<PhaseTwoEngineTrace>(budget)

    fun runOnce(): PhaseTwoEngineTrace = loop.runOnce(
        terminal = { telemetry.state.gameOver },
        step = { boundary.step().error != null },
        stop = { kind, reason -> telemetry.stop(kind, reason) },
        finish = { telemetry.finish() },
    )
}
