package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PestControlTierOneGrixisReplicationExecutionHarnessTest : FunSpec({
    test("replication cell plan is exactly balanced across twelve ordered games") {
        val cells = PestControlTierOneGrixisReplicationExecutionInputLoader.replicationCells()
        cells.size shouldBe 12
        cells.map { it.gameNumber } shouldBe (1..12).toList()
        cells.count { it.startingDeck == GrixisStartingDeck.PEST_CONTROL } shouldBe 6
        cells.count { it.startingDeck == GrixisStartingDeck.GRIXIS_AFFINITY } shouldBe 6
        cells.count { it.pestSeat == PestSeat.SEAT_ZERO } shouldBe 6
        cells.count { it.pestSeat == PestSeat.SEAT_ONE } shouldBe 6
        cells.groupingBy { it.pestSeat to it.startingDeck }.eachCount().values.toSet() shouldBe setOf(3)
    }

    test("synthetic twelve-game coordinator consumes every assignment once in order") {
        val cells = PestControlTierOneGrixisReplicationExecutionInputLoader.replicationCells()
        val seeds = (1L..12L).map { 9_990_000L + it }
        val assignments = cells.mapIndexed { index, cell ->
            val seed = seeds[index]
            GrixisSmokeAssignment(
                gameNumber = cell.gameNumber,
                seed = seed,
                seedHex = "0x${seed.toULong().toString(16).padStart(16, '0')}",
                pestSeat = cell.pestSeat,
                grixisSeat = if (cell.pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO,
                startingDeck = cell.startingDeck,
            )
        }
        val input = GrixisReplicationExecutionInput(
            GrixisSmokeVectorIdentity(
                freezeCommit = "5234db81bc87b6061bc3dfb891544205e66acc93",
                orderedVectorSha256 = PEST_GRIXIS_REPLICATION_VECTOR_SHA256,
                assignmentCsvSha256 = PEST_GRIXIS_REPLICATION_ASSIGNMENTS_SHA256,
                freezeManifestSha256 = PEST_GRIXIS_REPLICATION_MANIFEST_SHA256,
            ),
            seeds,
            assignments,
        )
        val attempted = mutableListOf<Int>()
        val recorded = mutableListOf<Int>()
        val outcome = PestControlTierOneGrixisReplicationExecutionCoordinator(
            input,
            persistAttemptBeforeInitialization = { attempted += it.gameNumber },
            persistCompletedGame = { assignment, _ -> recorded += assignment.gameNumber },
        ).execute { assignment ->
            """{"gameNumber":${assignment.gameNumber},"terminal":true}""".toByteArray()
        }
        outcome.disposition shouldBe GrixisCoordinatorDisposition.VALIDATED
        attempted shouldBe (1..12).toList()
        recorded shouldBe (1..12).toList()
        outcome.attempts.map { it.gameNumber } shouldBe (1..12).toList()
        outcome.recordedGames shouldBe (1..12).toList()
        outcome.perGameRaw.size shouldBe 12
    }

    test("synthetic coordinator fails closed and does not continue after a game failure") {
        val cells = PestControlTierOneGrixisReplicationExecutionInputLoader.replicationCells()
        val seeds = (1L..12L).map { 9_991_000L + it }
        val assignments = cells.mapIndexed { index, cell ->
            val seed = seeds[index]
            GrixisSmokeAssignment(
                cell.gameNumber, seed,
                "0x${seed.toULong().toString(16).padStart(16, '0')}",
                cell.pestSeat,
                if (cell.pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO,
                cell.startingDeck,
            )
        }
        val input = GrixisReplicationExecutionInput(
            GrixisSmokeVectorIdentity(
                "5234db81bc87b6061bc3dfb891544205e66acc93",
                PEST_GRIXIS_REPLICATION_VECTOR_SHA256,
                PEST_GRIXIS_REPLICATION_ASSIGNMENTS_SHA256,
                PEST_GRIXIS_REPLICATION_MANIFEST_SHA256,
            ), seeds, assignments,
        )
        val attempted = mutableListOf<Int>()
        val recorded = mutableListOf<Int>()
        val outcome = PestControlTierOneGrixisReplicationExecutionCoordinator(
            input,
            { attempted += it.gameNumber },
            { assignment, _ -> recorded += assignment.gameNumber },
        ).execute { assignment ->
            if (assignment.gameNumber == 5) error("synthetic stop")
            "{}".toByteArray()
        }
        outcome.disposition shouldBe GrixisCoordinatorDisposition.REJECTED
        attempted shouldBe listOf(1,2,3,4,5)
        recorded shouldBe listOf(1,2,3,4)
        outcome.failure shouldBe "synthetic stop"
    }
})
