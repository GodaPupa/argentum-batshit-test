package com.wingedsheep.gym.matchup

/**
 * Replication-specific provenance mapping.
 *
 * The original smoke adapter is intentionally limited to Games 1-4. This mapper preserves the same
 * deck/protocol provenance fields while accepting only untouched replication Games 2-12.
 */
object PestControlTierOneGrixisReplicationGameAdapter {
    fun provenance(
        assignment: GrixisSmokeAssignment,
        vectorIdentity: GrixisSmokeVectorIdentity,
        sourceCommit: String,
    ): GrixisSmokeProvenance {
        require(sourceCommit.length == 40 && sourceCommit.all { it in '0'..'9' || it in 'a'..'f' })
        require(vectorIdentity.freezeCommit == "5234db81bc87b6061bc3dfb891544205e66acc93")
        require(vectorIdentity.orderedVectorSha256 == PEST_GRIXIS_REPLICATION_VECTOR_SHA256)
        require(vectorIdentity.assignmentCsvSha256 == PEST_GRIXIS_REPLICATION_ASSIGNMENTS_SHA256)
        require(vectorIdentity.freezeManifestSha256 == PEST_GRIXIS_REPLICATION_MANIFEST_SHA256)
        require(assignment.gameNumber in 2..12)
        require(assignment.seed != 0L)
        require(assignment.seedHex == "0x${assignment.seed.toULong().toString(16).padStart(16, '0')}")
        require(assignment.pestSeat != assignment.grixisSeat)

        val expected = PestControlTierOneGrixisReplicationExecutionInputLoader
            .replicationCells()[assignment.gameNumber - 1]
        require(assignment.pestSeat == expected.pestSeat)
        require(assignment.startingDeck == expected.startingDeck)

        return GrixisSmokeProvenance(
            protocolId = PEST_GRIXIS_PREBOARD_PROTOCOL_ID,
            blockId = PEST_GRIXIS_REPLICATION_BLOCK_ID,
            sourceCommit = sourceCommit,
            acceptedReadinessCommit = PEST_GRIXIS_READINESS_COMMIT,
            freezeCommit = vectorIdentity.freezeCommit,
            orderedVectorSha256 = vectorIdentity.orderedVectorSha256,
            assignmentCsvSha256 = vectorIdentity.assignmentCsvSha256,
            freezeManifestSha256 = vectorIdentity.freezeManifestSha256,
            pestMainSha256 = PEST_CONTROL_V10_HASH,
            grixisMainSha256 = PEST_GRIXIS_MAIN_SHA256,
            grixisComplete75Sha256 = PEST_GRIXIS_COMPLETE_75_SHA256,
            gameNumber = assignment.gameNumber,
            seed = assignment.seed,
            seedHex = assignment.seedHex,
            pestSeat = assignment.pestSeat,
            grixisSeat = assignment.grixisSeat,
            startingDeck = assignment.startingDeck,
            classification = "TIER_ONE_GRIXIS_REPLICATION_12",
        )
    }
}
