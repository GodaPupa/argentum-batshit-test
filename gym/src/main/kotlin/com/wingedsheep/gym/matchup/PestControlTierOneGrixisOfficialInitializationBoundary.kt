package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_GRIXIS_OFFICIAL_INITIALIZATION_BLOCKER_SHA256 =
    "fdfc85b1be4c27556ca1acd9dce20407d9730026e93fc7f096234fd4c472c1d1"

data class GrixisOfficialInitializationRequest(
    val readiness: GrixisSmokeHarnessReadiness = GrixisSmokeHarnessReadiness(),
    val assignment: GrixisSmokeAssignment? = null,
    val executionCommit: String? = null,
    val qualifiedRunner: String = PEST_V2_QUALIFIED_RUNNER,
    val explicitAuthorization: Boolean = true,
    val isUnitTestProcess: Boolean = false,
    val attemptNumber: Int = 1,
    val priorOutputExists: Boolean = false,
    val durableAttemptRecorded: Boolean = false,
)

data class GrixisOfficialInitializationBoundaryResult(
    val contractErrors: List<String>,
    val activationBlockers: List<String>,
    val blockerSha256: String,
    val officialSeedsGenerated: Int = 0,
    val officialGamesInitialized: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val failClosed: Boolean
        get() = contractErrors.isEmpty() &&
            activationBlockers.contains("official initializer implementation is absent")
}

/**
 * Validation-only boundary for a future official initializer. There is deliberately no function
 * returning a game environment or session, so satisfying request fields cannot consume a seed.
 */
object PestControlTierOneGrixisOfficialInitializationBoundary {
    fun inspect(
        registry: CardRegistry,
        request: GrixisOfficialInitializationRequest = GrixisOfficialInitializationRequest(),
    ): GrixisOfficialInitializationBoundaryResult {
        val contractErrors = mutableListOf<String>()
        val preflight = PestControlTierOneGrixisTurnZeroPreflight.inspect(registry)
        if (!preflight.green) contractErrors += "turn-zero preflight is not green"
        if (request.qualifiedRunner != PEST_V2_QUALIFIED_RUNNER) {
            contractErrors += "qualified runner mismatch"
        }

        val blockers = PestControlTierOneGrixisSmokeHarness.activationErrors(
            readiness = request.readiness,
            registry = registry,
            explicitAuthorization = request.explicitAuthorization,
            isUnitTestProcess = request.isUnitTestProcess,
            attemptNumber = request.attemptNumber,
            priorOutputExists = request.priorOutputExists,
        ).toMutableList()
        if (request.assignment == null) blockers += "official assignment is absent"
        if (request.executionCommit == null) {
            blockers += "execution commit is absent"
        } else if (!request.executionCommit.matches(Regex("[0-9a-f]{40}"))) {
            blockers += "execution commit is malformed"
        }
        if (!request.durableAttemptRecorded) blockers += "durable attempt marker is absent"

        val identity = request.readiness.vectorIdentity
        val assignment = request.assignment
        val executionCommit = request.executionCommit
        if (identity != null && assignment != null && executionCommit != null &&
            executionCommit.matches(Regex("[0-9a-f]{40}"))
        ) {
            runCatching {
                PestControlTierOneGrixisGameAdapter.provenance(assignment, identity, executionCommit)
            }.onFailure { blockers += "official assignment provenance is invalid" }
        }
        blockers += "official initializer implementation is absent"

        val distinctBlockers = blockers.distinct()
        val blockerBytes = distinctBlockers.joinToString("\n", postfix = "\n").toByteArray()
        val blockerSha256 = sha256(blockerBytes)
        if (request == GrixisOfficialInitializationRequest() &&
            blockerSha256 != PEST_GRIXIS_OFFICIAL_INITIALIZATION_BLOCKER_SHA256
        ) contractErrors += "canonical activation blocker hash mismatch"
        return GrixisOfficialInitializationBoundaryResult(
            contractErrors = contractErrors.distinct(),
            activationBlockers = distinctBlockers,
            blockerSha256 = blockerSha256,
        )
    }
}
