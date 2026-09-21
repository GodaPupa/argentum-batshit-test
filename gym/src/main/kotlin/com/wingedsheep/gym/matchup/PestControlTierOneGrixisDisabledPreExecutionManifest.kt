package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_GRIXIS_DISABLED_PREEXECUTION_MANIFEST_SHA256 =
    "d3fb8036426119983ae8b0e3a0f8fd11700ddd0e571a04da099613aa7a69ab32"

data class GrixisDisabledPreExecutionManifestInspection(
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
object PestControlTierOneGrixisDisabledPreExecutionManifest {
    fun inspect(
        registry: CardRegistry,
        state: String = "DISABLED",
    ): GrixisDisabledPreExecutionManifestInspection {
        val errors = mutableListOf<String>()
        val provenance = PestControlTierOneGrixisDisabledSyntheticProvenance.inspect(registry)
        if (!provenance.green) errors += "disabled synthetic provenance is not green"
        if (provenance.provenanceSha256 != PEST_GRIXIS_DISABLED_SYNTHETIC_PROVENANCE_SHA256) {
            errors += "disabled synthetic provenance proof mismatch"
        }
        if (state != "DISABLED") errors += "pre-execution manifest must remain DISABLED"
        if (provenance.rows.size != PEST_GRIXIS_SMOKE_GAMES) {
            errors += "pre-execution manifest provenance row count mismatch"
        }

        val proofBytes = listOf(
            "pest-control-tier-one-grixis-disabled-preexecution-manifest-v1",
            "state=$state",
            "protocolId=$PEST_GRIXIS_PREBOARD_PROTOCOL_ID",
            "blockId=$PEST_GRIXIS_SMOKE_BLOCK_ID",
            "qualifiedRunner=$PEST_V2_QUALIFIED_RUNNER",
            "pestMainSha256=$PEST_CONTROL_V10_HASH",
            "grixisMainSha256=$PEST_GRIXIS_MAIN_SHA256",
            "grixisComplete75Sha256=$PEST_GRIXIS_COMPLETE_75_SHA256",
            "initializerConstructionSha256=$PEST_GRIXIS_DISABLED_INITIALIZER_CONSTRUCTION_SHA256",
            "blockerSha256=$PEST_GRIXIS_OFFICIAL_INITIALIZATION_BLOCKER_SHA256",
            "singleGameSha256=$PEST_GRIXIS_DISABLED_SINGLE_GAME_SHA256",
            "fourCellPlanSha256=$PEST_GRIXIS_DISABLED_FOUR_CELL_PLAN_SHA256",
            "assignmentSchemaSha256=$PEST_GRIXIS_DISABLED_ASSIGNMENT_SCHEMA_SHA256",
            "syntheticProvenanceSha256=$PEST_GRIXIS_DISABLED_SYNTHETIC_PROVENANCE_SHA256",
            "expectedGames=$PEST_GRIXIS_SMOKE_GAMES",
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
        if (manifestSha256 != PEST_GRIXIS_DISABLED_PREEXECUTION_MANIFEST_SHA256) {
            errors += "disabled pre-execution manifest hash mismatch"
        }
        return GrixisDisabledPreExecutionManifestInspection(
            errors = errors.distinct(),
            manifestSha256 = manifestSha256,
            state = state,
            expectedGames = PEST_GRIXIS_SMOKE_GAMES,
            provenanceRows = provenance.rows.size,
        )
    }
}
