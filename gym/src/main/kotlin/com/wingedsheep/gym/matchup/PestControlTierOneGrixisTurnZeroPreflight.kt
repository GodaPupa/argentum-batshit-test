package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry
import kotlinx.serialization.decodeFromString

const val PEST_GRIXIS_TELEMETRY_SCHEMA_SHA256 =
    "88d48023fb39fd59e637ff4e3fde12df0f5a7bf256a5db041ca3c9a16b5e3d73"

data class GrixisTurnZeroPreflightResult(
    val errors: List<String>,
    val activationBlockers: List<String>,
    val qualifiedRunner: String,
    val constructionStepCount: Int,
    val telemetrySchemaSha256: String,
    val artifactContractValidated: Boolean,
    val officialSeedsGenerated: Int = 0,
    val officialGamesAuthorized: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val green: Boolean get() = errors.isEmpty()
}

/**
 * Vectorless turn-zero composition check. It initializes only the fixed construction fixture,
 * never submits an action, and preserves the official activation blockers.
 */
object PestControlTierOneGrixisTurnZeroPreflight {
    private val expectedBlockers = listOf(
        "smoke harness is not AUTHORIZED",
        "smoke vector is not frozen",
        "game adapter has no official initialization method",
        "no execution method is defined",
    )

    fun inspect(registry: CardRegistry): GrixisTurnZeroPreflightResult {
        val errors = mutableListOf<String>()
        val readiness = GrixisSmokeHarnessReadiness()
        val blockers = PestControlTierOneGrixisSmokeHarness.activationErrors(
            readiness = readiness,
            registry = registry,
            explicitAuthorization = true,
            isUnitTestProcess = false,
            attemptNumber = 1,
            priorOutputExists = false,
        )
        if (blockers != expectedBlockers) errors += "activation blocker set mismatch"
        if (PEST_V2_QUALIFIED_RUNNER != "9829ee98869343cd48dceaa9a27c56ed27c6b3bc") {
            errors += "qualified runner mismatch"
        }

        var stepCount = -1
        runCatching { PestControlTierOneGrixisInitializationContract.initializeConstructionFixture(registry) }
            .onSuccess { fixture ->
                stepCount = fixture.environment.stepCount
                if (stepCount != 0) errors += "construction fixture advanced"
                if (!fixture.excludedFromExperimentalEvidence ||
                    !fixture.excludedFromFutureSeedOverlapRegistry
                ) errors += "construction fixture exclusion mismatch"
            }
            .onFailure { errors += "construction conservation failed: ${it.message}" }

        val telemetryHash = PestControlTierOneGrixisTelemetryContract.schemaSha256()
        if (telemetryHash != PEST_GRIXIS_TELEMETRY_SCHEMA_SHA256) errors += "telemetry schema mismatch"
        if (PestControlTierOneGrixisTelemetryContract.indexText(emptyList()) != GrixisTelemetryIndex()) {
            errors += "empty telemetry index mismatch"
        }

        val artifactValidated = runCatching { validateSyntheticArtifactContract() }.isSuccess
        if (!artifactValidated) errors += "artifact reconciliation contract mismatch"
        if (readiness.officialSeedsGenerated != 0 || readiness.officialGamesAuthorized != 0 ||
            readiness.outcomeExposure != 0
        ) errors += "official counters are nonzero"

        return GrixisTurnZeroPreflightResult(
            errors = errors.distinct(),
            activationBlockers = blockers,
            qualifiedRunner = PEST_V2_QUALIFIED_RUNNER,
            constructionStepCount = stepCount,
            telemetrySchemaSha256 = telemetryHash,
            artifactContractValidated = artifactValidated,
        )
    }

    private fun validateSyntheticArtifactContract() {
        val identity = GrixisSmokeVectorIdentity(
            freezeCommit = "1".repeat(40),
            orderedVectorSha256 = "2".repeat(64),
            assignmentCsvSha256 = "3".repeat(64),
            freezeManifestSha256 = "4".repeat(64),
        )
        val syntheticSeeds = listOf(101L, 102L, 103L, 104L)
        val summary = "synthetic-turn-zero-preflight\n".toByteArray()
        val bytes = PestControlTierOneGrixisArtifactContract.buildIndex(
            vectorIdentity = identity,
            attempts = emptyList(),
            recordedGames = emptyList(),
            perGameRaw = emptyList(),
            summary = summary,
            disposition = "REJECTED",
        )
        val index = PROTOCOL_JSON.decodeFromString<GrixisSmokeArtifactIndex>(bytes.decodeToString())
        require(
            PestControlTierOneGrixisArtifactContract.validate(
                index,
                identity,
                syntheticSeeds,
                emptyList(),
                summary,
            ).isEmpty()
        )
    }
}
