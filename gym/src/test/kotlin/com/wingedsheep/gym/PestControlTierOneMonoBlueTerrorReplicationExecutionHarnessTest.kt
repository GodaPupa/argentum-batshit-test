package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PestControlTierOneMonoBlueTerrorReplicationExecutionHarnessTest : FunSpec({
    test("replication cell plan is exactly balanced across twelve ordered games") {
        val cells = PestControlTierOneMonoBlueTerrorReplicationExecutionInputLoader.replicationCells()
        cells.size shouldBe 12
        cells.map { it.gameNumber } shouldBe (1..12).toList()
        cells.count { it.startingDeck == MonoBlueTerrorStartingDeck.PEST_CONTROL } shouldBe 6
        cells.count { it.startingDeck == MonoBlueTerrorStartingDeck.MONO_BLUE_TERROR } shouldBe 6
        cells.count { it.pestSeat == PestSeat.SEAT_ZERO } shouldBe 6
        cells.count { it.pestSeat == PestSeat.SEAT_ONE } shouldBe 6
        cells.groupingBy { it.pestSeat to it.startingDeck }.eachCount().values.toSet() shouldBe setOf(3)
    }

    test("synthetic twelve-game coordinator consumes every assignment exactly once in order") {
        val cells = PestControlTierOneMonoBlueTerrorReplicationExecutionInputLoader.replicationCells()
        val seeds = (1L..12L).map { 9_992_000L + it }
        val assignments = cells.mapIndexed { index, cell ->
            val seed = seeds[index]
            MonoBlueTerrorSmokeAssignment(
                gameNumber = cell.gameNumber,
                seed = seed,
                seedHex = terrorAssignmentSeedHex(seed),
                pestSeat = cell.pestSeat,
                terrorSeat = if (cell.pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO,
                startingDeck = cell.startingDeck,
            )
        }
        val input = MonoBlueTerrorReplicationExecutionInput(
            vectorIdentity = MonoBlueTerrorSmokeVectorIdentity(
                freezeCommit = PEST_MONO_BLUE_TERROR_REPLICATION_FREEZE_COMMIT,
                orderedVectorSha256 = PEST_MONO_BLUE_TERROR_REPLICATION_VECTOR_SHA256,
                assignmentCsvSha256 = PEST_MONO_BLUE_TERROR_REPLICATION_ASSIGNMENTS_SHA256,
                freezeManifestSha256 = PEST_MONO_BLUE_TERROR_REPLICATION_MANIFEST_SHA256,
            ),
            seeds = seeds,
            assignments = assignments,
        )

        val attempted = mutableListOf<Int>()
        val initialized = mutableListOf<Int>()
        val recorded = mutableListOf<Int>()

        val outcome = PestControlTierOneMonoBlueTerrorReplicationExecutionCoordinator(
            input = input,
            persistAttemptBeforeInitialization = { attempted += it.gameNumber },
            persistInitializationEntry = { initialized += it.gameNumber },
            persistCompletedGame = { assignment, _ -> recorded += assignment.gameNumber },
        ).execute { assignment ->
            """{"gameNumber":${assignment.gameNumber},"terminal":true}""".toByteArray()
        }

        outcome.disposition shouldBe MonoBlueTerrorCoordinatorDisposition.VALIDATED
        attempted shouldBe (1..12).toList()
        initialized shouldBe (1..12).toList()
        recorded shouldBe (1..12).toList()
        outcome.attempts.map { it.gameNumber } shouldBe (1..12).toList()
        outcome.initializedGames shouldBe (1..12).toList()
        outcome.recordedGames shouldBe (1..12).toList()
        outcome.perGameRaw.size shouldBe 12
    }

    test("synthetic coordinator fails closed and stops after a game-five failure") {
        val cells = PestControlTierOneMonoBlueTerrorReplicationExecutionInputLoader.replicationCells()
        val seeds = (1L..12L).map { 9_993_000L + it }
        val assignments = cells.mapIndexed { index, cell ->
            val seed = seeds[index]
            MonoBlueTerrorSmokeAssignment(
                gameNumber = cell.gameNumber,
                seed = seed,
                seedHex = terrorAssignmentSeedHex(seed),
                pestSeat = cell.pestSeat,
                terrorSeat = if (cell.pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO,
                startingDeck = cell.startingDeck,
            )
        }
        val input = MonoBlueTerrorReplicationExecutionInput(
            vectorIdentity = MonoBlueTerrorSmokeVectorIdentity(
                freezeCommit = PEST_MONO_BLUE_TERROR_REPLICATION_FREEZE_COMMIT,
                orderedVectorSha256 = PEST_MONO_BLUE_TERROR_REPLICATION_VECTOR_SHA256,
                assignmentCsvSha256 = PEST_MONO_BLUE_TERROR_REPLICATION_ASSIGNMENTS_SHA256,
                freezeManifestSha256 = PEST_MONO_BLUE_TERROR_REPLICATION_MANIFEST_SHA256,
            ),
            seeds = seeds,
            assignments = assignments,
        )

        val attempted = mutableListOf<Int>()
        val initialized = mutableListOf<Int>()
        val recorded = mutableListOf<Int>()

        val outcome = PestControlTierOneMonoBlueTerrorReplicationExecutionCoordinator(
            input,
            { attempted += it.gameNumber },
            { initialized += it.gameNumber },
            { assignment, _ -> recorded += assignment.gameNumber },
        ).execute { assignment ->
            if (assignment.gameNumber == 5) error("synthetic stop")
            "{}".toByteArray()
        }

        outcome.disposition shouldBe MonoBlueTerrorCoordinatorDisposition.REJECTED
        attempted shouldBe listOf(1, 2, 3, 4, 5)
        initialized shouldBe listOf(1, 2, 3, 4, 5)
        recorded shouldBe listOf(1, 2, 3, 4)
        outcome.failure shouldBe "synthetic stop"
    }
})
