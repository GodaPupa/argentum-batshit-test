package com.wingedsheep.gym.matchup

import kotlinx.serialization.Serializable

data class MonoBlueTerrorSmokeAssignment(
    val gameNumber: Int,
    val seed: Long,
    val seedHex: String,
    val pestSeat: PestSeat,
    val terrorSeat: PestSeat,
    val startingDeck: MonoBlueTerrorStartingDeck,
)

@Serializable
data class MonoBlueTerrorSmokeProvenance(
    val protocolId: String,
    val blockId: String,
    val sourceCommit: String,
    val acceptedReadinessCommit: String,
    val freezeCommit: String,
    val orderedVectorSha256: String,
    val assignmentCsvSha256: String,
    val freezeManifestSha256: String,
    val pestMainSha256: String,
    val terrorMainSha256: String,
    val terrorComplete75Sha256: String,
    val gameNumber: Int,
    val seed: Long,
    val seedHex: String,
    val pestSeat: PestSeat,
    val terrorSeat: PestSeat,
    val startingDeck: MonoBlueTerrorStartingDeck,
    val classification: String = "NONEXPERIMENTAL_SMOKE_VECTOR",
)

/**
 * Validates and maps a future frozen assignment to immutable provenance. Deliberately exposes no
 * game initializer: provenance can be tested before any code is capable of consuming entropy.
 */
object PestControlTierOneMonoBlueTerrorGameAdapter {
    fun provenance(
        assignment: MonoBlueTerrorSmokeAssignment,
        vectorIdentity: MonoBlueTerrorSmokeVectorIdentity,
        sourceCommit: String,
    ): MonoBlueTerrorSmokeProvenance {
        require(sourceCommit.isLowerHex(40))
        require(vectorIdentity.freezeCommit.isLowerHex(40))
        require(vectorIdentity.orderedVectorSha256.isLowerHex(64))
        require(vectorIdentity.assignmentCsvSha256.isLowerHex(64))
        require(vectorIdentity.freezeManifestSha256.isLowerHex(64))
        require(assignment.gameNumber in 1..PEST_MONO_BLUE_TERROR_SMOKE_GAMES)
        require(assignment.seed != 0L)
        require(assignment.seedHex == assignment.seed.toSeedHex())
        require(assignment.pestSeat != assignment.terrorSeat)
        require(
            (assignment.pestSeat == PestSeat.SEAT_ZERO && assignment.terrorSeat == PestSeat.SEAT_ONE) ||
                (assignment.pestSeat == PestSeat.SEAT_ONE && assignment.terrorSeat == PestSeat.SEAT_ZERO)
        )
        val expectedCell = PestControlTierOneMonoBlueTerrorSmokeHarness.cellTemplate()
            .single { it.gameNumber == assignment.gameNumber }
        require(assignment.pestSeat == expectedCell.pestSeat)
        require(assignment.startingDeck == expectedCell.startingDeck)

        return MonoBlueTerrorSmokeProvenance(
            protocolId = PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID,
            blockId = PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID,
            sourceCommit = sourceCommit,
            acceptedReadinessCommit = PEST_MONO_BLUE_TERROR_READINESS_COMMIT,
            freezeCommit = vectorIdentity.freezeCommit,
            orderedVectorSha256 = vectorIdentity.orderedVectorSha256,
            assignmentCsvSha256 = vectorIdentity.assignmentCsvSha256,
            freezeManifestSha256 = vectorIdentity.freezeManifestSha256,
            pestMainSha256 = PEST_CONTROL_V10_HASH,
            terrorMainSha256 = PEST_MONO_BLUE_TERROR_MAIN_SHA256,
            terrorComplete75Sha256 = PEST_MONO_BLUE_TERROR_COMPLETE_75_SHA256,
            gameNumber = assignment.gameNumber,
            seed = assignment.seed,
            seedHex = assignment.seedHex,
            pestSeat = assignment.pestSeat,
            terrorSeat = assignment.terrorSeat,
            startingDeck = assignment.startingDeck,
        )
    }

    private fun String.isLowerHex(length: Int): Boolean =
        this.length == length && all { it in '0'..'9' || it in 'a'..'f' }

    private fun Long.toSeedHex(): String = "0x${toULong().toString(16).padStart(16, '0')}"
}
