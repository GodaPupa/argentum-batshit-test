package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry

const val PEST_GRIXIS_DISABLED_ARTIFACT_ENVELOPE_SHA256 =
    "76788177bdb6263a6b0f29ffb2390ac089c678b65ac3e78f3dbb261eea8e2edd"
const val PEST_GRIXIS_DISABLED_ARTIFACT_ENVELOPE_STATUS =
    "ARTIFACT_DIGESTS_VERIFIED_BYTES_NOT_LOADED_EXECUTION_NOT_AUTHORIZED"
const val PEST_GRIXIS_FROZEN_CHECKSUM_INVENTORY_SHA256 =
    "96ec2a8e74858df3d0bf2cd5adf4703b3f2fb8b5a69c2bbd40899bcc4bf347ac"

data class GrixisFrozenArtifactDigestEnvelope(
    val workflowRunId: Long = 35556631787,
    val artifactId: Long = 10620940806,
    val orderedVectorSha256: String = PEST_GRIXIS_FROZEN_VECTOR_SHA256,
    val assignmentCsvSha256: String = PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256,
    val freezeManifestSha256: String = PEST_GRIXIS_FROZEN_MANIFEST_SHA256,
    val quarantinedVectorSha256: String = PEST_GRIXIS_FROZEN_QUARANTINE_SHA256,
    val checksumInventorySha256: String = PEST_GRIXIS_FROZEN_CHECKSUM_INVENTORY_SHA256,
    val artifactArchiveSha256: String = PEST_GRIXIS_FROZEN_ARCHIVE_SHA256,
)

data class GrixisDisabledArtifactEnvelopeInspection(
    val errors: List<String>,
    val envelopeSha256: String,
    val status: String,
    val artifactBytesLoaded: Boolean = false,
    val seedValuesVisible: Int = 0,
    val officialAssignments: Int = PEST_GRIXIS_SMOKE_GAMES,
    val officialSeedsGenerated: Int = PEST_GRIXIS_SMOKE_GAMES,
    val initializerEnabled: Boolean = false,
    val runnerEnabled: Boolean = false,
    val officialGamesAuthorized: Int = 0,
    val officialGamesInitialized: Int = 0,
    val submittedActions: Int = 0,
    val artifactsWithOutcomes: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val green: Boolean get() = errors.isEmpty()
}

/** Verifies only frozen artifact digests. It cannot accept, decode, or expose artifact bytes. */
object PestControlTierOneGrixisDisabledArtifactEnvelope {
    fun inspect(
        registry: CardRegistry,
        envelope: GrixisFrozenArtifactDigestEnvelope = GrixisFrozenArtifactDigestEnvelope(),
    ): GrixisDisabledArtifactEnvelopeInspection {
        val errors = mutableListOf<String>()
        val binding = PestControlTierOneGrixisFrozenVectorBinding.inspect(registry)
        if (!binding.green) errors += "frozen vector binding is not green"
        if (binding.bindingSha256 != PEST_GRIXIS_FROZEN_VECTOR_BINDING_SHA256) {
            errors += "frozen vector binding proof mismatch"
        }
        val expected = GrixisFrozenArtifactDigestEnvelope()
        if (envelope != expected) errors += "frozen artifact digest envelope mismatch"

        val proofBytes = listOf(
            "pest-control-tier-one-grixis-disabled-artifact-envelope-v1",
            "status=$PEST_GRIXIS_DISABLED_ARTIFACT_ENVELOPE_STATUS",
            "bindingSha256=${binding.bindingSha256}",
            "workflowRunId=${envelope.workflowRunId}",
            "artifactId=${envelope.artifactId}",
            "orderedVectorSha256=${envelope.orderedVectorSha256}",
            "assignmentCsvSha256=${envelope.assignmentCsvSha256}",
            "freezeManifestSha256=${envelope.freezeManifestSha256}",
            "quarantinedVectorSha256=${envelope.quarantinedVectorSha256}",
            "checksumInventorySha256=${envelope.checksumInventorySha256}",
            "artifactArchiveSha256=${envelope.artifactArchiveSha256}",
            "artifactBytesLoaded=false",
            "seedValuesVisible=0",
            "officialAssignments=4",
            "officialSeedsGenerated=4",
            "initializerEnabled=false",
            "runnerEnabled=false",
            "officialGamesAuthorized=0",
            "officialGamesInitialized=0",
            "submittedActions=0",
            "artifactsWithOutcomes=0",
            "outcomeExposure=0",
        ).joinToString("\n", postfix = "\n").toByteArray()
        val envelopeSha256 = sha256(proofBytes)
        if (envelopeSha256 != PEST_GRIXIS_DISABLED_ARTIFACT_ENVELOPE_SHA256) {
            errors += "disabled artifact envelope hash mismatch"
        }
        return GrixisDisabledArtifactEnvelopeInspection(
            errors = errors.distinct(),
            envelopeSha256 = envelopeSha256,
            status = PEST_GRIXIS_DISABLED_ARTIFACT_ENVELOPE_STATUS,
        )
    }
}
