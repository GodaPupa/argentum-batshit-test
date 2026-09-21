package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.GrixisSmokeAssignment
import com.wingedsheep.gym.matchup.GrixisSmokeVectorIdentity
import com.wingedsheep.gym.matchup.GrixisStartingDeck
import com.wingedsheep.gym.matchup.PEST_CONTROL_V10_HASH
import com.wingedsheep.gym.matchup.PEST_GRIXIS_COMPLETE_75_SHA256
import com.wingedsheep.gym.matchup.PEST_GRIXIS_MAIN_SHA256
import com.wingedsheep.gym.matchup.PEST_GRIXIS_READINESS_COMMIT
import com.wingedsheep.gym.matchup.PEST_GRIXIS_SMOKE_BLOCK_ID
import com.wingedsheep.gym.matchup.PestControlTierOneGrixisGameAdapter
import com.wingedsheep.gym.matchup.PestSeat
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val SYNTHETIC_GRIXIS_SOURCE = "1111111111111111111111111111111111111111"
private val SYNTHETIC_GRIXIS_VECTOR = GrixisSmokeVectorIdentity(
    freezeCommit = "2222222222222222222222222222222222222222",
    orderedVectorSha256 = "3".repeat(64),
    assignmentCsvSha256 = "4".repeat(64),
    freezeManifestSha256 = "5".repeat(64),
)

/** Provenance mapping only; this test has no registry, environment, or game initializer. */
class PestControlTierOneGrixisGameAdapterTest : FunSpec({
    test("synthetic assignment maps to complete immutable Grixis provenance") {
        val assignment = syntheticGrixisAssignment()
        val provenance = PestControlTierOneGrixisGameAdapter.provenance(
            assignment,
            SYNTHETIC_GRIXIS_VECTOR,
            SYNTHETIC_GRIXIS_SOURCE,
        )

        provenance.blockId shouldBe PEST_GRIXIS_SMOKE_BLOCK_ID
        provenance.sourceCommit shouldBe SYNTHETIC_GRIXIS_SOURCE
        provenance.acceptedReadinessCommit shouldBe PEST_GRIXIS_READINESS_COMMIT
        provenance.freezeCommit shouldBe SYNTHETIC_GRIXIS_VECTOR.freezeCommit
        provenance.orderedVectorSha256 shouldBe SYNTHETIC_GRIXIS_VECTOR.orderedVectorSha256
        provenance.assignmentCsvSha256 shouldBe SYNTHETIC_GRIXIS_VECTOR.assignmentCsvSha256
        provenance.freezeManifestSha256 shouldBe SYNTHETIC_GRIXIS_VECTOR.freezeManifestSha256
        provenance.pestMainSha256 shouldBe PEST_CONTROL_V10_HASH
        provenance.grixisMainSha256 shouldBe PEST_GRIXIS_MAIN_SHA256
        provenance.grixisComplete75Sha256 shouldBe PEST_GRIXIS_COMPLETE_75_SHA256
        provenance.gameNumber shouldBe assignment.gameNumber
        provenance.seed shouldBe assignment.seed
        provenance.classification shouldBe "NONEXPERIMENTAL_SMOKE_VECTOR"
    }

    test("adapter rejects malformed identity seat cell and seed before initialization exists") {
        val assignment = syntheticGrixisAssignment()
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneGrixisGameAdapter.provenance(
                assignment,
                SYNTHETIC_GRIXIS_VECTOR,
                "short",
            )
        }
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneGrixisGameAdapter.provenance(
                assignment.copy(seedHex = "0x0"),
                SYNTHETIC_GRIXIS_VECTOR,
                SYNTHETIC_GRIXIS_SOURCE,
            )
        }
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneGrixisGameAdapter.provenance(
                assignment.copy(grixisSeat = PestSeat.SEAT_ZERO),
                SYNTHETIC_GRIXIS_VECTOR,
                SYNTHETIC_GRIXIS_SOURCE,
            )
        }
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneGrixisGameAdapter.provenance(
                assignment.copy(startingDeck = GrixisStartingDeck.GRIXIS_AFFINITY),
                SYNTHETIC_GRIXIS_VECTOR,
                SYNTHETIC_GRIXIS_SOURCE,
            )
        }
    }
})

private fun syntheticGrixisAssignment(): GrixisSmokeAssignment {
    val seed = 9_000_001L
    return GrixisSmokeAssignment(
        gameNumber = 1,
        seed = seed,
        seedHex = "0x${seed.toULong().toString(16).padStart(16, '0')}",
        pestSeat = PestSeat.SEAT_ZERO,
        grixisSeat = PestSeat.SEAT_ONE,
        startingDeck = GrixisStartingDeck.PEST_CONTROL,
    )
}
