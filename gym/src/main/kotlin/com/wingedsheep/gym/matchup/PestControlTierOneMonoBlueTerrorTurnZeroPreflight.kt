package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry
import kotlinx.serialization.decodeFromString

const val PEST_MONO_BLUE_TERROR_TELEMETRY_SCHEMA_SHA256 =
    "42a0b2df0d0f7f50752bbaeb3897e66382042e301fc5113cfc1d8ddcb52ab428"

data class MonoBlueTerrorTurnZeroPreflightResult(
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
object PestControlTierOneMonoBlueTerrorTurnZeroPreflight {
    private val expectedBlockers = listOf(
        "smoke harness is not AUTHORIZED",
        "smoke vector is not frozen",
        "game adapter has no official initialization method",
        "no execution method is defined",
    )

    fun inspect(registry: CardRegistry): MonoBlueTerrorTurnZeroPreflightResult {
        val errors = mutableListOf<String>()
        val readiness = MonoBlueTerrorSmokeHarnessReadiness()
        val blockers = PestControlTierOneMonoBlueTerrorSmokeHarness.activationErrors(
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
        runCatching {
            PestControlTierOneMonoBlueTerrorInitializationContract.initializeConstructionFixture(registry)
        }.onSuccess { fixture ->
            stepCount = fixture.environment.stepCount
            if (stepCount != 0) errors += "construction fixture advanced"
            if (!fixture.excludedFromExperimentalEvidence ||
                !fixture.excludedFromFutureSeedOverlapRegistry
            ) {
                errors += "construction fixture exclusion mismatch"
            }
        }.onFailure {
            errors += "construction conservation failed: ${it.message}"
        }

        val telemetryHash = PestControlTierOneMonoBlueTerrorTelemetryContract.schemaSha256()
        if (telemetryHash != PEST_MONO_BLUE_TERROR_TELEMETRY_SCHEMA_SHA256) {
            errors += "telemetry schema mismatch"
        }
        if (
            PestControlTierOneMonoBlueTerrorTelemetryContract.indexText(emptyList()) !=
            MonoBlueTerrorTelemetryIndex()
        ) {
            errors += "empty telemetry index mismatch"
        }

        val artifactValidated = runCatching { validateSyntheticArtifactContract() }.isSuccess
        if (!artifactValidated) errors += "artifact reconciliation contract mismatch"

        if (readiness.officialSeedsGenerated != 0 ||
            readiness.officialGamesAuthorized != 0 ||
            readiness.outcomeExposure != 0
        ) {
            errors += "official counters are nonzero"
        }

        return MonoBlueTerrorTurnZeroPreflightResult(
            errors = errors.distinct(),
            activationBlockers = blockers,
            qualifiedRunner = PEST_V2_QUALIFIED_RUNNER,
            constructionStepCount = stepCount,
            telemetrySchemaSha256 = telemetryHash,
            artifactContractValidated = artifactValidated,
        )
    }

    private fun validateSyntheticArtifactContract() {
        val identity = MonoBlueTerrorSmokeVectorIdentity(
            freezeCommit = "1".repeat(40),
            orderedVectorSha256 = "2".repeat(64),
            assignmentCsvSha256 = "3".repeat(64),
            freezeManifestSha256 = "4".repeat(64),
        )
        val syntheticSeeds = listOf(201L, 202L, 203L, 204L)
        val summary = "synthetic-terror-turn-zero-preflight\n".toByteArray()
        val bytes = PestControlTierOneMonoBlueTerrorArtifactContract.buildIndex(
            vectorIdentity = identity,
            attempts = emptyList(),
            recordedGames = emptyList(),
            perGameRaw = emptyList(),
            summary = summary,
            disposition = "REJECTED",
        )
        val index =
            PROTOCOL_JSON.decodeFromString<MonoBlueTerrorSmokeArtifactIndex>(bytes.decodeToString())
        require(
            PestControlTierOneMonoBlueTerrorArtifactContract.validate(
                index,
                identity,
                syntheticSeeds,
                emptyList(),
                summary,
            ).isEmpty()
        )
    }
}
