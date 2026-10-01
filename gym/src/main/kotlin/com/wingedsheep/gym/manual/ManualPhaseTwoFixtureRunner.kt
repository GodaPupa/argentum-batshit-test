package com.wingedsheep.gym.manual

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.model.EntityId
import java.nio.file.Path

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

    companion object {
        /**
         * Fixture-only trusted entry: reserve and force identity/intent before the factory may
         * initialize telemetry or construct pilots. The factory and trace codec must be source
         * reviewed separately. This does not add official admission or per-action write-ahead
         * logging to the existing in-memory telemetry boundary.
         */
        fun runNewFixture(
            root: Path,
            identity: ManualFixtureIdentity,
            trustedInitializeRunner: () -> ManualPhaseTwoFixtureRunner,
            trustedEncodeTrace: (PhaseTwoEngineTrace) -> ByteArray,
        ): PhaseTwoEngineTrace = ManualPhaseTwoFixtureLifecycle.runNew(
            root, identity, trustedInitializeRunner, { it.runOnce() }, trustedEncodeTrace,
        )
    }
}
