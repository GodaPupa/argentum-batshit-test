package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

data class MonoBlueTerrorSyntheticExecutionInspection(
    val errors: List<String>,
    val boundaryBlockerSha256: String,
    val coordinatorSchemaSha256: String,
    val attemptedGames: List<Int>,
    val initializedGames: List<Int>,
    val recordedGames: List<Int>,
    val officialSeedsConsumed: Int = 0,
    val officialGamesInitialized: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val valid: Boolean get() = errors.isEmpty()
}

/** Pure composition contract. It validates supplied records and cannot create any of them. */
object PestControlTierOneMonoBlueTerrorExecutionContract {
    fun inspect(
        registry: CardRegistry,
        vectorIdentity: MonoBlueTerrorSmokeVectorIdentity,
        frozenSeeds: List<Long>,
        events: List<MonoBlueTerrorCoordinatorEvent>,
        disposition: MonoBlueTerrorCoordinatorDisposition,
        artifactIndex: MonoBlueTerrorSmokeArtifactIndex,
        perGameRaw: List<ByteArray>,
        summary: ByteArray,
    ): MonoBlueTerrorSyntheticExecutionInspection {
        val errors = mutableListOf<String>()

        val boundary =
            PestControlTierOneMonoBlueTerrorOfficialInitializationBoundary.inspect(registry)
        if (!boundary.failClosed) {
            errors += "official initialization boundary is not fail closed"
        }
        if (
            boundary.activationBlockers.none {
                it == "official initializer implementation is absent"
            }
        ) {
            errors += "official initializer terminal blocker is absent"
        }

        val ledger =
            PestControlTierOneMonoBlueTerrorCoordinatorLedger.validate(events, disposition)
        errors += ledger.errors.map { "coordinator: $it" }

        errors += PestControlTierOneMonoBlueTerrorArtifactContract.validate(
            artifactIndex,
            vectorIdentity,
            frozenSeeds,
            perGameRaw,
            summary,
        ).map { "artifact: $it" }

        if (artifactIndex.attemptedGames != ledger.attemptedGames) {
            errors += "coordinator/artifact attempt mismatch"
        }
        if (artifactIndex.recordedGames != ledger.recordedGames) {
            errors += "coordinator/artifact record mismatch"
        }
        if (ledger.initializedGames.any { it !in ledger.attemptedGames }) {
            errors += "initialized game lacks attempted record"
        }

        val expectedDisposition = when (disposition) {
            MonoBlueTerrorCoordinatorDisposition.VALIDATED -> "VALIDATED"
            MonoBlueTerrorCoordinatorDisposition.REJECTED -> "REJECTED"
        }
        if (artifactIndex.disposition != expectedDisposition) {
            errors += "coordinator/artifact disposition mismatch"
        }

        return MonoBlueTerrorSyntheticExecutionInspection(
            errors = errors.distinct(),
            boundaryBlockerSha256 = boundary.blockerSha256,
            coordinatorSchemaSha256 =
                PestControlTierOneMonoBlueTerrorCoordinatorLedger.schemaSha256(),
            attemptedGames = ledger.attemptedGames,
            initializedGames = ledger.initializedGames,
            recordedGames = ledger.recordedGames,
        )
    }
}
