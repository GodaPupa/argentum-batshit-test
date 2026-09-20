package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val SYNTHETIC_EXECUTION_COMMIT = "1111111111111111111111111111111111111111"

class PestControlV2OfficialGameAdapterTest : FunSpec({
    test("official assignment maps to complete frozen experimental provenance without initialization") {
        val assignment = syntheticOfficialAssignment()
        val provenance = PestControlV2OfficialGameAdapter.provenance(assignment, SYNTHETIC_EXECUTION_COMMIT)

        provenance.protocolId shouldBe PEST_V2_OFFICIAL_PROTOCOL
        provenance.blockId shouldBe PEST_V2_OFFICIAL_BLOCK
        provenance.sourceCommit shouldBe SYNTHETIC_EXECUTION_COMMIT
        provenance.executionCommit shouldBe SYNTHETIC_EXECUTION_COMMIT
        provenance.freezeCommit shouldBe PEST_V2_FREEZE_COMMIT
        provenance.orderedVectorSha256 shouldBe PEST_V2_ORDERED_VECTOR_SHA256
        provenance.assignmentCsvSha256 shouldBe PEST_V2_ASSIGNMENT_CSV_SHA256
        provenance.freezeManifestSha256 shouldBe PEST_V2_FREEZE_MANIFEST_SHA256
        provenance.entropyClassification shouldBe "FROZEN_EXPERIMENTAL_VECTOR"
        provenance.gameNumber shouldBe assignment.game
        provenance.seedDecimal shouldBe assignment.seed
        provenance.seedHex shouldBe assignment.seedHex
        provenance.pestSeat shouldBe assignment.pestSeat
        provenance.startingDeck shouldBe assignment.starter
    }

    test("adapter rejects malformed commit seed identity and position before initialization") {
        val assignment = syntheticOfficialAssignment()
        shouldThrow<IllegalArgumentException> {
            PestControlV2OfficialGameAdapter.provenance(assignment, "short")
        }
        shouldThrow<IllegalArgumentException> {
            PestControlV2OfficialGameAdapter.provenance(assignment.copy(seedHex = "0x0"), SYNTHETIC_EXECUTION_COMMIT)
        }
        shouldThrow<IllegalArgumentException> {
            PestControlV2OfficialGameAdapter.provenance(assignment.copy(game = 11), SYNTHETIC_EXECUTION_COMMIT)
        }
    }

    test("accepted freeze source identities remain pinned") {
        PEST_V2_FREEZE_RUN_ID shouldBe 35510807996L
        PEST_V2_FREEZE_ARTIFACT_ID shouldBe 10605531402L
        PEST_V2_FREEZE_ARTIFACT_DIGEST shouldBe
            "sha256:9b7e61ddd92cf008c82368d4d5f897fc91815fdf07e6f0d4ab04b651db313731"
        PEST_V2_FREEZE_MANIFEST_SHA256 shouldBe
            "8dbff485b5adfe0b2d9d683668958494c055ab40de0c7335f923448cee50b450"
    }
})

private fun syntheticOfficialAssignment(): V2OfficialAssignment {
    val seed = 5_000_001L
    return V2OfficialAssignment(
        game = 1,
        seed = seed,
        seedHex = "0x${seed.toULong().toString(16).padStart(16, '0')}",
        pestSeat = PestSeat.SEAT_ZERO,
        redSeat = PestSeat.SEAT_ONE,
        starter = StartingDeck.PEST_CONTROL,
        playDraw = "PLAY",
    )
}
