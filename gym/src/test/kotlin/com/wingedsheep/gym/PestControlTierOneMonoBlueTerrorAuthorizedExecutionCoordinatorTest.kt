package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private val COORD_TERROR_IDENTITY = MonoBlueTerrorSmokeVectorIdentity(
    freezeCommit = "1".repeat(40),
    orderedVectorSha256 = PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_SHA256,
    assignmentCsvSha256 = PEST_MONO_BLUE_TERROR_FROZEN_ASSIGNMENTS_SHA256,
    freezeManifestSha256 = PEST_MONO_BLUE_TERROR_FROZEN_MANIFEST_SHA256,
)

class PestControlTierOneMonoBlueTerrorAuthorizedExecutionCoordinatorTest : FunSpec({
    test("successful coordinator preserves durable attempt-init-record ordering for all four cells") {
        val assignments = terrorCoordinatorAssignments()
        val durableOrder = mutableListOf<String>()

        val coordinator = PestControlTierOneMonoBlueTerrorAuthorizedExecutionCoordinator(
            assignments = assignments,
            vectorIdentity = COORD_TERROR_IDENTITY,
            persistAttemptBeforeInitialization = { attempt ->
                durableOrder += "attempt-${attempt.gameNumber}"
            },
            persistInitializationEntry = { assignment ->
                durableOrder += "init-${assignment.gameNumber}"
            },
            persistCompletedGame = { assignment, raw ->
                raw.isNotEmpty() shouldBe true
                durableOrder += "record-${assignment.gameNumber}"
            },
        )

        val outcome = coordinator.execute { assignment ->
            "synthetic-authorized-terror-game-${assignment.gameNumber}\n".toByteArray()
        }

        outcome.disposition shouldBe MonoBlueTerrorCoordinatorDisposition.VALIDATED
        outcome.attempts.map { it.gameNumber } shouldBe listOf(1, 2, 3, 4)
        outcome.initializedGames shouldBe listOf(1, 2, 3, 4)
        outcome.recordedGames shouldBe listOf(1, 2, 3, 4)
        outcome.perGameRaw.size shouldBe 4
        outcome.failure shouldBe null
        durableOrder shouldBe (1..4).flatMap { game ->
            listOf("attempt-$game", "init-$game", "record-$game")
        }

        val summary = "status=VALIDATED\n".toByteArray()
        val index = outcome.artifactIndex(
            COORD_TERROR_IDENTITY,
            assignments.map { it.seed },
            summary,
        )
        index.isNotEmpty() shouldBe true
    }

    test("game failure rejects terminally without attempting later cells") {
        val assignments = terrorCoordinatorAssignments()
        val attempts = mutableListOf<Int>()
        val initialized = mutableListOf<Int>()
        val recorded = mutableListOf<Int>()

        val coordinator = PestControlTierOneMonoBlueTerrorAuthorizedExecutionCoordinator(
            assignments = assignments,
            vectorIdentity = COORD_TERROR_IDENTITY,
            persistAttemptBeforeInitialization = { attempts += it.gameNumber },
            persistInitializationEntry = { initialized += it.gameNumber },
            persistCompletedGame = { assignment, _ -> recorded += assignment.gameNumber },
        )

        val outcome = coordinator.execute { assignment ->
            if (assignment.gameNumber == 2) error("synthetic game two failure")
            "synthetic-game-${assignment.gameNumber}\n".toByteArray()
        }

        outcome.disposition shouldBe MonoBlueTerrorCoordinatorDisposition.REJECTED
        outcome.attempts.map { it.gameNumber } shouldBe listOf(1, 2)
        outcome.initializedGames shouldBe listOf(1, 2)
        outcome.recordedGames shouldBe listOf(1)
        attempts shouldBe listOf(1, 2)
        initialized shouldBe listOf(1, 2)
        recorded shouldBe listOf(1)
        outcome.failure shouldBe "synthetic game two failure"
    }

    test("drifted vector identity is rejected before durable evidence") {
        val assignments = terrorCoordinatorAssignments()
        var writes = 0
        val coordinator = PestControlTierOneMonoBlueTerrorAuthorizedExecutionCoordinator(
            assignments = assignments,
            vectorIdentity = COORD_TERROR_IDENTITY.copy(orderedVectorSha256 = "f".repeat(64)),
            persistAttemptBeforeInitialization = { writes++ },
            persistInitializationEntry = { writes++ },
            persistCompletedGame = { _, _ -> writes++ },
        )

        val failure = runCatching {
            coordinator.execute { "never\n".toByteArray() }
        }.exceptionOrNull()

        (failure is IllegalArgumentException) shouldBe true
        writes shouldBe 0
    }
})

private fun terrorCoordinatorAssignments(): List<MonoBlueTerrorSmokeAssignment> {
    val seeds = listOf(9_970_001L, 9_970_002L, 9_970_003L, 9_970_004L)
    return PestControlTierOneMonoBlueTerrorSmokeHarness.cellTemplate().mapIndexed { index, cell ->
        val seed = seeds[index]
        MonoBlueTerrorSmokeAssignment(
            gameNumber = cell.gameNumber,
            seed = seed,
            seedHex = "0x${seed.toULong().toString(16).padStart(16, '0')}",
            pestSeat = cell.pestSeat,
            terrorSeat = if (cell.pestSeat == PestSeat.SEAT_ZERO) {
                PestSeat.SEAT_ONE
            } else {
                PestSeat.SEAT_ZERO
            },
            startingDeck = cell.startingDeck,
        )
    }
}
