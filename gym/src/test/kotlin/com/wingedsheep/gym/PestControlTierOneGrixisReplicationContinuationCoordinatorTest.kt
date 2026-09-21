package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PestControlTierOneGrixisReplicationContinuationCoordinatorTest : FunSpec({
    fun input(): GrixisReplicationExecutionInput {
        val cells = PestControlTierOneGrixisReplicationExecutionInputLoader.replicationCells()
        val seeds = (1L..12L).map { 6_660_000L + it }
        val assignments = cells.mapIndexed { index, cell ->
            val seed = seeds[index]
            GrixisSmokeAssignment(
                index + 1, seed,
                "0x${seed.toULong().toString(16).padStart(16, '0')}",
                cell.pestSeat,
                if (cell.pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO,
                cell.startingDeck,
            )
        }
        return GrixisReplicationExecutionInput(
            GrixisSmokeVectorIdentity(
                "5234db81bc87b6061bc3dfb891544205e66acc93",
                PEST_GRIXIS_REPLICATION_VECTOR_SHA256,
                PEST_GRIXIS_REPLICATION_ASSIGNMENTS_SHA256,
                PEST_GRIXIS_REPLICATION_MANIFEST_SHA256,
            ), seeds, assignments,
        )
    }

    test("continuation consumes only Games 2 through 12 exactly once") {
        val attempted = mutableListOf<Int>()
        val recorded = mutableListOf<Int>()
        val outcome = PestControlTierOneGrixisReplicationContinuationCoordinator(
            input(),
            { attempted += it.gameNumber },
            { assignment, _ -> recorded += assignment.gameNumber },
        ).execute { assignment -> """{"game":${assignment.gameNumber}}""".toByteArray() }

        outcome.disposition shouldBe GrixisCoordinatorDisposition.VALIDATED
        attempted shouldBe (2..12).toList()
        recorded shouldBe (2..12).toList()
        outcome.attempts.map { it.gameNumber } shouldBe (2..12).toList()
        outcome.recordedGames shouldBe (2..12).toList()
        outcome.perGameRaw.size shouldBe 11
        attempted.contains(1) shouldBe false
    }

    test("continuation fails closed and never reaches later games after failure") {
        val attempted = mutableListOf<Int>()
        val recorded = mutableListOf<Int>()
        val outcome = PestControlTierOneGrixisReplicationContinuationCoordinator(
            input(),
            { attempted += it.gameNumber },
            { assignment, _ -> recorded += assignment.gameNumber },
        ).execute { assignment ->
            if (assignment.gameNumber == 6) error("synthetic continuation stop")
            "{}".toByteArray()
        }

        outcome.disposition shouldBe GrixisCoordinatorDisposition.REJECTED
        attempted shouldBe listOf(2,3,4,5,6)
        recorded shouldBe listOf(2,3,4,5)
        outcome.failure shouldBe "synthetic continuation stop"
        attempted.contains(1) shouldBe false
    }

    test("continuation plan retains actual suffix balance") {
        val suffix = PestControlTierOneGrixisReplicationContinuationPlan.untouchedSuffix(input())
        suffix.count { it.startingDeck == GrixisStartingDeck.PEST_CONTROL } shouldBe 5
        suffix.count { it.startingDeck == GrixisStartingDeck.GRIXIS_AFFINITY } shouldBe 6
        suffix.count { it.pestSeat == PestSeat.SEAT_ZERO } shouldBe 5
        suffix.count { it.pestSeat == PestSeat.SEAT_ONE } shouldBe 6
    }
})
