package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PestControlTierOneGrixisReplicationContinuationPlanTest : FunSpec({
    fun syntheticInput(): GrixisReplicationExecutionInput {
        val cells = PestControlTierOneGrixisReplicationExecutionInputLoader.replicationCells()
        val seeds = (1L..12L).map { 7_770_000L + it }
        val assignments = cells.mapIndexed { index, cell ->
            val seed = seeds[index]
            GrixisSmokeAssignment(
                gameNumber = index + 1,
                seed = seed,
                seedHex = "0x${seed.toULong().toString(16).padStart(16, '0')}",
                pestSeat = cell.pestSeat,
                grixisSeat = if (cell.pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO,
                startingDeck = cell.startingDeck,
            )
        }
        return GrixisReplicationExecutionInput(
            GrixisSmokeVectorIdentity(
                "5234db81bc87b6061bc3dfb891544205e66acc93",
                PEST_GRIXIS_REPLICATION_VECTOR_SHA256,
                PEST_GRIXIS_REPLICATION_ASSIGNMENTS_SHA256,
                PEST_GRIXIS_REPLICATION_MANIFEST_SHA256,
            ),
            seeds,
            assignments,
        )
    }

    test("continuation is exactly untouched Games 2 through 12") {
        val suffix = PestControlTierOneGrixisReplicationContinuationPlan.untouchedSuffix(syntheticInput())
        suffix.size shouldBe 11
        suffix.map { it.gameNumber } shouldBe (2..12).toList()
        suffix.none { it.gameNumber == 1 } shouldBe true
    }

    test("continuation preserves actual unbalanced suffix denominators") {
        val suffix = PestControlTierOneGrixisReplicationContinuationPlan.untouchedSuffix(syntheticInput())
        suffix.count { it.startingDeck == GrixisStartingDeck.PEST_CONTROL } shouldBe 5
        suffix.count { it.startingDeck == GrixisStartingDeck.GRIXIS_AFFINITY } shouldBe 6
        suffix.count { it.pestSeat == PestSeat.SEAT_ZERO } shouldBe 5
        suffix.count { it.pestSeat == PestSeat.SEAT_ONE } shouldBe 6
    }

    test("classification is salvage continuation and not clean replication") {
        PEST_GRIXIS_CONTINUATION_CLASSIFICATION shouldBe "SALVAGE_CONTINUATION_11"
        PEST_GRIXIS_CONTINUATION_GAMES shouldBe 11
    }
})
