package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.MonoBlueTerrorSmokeAssignment
import com.wingedsheep.gym.matchup.MonoBlueTerrorSmokeVectorIdentity
import com.wingedsheep.gym.matchup.MonoBlueTerrorStartingDeck
import com.wingedsheep.gym.matchup.PEST_CONTROL_V10_HASH
import com.wingedsheep.gym.matchup.PEST_MONO_BLUE_TERROR_COMPLETE_75_SHA256
import com.wingedsheep.gym.matchup.PEST_MONO_BLUE_TERROR_MAIN_SHA256
import com.wingedsheep.gym.matchup.PEST_MONO_BLUE_TERROR_READINESS_COMMIT
import com.wingedsheep.gym.matchup.PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID
import com.wingedsheep.gym.matchup.PestControlTierOneMonoBlueTerrorGameAdapter
import com.wingedsheep.gym.matchup.PestSeat
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val SYNTHETIC_TERROR_SOURCE = "1111111111111111111111111111111111111111"
private val SYNTHETIC_TERROR_VECTOR = MonoBlueTerrorSmokeVectorIdentity(
    freezeCommit = "2222222222222222222222222222222222222222",
    orderedVectorSha256 = "3".repeat(64),
    assignmentCsvSha256 = "4".repeat(64),
    freezeManifestSha256 = "5".repeat(64),
)

/** Provenance mapping only; this test has no registry, environment, or game initializer. */
class PestControlTierOneMonoBlueTerrorGameAdapterTest : FunSpec({
    test("synthetic assignment maps to complete immutable Mono-Blue Terror provenance") {
        val assignment = syntheticTerrorAssignment()
        val provenance = PestControlTierOneMonoBlueTerrorGameAdapter.provenance(
            assignment,
            SYNTHETIC_TERROR_VECTOR,
            SYNTHETIC_TERROR_SOURCE,
        )

        provenance.blockId shouldBe PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID
        provenance.sourceCommit shouldBe SYNTHETIC_TERROR_SOURCE
        provenance.acceptedReadinessCommit shouldBe PEST_MONO_BLUE_TERROR_READINESS_COMMIT
        provenance.freezeCommit shouldBe SYNTHETIC_TERROR_VECTOR.freezeCommit
        provenance.orderedVectorSha256 shouldBe SYNTHETIC_TERROR_VECTOR.orderedVectorSha256
        provenance.assignmentCsvSha256 shouldBe SYNTHETIC_TERROR_VECTOR.assignmentCsvSha256
        provenance.freezeManifestSha256 shouldBe SYNTHETIC_TERROR_VECTOR.freezeManifestSha256
        provenance.pestMainSha256 shouldBe PEST_CONTROL_V10_HASH
        provenance.terrorMainSha256 shouldBe PEST_MONO_BLUE_TERROR_MAIN_SHA256
        provenance.terrorComplete75Sha256 shouldBe PEST_MONO_BLUE_TERROR_COMPLETE_75_SHA256
        provenance.gameNumber shouldBe assignment.gameNumber
        provenance.seed shouldBe assignment.seed
        provenance.classification shouldBe "NONEXPERIMENTAL_SMOKE_VECTOR"
    }

    test("adapter rejects malformed identity seat cell and seed before initialization exists") {
        val assignment = syntheticTerrorAssignment()
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneMonoBlueTerrorGameAdapter.provenance(
                assignment,
                SYNTHETIC_TERROR_VECTOR,
                "short",
            )
        }
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneMonoBlueTerrorGameAdapter.provenance(
                assignment.copy(seedHex = "0x0"),
                SYNTHETIC_TERROR_VECTOR,
                SYNTHETIC_TERROR_SOURCE,
            )
        }
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneMonoBlueTerrorGameAdapter.provenance(
                assignment.copy(terrorSeat = PestSeat.SEAT_ZERO),
                SYNTHETIC_TERROR_VECTOR,
                SYNTHETIC_TERROR_SOURCE,
            )
        }
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneMonoBlueTerrorGameAdapter.provenance(
                assignment.copy(startingDeck = MonoBlueTerrorStartingDeck.MONO_BLUE_TERROR),
                SYNTHETIC_TERROR_VECTOR,
                SYNTHETIC_TERROR_SOURCE,
            )
        }
    }
})

private fun syntheticTerrorAssignment(): MonoBlueTerrorSmokeAssignment {
    val seed = 9_200_001L
    return MonoBlueTerrorSmokeAssignment(
        gameNumber = 1,
        seed = seed,
        seedHex = "0x${seed.toULong().toString(16).padStart(16, '0')}",
        pestSeat = PestSeat.SEAT_ZERO,
        terrorSeat = PestSeat.SEAT_ONE,
        startingDeck = MonoBlueTerrorStartingDeck.PEST_CONTROL,
    )
}
