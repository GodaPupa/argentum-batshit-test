package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_MONO_BLUE_TERROR_CONSTRUCTION_CLOSURE_SHA256 =
    "ff68ed38ab676ae989191aca44d720b32605743c1501455193e6a22d76df6538"
const val PEST_MONO_BLUE_TERROR_CONSTRUCTION_READY_STATUS =
    "CONSTRUCTION_READY_VECTOR_CREATION_NOT_AUTHORIZED"

data class MonoBlueTerrorConstructionClosureInspection(
    val errors: List<String>,
    val closureSha256: String,
    val status: String,
    val constructionReady: Boolean,
    val vectorCreationAuthorized: Boolean = false,
    val vectorIdentityPresent: Boolean = false,
    val officialAssignments: Int = 0,
    val officialSeedsGenerated: Int = 0,
    val officialGamesInitialized: Int = 0,
    val submittedActions: Int = 0,
    val artifactsWritten: Int = 0,
    val outcomeExposure: Int = 0,
)

/** Final closure inspection for construction only. It grants no vector or execution authority. */
object PestControlTierOneMonoBlueTerrorConstructionClosure {
    fun inspect(
        registry: CardRegistry,
        runnerSurface: MonoBlueTerrorRunnerSurfacePreflightResult,
    ): MonoBlueTerrorConstructionClosureInspection {
        val errors = mutableListOf<String>()

        val manifest =
            PestControlTierOneMonoBlueTerrorDisabledPreExecutionManifest.inspect(registry)
        if (!manifest.green) {
            errors += "disabled pre-execution manifest is not green"
        }
        if (
            manifest.manifestSha256 !=
            PEST_MONO_BLUE_TERROR_DISABLED_PREEXECUTION_MANIFEST_SHA256
        ) {
            errors += "disabled pre-execution manifest proof mismatch"
        }

        if (!runnerSurface.green) {
            errors += "runner surface preflight is not green"
        }
        if (runnerSurface.workflowFilesAudited <= 0) {
            errors += "no workflow files were audited"
        }
        if (runnerSurface.commandFilesAudited <= 0) {
            errors += "no command files were audited"
        }
        if (runnerSurface.classesAudited != 9) {
            errors += "compiled surface audit count mismatch"
        }
        if (
            runnerSurface.officialSeedsGenerated != 0 ||
            runnerSurface.officialGamesInitialized != 0 ||
            runnerSurface.outcomeExposure != 0
        ) {
            errors += "runner surface official counters are nonzero"
        }

        val boundary =
            PestControlTierOneMonoBlueTerrorOfficialInitializationBoundary.inspect(registry)
        if (
            !boundary.failClosed ||
            "official initializer is disabled" !in boundary.activationBlockers
        ) {
            errors += "official initializer terminal blocker is absent"
        }

        val preliminaryReady = errors.isEmpty()
        val status = if (preliminaryReady) {
            PEST_MONO_BLUE_TERROR_CONSTRUCTION_READY_STATUS
        } else {
            "CONSTRUCTION_BLOCKED"
        }

        val proofBytes = listOf(
            "pest-control-tier-one-mono-blue-terror-construction-closure-v1",
            "status=$status",
            "manifestSha256=${manifest.manifestSha256}",
            "surfaceGreen=${runnerSurface.green}",
            "workflowFilesAuditedPositive=${runnerSurface.workflowFilesAudited > 0}",
            "commandFilesAuditedPositive=${runnerSurface.commandFilesAudited > 0}",
            "classesAudited=${runnerSurface.classesAudited}",
            "initializerBlocker=official initializer is disabled",
            "constructionReady=$preliminaryReady",
            "vectorCreationAuthorized=false",
            "vectorIdentityPresent=false",
            "officialAssignments=0",
            "officialSeedsGenerated=0",
            "officialGamesInitialized=0",
            "submittedActions=0",
            "artifactsWritten=0",
            "outcomeExposure=0",
        ).joinToString("\n", postfix = "\n").toByteArray()

        val closureSha256 = sha256(proofBytes)
        if (closureSha256 != PEST_MONO_BLUE_TERROR_CONSTRUCTION_CLOSURE_SHA256) {
            errors += "construction closure hash mismatch"
        }

        return MonoBlueTerrorConstructionClosureInspection(
            errors = errors.distinct(),
            closureSha256 = closureSha256,
            status = status,
            constructionReady = errors.isEmpty(),
        )
    }
}
