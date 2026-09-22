package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_MONO_BLUE_TERROR_OFFICIAL_INITIALIZATION_BLOCKER_SHA256 =
    "64cd80b7d0e7e5a76c89fe98b7b67355ff21a9569cbb5720a89314474efaad14"
const val PEST_MONO_BLUE_TERROR_DISABLED_INITIALIZER_CONSTRUCTION_SHA256 =
    "f99fae927e7ca5a4530b315a00107dcedb845f78e7b45a5b680185f99f39533e"

data class MonoBlueTerrorOfficialInitializationRequest(
    val readiness: MonoBlueTerrorSmokeHarnessReadiness = MonoBlueTerrorSmokeHarnessReadiness(),
    val assignment: MonoBlueTerrorSmokeAssignment? = null,
    val executionCommit: String? = null,
    val qualifiedRunner: String = PEST_V2_QUALIFIED_RUNNER,
    val explicitAuthorization: Boolean = true,
    val isUnitTestProcess: Boolean = false,
    val attemptNumber: Int = 1,
    val priorOutputExists: Boolean = false,
    val durableAttemptRecorded: Boolean = false,
)

data class MonoBlueTerrorOfficialInitializationBoundaryResult(
    val contractErrors: List<String>,
    val activationBlockers: List<String>,
    val blockerSha256: String,
    val constructionValidationSha256: String?,
    val officialInitializerImplemented: Boolean = true,
    val officialInitializerEnabled: Boolean = false,
    val disabledInitializerConstructionGamesInitialized: Int = 0,
    val officialSeedsGenerated: Int = 0,
    val officialGamesInitialized: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val failClosed: Boolean
        get() = contractErrors.isEmpty() &&
            officialInitializerImplemented &&
            !officialInitializerEnabled &&
            activationBlockers.contains("official initializer is disabled")
}

private data class MonoBlueTerrorDisabledInitializerConstructionProof(val sha256: String)

/**
 * The implementation is intentionally private and construction-locked. It has no parameter for an
 * assignment, vector, seed, authorization, or execution commit and never returns its environment.
 */
private object PestControlTierOneMonoBlueTerrorDisabledOfficialInitializer {
    fun validateConstruction(
        registry: CardRegistry,
    ): MonoBlueTerrorDisabledInitializerConstructionProof {
        val fixture = initialize(registry)
        require(fixture.provenance.seed == NONEXPERIMENTAL_TERROR_INITIALIZER_FIXTURE_ENTROPY)
        require(fixture.excludedFromExperimentalEvidence)
        require(fixture.excludedFromFutureSeedOverlapRegistry)

        val conservation = fixture.openingConservation.sortedBy { it.seat.ordinal }
        require(conservation.size == 2)
        require(
            conservation.map {
                listOf(
                    it.seat.name,
                    it.deckIdentity,
                    it.deckSha256,
                    it.libraryCount,
                    it.handCount,
                    it.otherZoneCount,
                    it.totalOwnedCards,
                ).joinToString("|")
            } == listOf(
                "SEAT_ZERO|PEST_CONTROL_V10|$PEST_CONTROL_V10_HASH|53|7|0|60",
                "SEAT_ONE|SERPICO_CC_MONO_BLUE_TERROR_60|$PEST_MONO_BLUE_TERROR_MAIN_SHA256|53|7|0|60",
            )
        )

        val proofBytes = buildList {
            add("pest-control-tier-one-mono-blue-terror-disabled-official-initializer-v1")
            add(fixture.provenance.seedHex)
            add(fixture.provenance.freezeCommit)
            add(fixture.provenance.sourceCommit)
            conservation.forEach { opening ->
                add(
                    listOf(
                        opening.seat.name,
                        opening.deckIdentity,
                        opening.deckSha256,
                        opening.libraryCount,
                        opening.handCount,
                        opening.otherZoneCount,
                        opening.totalOwnedCards,
                    ).joinToString("|")
                )
            }
            add("excludedFromExperimentalEvidence=true")
            add("excludedFromFutureSeedOverlapRegistry=true")
            add("submittedActions=0")
        }.joinToString("\n", postfix = "\n").toByteArray()

        return MonoBlueTerrorDisabledInitializerConstructionProof(sha256(proofBytes))
    }

    private fun initialize(registry: CardRegistry): MonoBlueTerrorConstructionFixture =
        PestControlTierOneMonoBlueTerrorInitializationContract.initializeConstructionFixture(registry)
}

/**
 * Validation-only public boundary for the disabled official initializer. There is deliberately no
 * public function returning a game environment or session, so satisfying request fields cannot
 * consume a seed.
 */
object PestControlTierOneMonoBlueTerrorOfficialInitializationBoundary {
    fun inspect(
        registry: CardRegistry,
        request: MonoBlueTerrorOfficialInitializationRequest =
            MonoBlueTerrorOfficialInitializationRequest(),
    ): MonoBlueTerrorOfficialInitializationBoundaryResult {
        val contractErrors = mutableListOf<String>()
        val preflight = PestControlTierOneMonoBlueTerrorTurnZeroPreflight.inspect(registry)
        if (!preflight.green) contractErrors += "turn-zero preflight is not green"
        if (request.qualifiedRunner != PEST_V2_QUALIFIED_RUNNER) {
            contractErrors += "qualified runner mismatch"
        }

        val constructionProof = runCatching {
            PestControlTierOneMonoBlueTerrorDisabledOfficialInitializer.validateConstruction(registry)
        }.onFailure {
            contractErrors += "disabled initializer construction validation failed"
        }.getOrNull()

        if (
            constructionProof != null &&
            constructionProof.sha256 != PEST_MONO_BLUE_TERROR_DISABLED_INITIALIZER_CONSTRUCTION_SHA256
        ) {
            contractErrors += "disabled initializer construction hash mismatch"
        }

        val blockers = PestControlTierOneMonoBlueTerrorSmokeHarness.activationErrors(
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
        if (
            identity != null &&
            assignment != null &&
            executionCommit != null &&
            executionCommit.matches(Regex("[0-9a-f]{40}"))
        ) {
            runCatching {
                PestControlTierOneMonoBlueTerrorGameAdapter.provenance(
                    assignment,
                    identity,
                    executionCommit,
                )
            }.onFailure {
                blockers += "official assignment provenance is invalid"
            }
        }

        blockers += "official initializer is disabled"

        val distinctBlockers = blockers.distinct()
        val blockerBytes = distinctBlockers.joinToString("\n", postfix = "\n").toByteArray()
        val blockerSha256 = sha256(blockerBytes)
        if (
            request == MonoBlueTerrorOfficialInitializationRequest() &&
            blockerSha256 != PEST_MONO_BLUE_TERROR_OFFICIAL_INITIALIZATION_BLOCKER_SHA256
        ) {
            contractErrors += "canonical activation blocker hash mismatch"
        }

        return MonoBlueTerrorOfficialInitializationBoundaryResult(
            contractErrors = contractErrors.distinct(),
            activationBlockers = distinctBlockers,
            blockerSha256 = blockerSha256,
            constructionValidationSha256 = constructionProof?.sha256,
            disabledInitializerConstructionGamesInitialized = if (constructionProof == null) 0 else 1,
        )
    }
}
