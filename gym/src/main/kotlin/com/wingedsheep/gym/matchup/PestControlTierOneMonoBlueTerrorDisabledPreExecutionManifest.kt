package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_MONO_BLUE_TERROR_DISABLED_PREEXECUTION_MANIFEST_SHA256 =
    "e4ef6cee09398b4f3b3f08a15852e95fb930ab5f39ede991da8c0dbdedcd2e10"

data class MonoBlueTerrorDisabledPreExecutionManifestInspection(
    val errors: List<String>,
    val manifestSha256: String,
    val state: String,
    val expectedGames: Int,
    val provenanceRows: Int,
    val vectorIdentityPresent: Boolean = false,
    val initializerEnabled: Boolean = false,
    val workflowEntrypoints: Int = 0,
    val commandEntrypoints: Int = 0,
    val officialAssignments: Int = 0,
    val officialSeedsGenerated: Int = 0,
    val officialGamesInitialized: Int = 0,
    val submittedActions: Int = 0,
    val artifactsWritten: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val green: Boolean get() = errors.isEmpty()
}

/** Pure in-memory manifest inspection. It cannot write a manifest or activate execution. */
object PestControlTierOneMonoBlueTerrorDisabledPreExecutionManifest {
    fun inspect(
        registry: CardRegistry,
        state: String = "DISABLED",
    ): MonoBlueTerrorDisabledPreExecutionManifestInspection {
        val errors = mutableListOf<String>()

        val provenance =
            PestControlTierOneMonoBlueTerrorDisabledSyntheticProvenance.inspect(registry)
        if (!provenance.green) {
            errors += "disabled synthetic provenance is not green"
        }
        if (
            provenance.provenanceSha256 !=
            PEST_MONO_BLUE_TERROR_DISABLED_SYNTHETIC_PROVENANCE_SHA256
        ) {
            errors += "disabled synthetic provenance proof mismatch"
        }
        if (state != "DISABLED") {
            errors += "pre-execution manifest must remain DISABLED"
        }
        if (provenance.rows.size != PEST_MONO_BLUE_TERROR_SMOKE_GAMES) {
            errors += "pre-execution manifest provenance row count mismatch"
        }

        val proofBytes = listOf(
            "pest-control-tier-one-mono-blue-terror-disabled-preexecution-manifest-v1",
            "state=$state",
            "protocolId=$PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID",
            "blockId=$PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID",
            "qualifiedRunner=$PEST_V2_QUALIFIED_RUNNER",
            "pestMainSha256=$PEST_CONTROL_V10_HASH",
            "terrorMainSha256=$PEST_MONO_BLUE_TERROR_MAIN_SHA256",
            "terrorComplete75Sha256=$PEST_MONO_BLUE_TERROR_COMPLETE_75_SHA256",
            "initializerConstructionSha256=" +
                PEST_MONO_BLUE_TERROR_DISABLED_INITIALIZER_CONSTRUCTION_SHA256,
            "blockerSha256=$PEST_MONO_BLUE_TERROR_OFFICIAL_INITIALIZATION_BLOCKER_SHA256",
            "singleGameSha256=$PEST_MONO_BLUE_TERROR_DISABLED_SINGLE_GAME_SHA256",
            "fourCellPlanSha256=$PEST_MONO_BLUE_TERROR_DISABLED_FOUR_CELL_PLAN_SHA256",
            "assignmentSchemaSha256=" +
                PEST_MONO_BLUE_TERROR_DISABLED_ASSIGNMENT_SCHEMA_SHA256,
            "syntheticProvenanceSha256=" +
                PEST_MONO_BLUE_TERROR_DISABLED_SYNTHETIC_PROVENANCE_SHA256,
            "expectedGames=$PEST_MONO_BLUE_TERROR_SMOKE_GAMES",
            "provenanceRows=${provenance.rows.size}",
            "vectorIdentityPresent=false",
            "initializerEnabled=false",
            "workflowEntrypoints=0",
            "commandEntrypoints=0",
            "officialAssignments=0",
            "officialSeedsGenerated=0",
            "officialGamesInitialized=0",
            "submittedActions=0",
            "artifactsWritten=0",
            "outcomeExposure=0",
        ).joinToString("\n", postfix = "\n").toByteArray()

        val manifestSha256 = sha256(proofBytes)
        if (
            manifestSha256 !=
            PEST_MONO_BLUE_TERROR_DISABLED_PREEXECUTION_MANIFEST_SHA256
        ) {
            errors += "disabled pre-execution manifest hash mismatch"
        }

        return MonoBlueTerrorDisabledPreExecutionManifestInspection(
            errors = errors.distinct(),
            manifestSha256 = manifestSha256,
            state = state,
            expectedGames = PEST_MONO_BLUE_TERROR_SMOKE_GAMES,
            provenanceRows = provenance.rows.size,
        )
    }
}
