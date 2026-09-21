package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private val COORDINATOR_VECTOR = listOf(9_960_001L, 9_960_002L, 9_960_003L, 9_960_004L)
private val COORDINATOR_IDENTITY = GrixisSmokeVectorIdentity(
    freezeCommit = "5".repeat(40),
    orderedVectorSha256 = PEST_GRIXIS_FROZEN_VECTOR_SHA256,
    assignmentCsvSha256 = PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256,
    freezeManifestSha256 = PEST_GRIXIS_FROZEN_MANIFEST_SHA256,
)

class PestControlTierOneGrixisAuthorizedExecutionCoordinatorTest : FunSpec({
    test("coordinator persists attempt before game and records all four in frozen order") {
        val eventOrder = mutableListOf<String>()
        val assignments = assignments()
        val coordinator = PestControlTierOneGrixisAuthorizedExecutionCoordinator(
            assignments = assignments,
            vectorIdentity = COORDINATOR_IDENTITY,
            persistAttemptBeforeInitialization = { attempt ->
                eventOrder += "attempt-${attempt.gameNumber}"
            },
            persistCompletedGame = { assignment, _ ->
                eventOrder += "record-${assignment.gameNumber}"
            },
        )

        val outcome = coordinator.execute { assignment ->
            eventOrder += "run-${assignment.gameNumber}"
            "synthetic-game-${assignment.gameNumber}\n".toByteArray()
        }

        outcome.disposition shouldBe GrixisCoordinatorDisposition.VALIDATED
        outcome.attempts.map { it.gameNumber } shouldBe listOf(1, 2, 3, 4)
        outcome.recordedGames shouldBe listOf(1, 2, 3, 4)
        eventOrder shouldBe listOf(
            "attempt-1", "run-1", "record-1",
            "attempt-2", "run-2", "record-2",
            "attempt-3", "run-3", "record-3",
            "attempt-4", "run-4", "record-4",
        )

        val summary = "synthetic-summary\n".toByteArray()
        val index = outcome.artifactIndex(COORDINATOR_IDENTITY, COORDINATOR_VECTOR, summary)
        index.isNotEmpty() shouldBe true
    }

    test("coordinator stops permanently on failure and does not attempt later games") {
        val attempts = mutableListOf<Int>()
        val records = mutableListOf<Int>()
        val coordinator = PestControlTierOneGrixisAuthorizedExecutionCoordinator(
            assignments = assignments(),
            vectorIdentity = COORDINATOR_IDENTITY,
            persistAttemptBeforeInitialization = { attempts += it.gameNumber },
            persistCompletedGame = { assignment, _ -> records += assignment.gameNumber },
        )

        val outcome = coordinator.execute { assignment ->
            if (assignment.gameNumber == 2) error("synthetic terminal failure")
            "synthetic-game-${assignment.gameNumber}\n".toByteArray()
        }

        outcome.disposition shouldBe GrixisCoordinatorDisposition.REJECTED
        attempts shouldBe listOf(1, 2)
        records shouldBe listOf(1)
        outcome.recordedGames shouldBe listOf(1)
        outcome.failure shouldBe "synthetic terminal failure"
    }

    test("coordinator rejects assignment reorder before any attempt") {
        val attempts = mutableListOf<Int>()
        val reordered = assignments().toMutableList().also { java.util.Collections.swap(it, 0, 1) }
        val coordinator = PestControlTierOneGrixisAuthorizedExecutionCoordinator(
            assignments = reordered,
            vectorIdentity = COORDINATOR_IDENTITY,
            persistAttemptBeforeInitialization = { attempts += it.gameNumber },
            persistCompletedGame = { _, _ -> },
        )
        val failure = runCatching { coordinator.execute { "x".toByteArray() } }.exceptionOrNull()
        (failure != null) shouldBe true
        attempts shouldBe emptyList()
    }
})

private fun assignments(): List<GrixisSmokeAssignment> =
    PestControlTierOneGrixisSmokeHarness.cellTemplate().mapIndexed { index, cell ->
        val seed = COORDINATOR_VECTOR[index]
        GrixisSmokeAssignment(
            gameNumber = cell.gameNumber,
            seed = seed,
            seedHex = "0x${seed.toULong().toString(16).padStart(16, '0')}",
            pestSeat = cell.pestSeat,
            grixisSeat = if (cell.pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO,
            startingDeck = cell.startingDeck,
        )
    }
