package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_GRIXIS_DISABLED_SYNTHETIC_PROVENANCE_SHA256 =
    "f33aaf2abb7e8e80a35c68a81b3d1c80dd8caea085a1e2cddb07846fbe130259"
const val PEST_GRIXIS_DISABLED_SYNTHETIC_PROVENANCE_CLASSIFICATION =
    "NONEXPERIMENTAL_SYNTHETIC_PROVENANCE_ONLY"

data class GrixisDisabledSyntheticProvenanceRow(
    val schemaRow: GrixisDisabledAssignmentSchemaRow,
    val protocolId: String,
    val blockId: String,
    val qualifiedRunner: String,
    val pestMainSha256: String,
    val grixisMainSha256: String,
    val assignmentSchemaSha256: String,
    val classification: String,
)

data class GrixisDisabledSyntheticProvenanceInspection(
    val errors: List<String>,
    val provenanceSha256: String,
    val rows: List<GrixisDisabledSyntheticProvenanceRow>,
    val vectorIdentityPresent: Boolean = false,
    val numericEntropyFields: Int = 0,
    val officialAssignments: Int = 0,
    val officialSeedsGenerated: Int = 0,
    val officialGamesInitialized: Int = 0,
    val submittedActions: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val green: Boolean get() = errors.isEmpty()
}

/** Pure provenance binding for opaque synthetic rows. It cannot represent executable entropy. */
object PestControlTierOneGrixisDisabledSyntheticProvenance {
    fun inspect(
        registry: CardRegistry,
        qualifiedRunner: String = PEST_V2_QUALIFIED_RUNNER,
    ): GrixisDisabledSyntheticProvenanceInspection {
        val errors = mutableListOf<String>()
        val schema = PestControlTierOneGrixisDisabledAssignmentSchema.inspect(registry)
        if (!schema.green) errors += "disabled assignment schema is not green"
        if (schema.schemaSha256 != PEST_GRIXIS_DISABLED_ASSIGNMENT_SCHEMA_SHA256) {
            errors += "disabled assignment schema proof mismatch"
        }
        if (qualifiedRunner != PEST_V2_QUALIFIED_RUNNER) errors += "qualified runner mismatch"
        val rows = schema.rows.map { row ->
            GrixisDisabledSyntheticProvenanceRow(
                schemaRow = row,
                protocolId = PEST_GRIXIS_PREBOARD_PROTOCOL_ID,
                blockId = PEST_GRIXIS_SMOKE_BLOCK_ID,
                qualifiedRunner = qualifiedRunner,
                pestMainSha256 = PEST_CONTROL_V10_HASH,
                grixisMainSha256 = PEST_GRIXIS_MAIN_SHA256,
                assignmentSchemaSha256 = schema.schemaSha256,
                classification = PEST_GRIXIS_DISABLED_SYNTHETIC_PROVENANCE_CLASSIFICATION,
            )
        }
        if (rows.size != PEST_GRIXIS_SMOKE_GAMES) errors += "synthetic provenance row count mismatch"
        if (rows.any { it.protocolId != PEST_GRIXIS_PREBOARD_PROTOCOL_ID }) {
            errors += "synthetic provenance protocol mismatch"
        }
        if (rows.any { it.blockId != PEST_GRIXIS_SMOKE_BLOCK_ID }) {
            errors += "synthetic provenance block mismatch"
        }
        if (rows.any { it.pestMainSha256 != PEST_CONTROL_V10_HASH }) {
            errors += "synthetic provenance Pest deck mismatch"
        }
        if (rows.any { it.grixisMainSha256 != PEST_GRIXIS_MAIN_SHA256 }) {
            errors += "synthetic provenance Grixis deck mismatch"
        }

        val proofBytes = buildList {
            add("pest-control-tier-one-grixis-disabled-synthetic-provenance-v1")
            add("assignmentSchemaSha256=$PEST_GRIXIS_DISABLED_ASSIGNMENT_SCHEMA_SHA256")
            add("protocolId=$PEST_GRIXIS_PREBOARD_PROTOCOL_ID")
            add("blockId=$PEST_GRIXIS_SMOKE_BLOCK_ID")
            add("qualifiedRunner=$qualifiedRunner")
            add("pestMainSha256=$PEST_CONTROL_V10_HASH")
            add("grixisMainSha256=$PEST_GRIXIS_MAIN_SHA256")
            add("classification=$PEST_GRIXIS_DISABLED_SYNTHETIC_PROVENANCE_CLASSIFICATION")
            rows.forEach { provenance ->
                val row = provenance.schemaRow
                add(
                    "${row.gameNumber}|${row.syntheticSlotLabel}|${row.pestSeat.name}|" +
                        "${row.grixisSeat.name}|${row.startingDeck.name}",
                )
            }
            add("provenanceRows=4")
            add("vectorIdentityPresent=false")
            add("numericEntropyFields=0")
            add("officialAssignments=0")
            add("officialSeedsGenerated=0")
            add("officialGamesInitialized=0")
            add("submittedActions=0")
            add("outcomeExposure=0")
        }.joinToString("\n", postfix = "\n").toByteArray()
        val provenanceSha256 = sha256(proofBytes)
        if (provenanceSha256 != PEST_GRIXIS_DISABLED_SYNTHETIC_PROVENANCE_SHA256) {
            errors += "disabled synthetic provenance hash mismatch"
        }
        return GrixisDisabledSyntheticProvenanceInspection(
            errors = errors.distinct(),
            provenanceSha256 = provenanceSha256,
            rows = rows,
        )
    }
}
