package com.wingedsheep.gym.matchup

import kotlinx.serialization.Serializable

data class GrixisSmokeAssignment(
    val gameNumber: Int,
    val seed: Long,
    val seedHex: String,
    val pestSeat: PestSeat,
    val grixisSeat: PestSeat,
    val startingDeck: GrixisStartingDeck,
)

@Serializable
data class GrixisSmokeProvenance(
    val protocolId: String,
    val blockId: String,
    val sourceCommit: String,
    val acceptedReadinessCommit: String,
    val freezeCommit: String,
    val orderedVectorSha256: String,
    val assignmentCsvSha256: String,
    val freezeManifestSha256: String,
    val pestMainSha256: String,
    val grixisMainSha256: String,
    val grixisComplete75Sha256: String,
    val gameNumber: Int,
    val seed: Long,
    val seedHex: String,
    val pestSeat: PestSeat,
    val grixisSeat: PestSeat,
    val startingDeck: GrixisStartingDeck,
    val classification: String = "NONEXPERIMENTAL_SMOKE_VECTOR",
)

/**
 * Validates and maps a future frozen assignment to immutable provenance. Deliberately exposes no
 * game initializer: provenance can be tested before any code is capable of consuming entropy.
 */
object PestControlTierOneGrixisGameAdapter {
    fun provenance(
        assignment: GrixisSmokeAssignment,
        vectorIdentity: GrixisSmokeVectorIdentity,
        sourceCommit: String,
    ): GrixisSmokeProvenance {
        require(sourceCommit.isLowerHex(40))
        require(vectorIdentity.freezeCommit.isLowerHex(40))
        require(vectorIdentity.orderedVectorSha256.isLowerHex(64))
        require(vectorIdentity.assignmentCsvSha256.isLowerHex(64))
        require(vectorIdentity.freezeManifestSha256.isLowerHex(64))
        require(assignment.gameNumber in 1..PEST_GRIXIS_SMOKE_GAMES)
        require(assignment.seed != 0L)
        require(assignment.seedHex == assignment.seed.toSeedHex())
        require(assignment.pestSeat != assignment.grixisSeat)
        require(
            (assignment.pestSeat == PestSeat.SEAT_ZERO && assignment.grixisSeat == PestSeat.SEAT_ONE) ||
                (assignment.pestSeat == PestSeat.SEAT_ONE && assignment.grixisSeat == PestSeat.SEAT_ZERO)
        )
        val expectedCell = PestControlTierOneGrixisSmokeHarness.cellTemplate()
            .single { it.gameNumber == assignment.gameNumber }
        require(assignment.pestSeat == expectedCell.pestSeat)
        require(assignment.startingDeck == expectedCell.startingDeck)

        return GrixisSmokeProvenance(
            protocolId = PEST_GRIXIS_PREBOARD_PROTOCOL_ID,
            blockId = PEST_GRIXIS_SMOKE_BLOCK_ID,
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
        )
    }

    private fun String.isLowerHex(length: Int): Boolean =
        this.length == length && all { it in '0'..'9' || it in 'a'..'f' }

    private fun Long.toSeedHex(): String = "0x${toULong().toString(16).padStart(16, '0')}"
}
