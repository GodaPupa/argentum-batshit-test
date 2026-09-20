package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private val COORDINATOR_TEST_SEEDS = (1L..10L).map { 6_000_000L + it }
private val COORDINATOR_TEST_SUMMARY = "synthetic-summary\n".toByteArray()

class PestControlV2OfficialExecutionCoordinatorTest : FunSpec({
    test("synthetic coordinator persists each attempt before game initialization and completes exactly once") {
        val events = mutableListOf<String>()
        val coordinator = syntheticCoordinator(
            persistAttempt = { events += "attempt-${it.gameNumber}" },
            persistGame = { assignment, _ -> events += "record-${assignment.game}" },
        )

        val outcome = coordinator.execute { assignment ->
            events += "initialize-${assignment.game}"
            "synthetic-${assignment.game}\n".toByteArray()
        }

        outcome.state shouldBe V2OfficialRunnerState.COMPLETED
        outcome.failure shouldBe null
        outcome.attempts.map { it.seed } shouldBe COORDINATOR_TEST_SEEDS
        outcome.recordedGames shouldBe (1..10).toList()
        events shouldBe (1..10).flatMap { listOf("attempt-$it", "initialize-$it", "record-$it") }
        PestControlV2OfficialArtifactContract.validate(
            PROTOCOL_JSON.decodeFromString(outcome.artifactIndex(COORDINATOR_TEST_SUMMARY).decodeToString()),
            COORDINATOR_TEST_SEEDS,
            outcome.perGameRaw,
            COORDINATOR_TEST_SUMMARY,
        ) shouldBe emptyList()
    }

    test("synthetic failure rejects the block without invoking later assignments") {
        val initialized = mutableListOf<Int>()
        val coordinator = syntheticCoordinator()

        val outcome = coordinator.execute { assignment ->
            initialized += assignment.game
            if (assignment.game == 3) error("synthetic interruption")
            "synthetic-${assignment.game}\n".toByteArray()
        }

        outcome.state shouldBe V2OfficialRunnerState.REJECTED
        outcome.failure shouldBe "synthetic interruption"
        outcome.attempts.map { it.gameNumber } shouldBe listOf(1, 2, 3)
        outcome.recordedGames shouldBe listOf(1, 2)
        initialized shouldBe listOf(1, 2, 3)
        PestControlV2OfficialArtifactContract.validate(
            PROTOCOL_JSON.decodeFromString(outcome.artifactIndex(COORDINATOR_TEST_SUMMARY).decodeToString()),
            COORDINATOR_TEST_SEEDS,
            outcome.perGameRaw,
            COORDINATOR_TEST_SUMMARY,
        ) shouldBe emptyList()
    }

    test("preflight failure exposes no seed to the game function") {
        var invoked = false
        val rejected = syntheticPreflight().copy(errors = listOf("synthetic mismatch"))
        val coordinator = PestControlV2OfficialExecutionCoordinator(rejected, {}, { _, _ -> })

        val outcome = coordinator.execute {
            invoked = true
            byteArrayOf()
        }

        outcome.state shouldBe V2OfficialRunnerState.REJECTED
        outcome.attempts shouldBe emptyList()
        invoked shouldBe false
    }

    test("attempt persistence failure prevents game initialization") {
        var invoked = false
        val coordinator = syntheticCoordinator(
            persistAttempt = { error("synthetic persistence failure") },
        )

        val outcome = coordinator.execute {
            invoked = true
            byteArrayOf()
        }

        outcome.state shouldBe V2OfficialRunnerState.REJECTED
        outcome.attempts.map { it.gameNumber } shouldBe listOf(1)
        outcome.recordedGames shouldBe emptyList()
        outcome.failure shouldBe "synthetic persistence failure"
        invoked shouldBe false
    }
})

private fun syntheticCoordinator(
    persistAttempt: (V2OfficialAttempt) -> Unit = {},
    persistGame: (V2OfficialAssignment, ByteArray) -> Unit = { _, _ -> },
) = PestControlV2OfficialExecutionCoordinator(syntheticPreflight(), persistAttempt, persistGame)

private fun syntheticPreflight() = V2OfficialPreflightResult(
    errors = emptyList(),
    seeds = COORDINATOR_TEST_SEEDS,
    assignments = COORDINATOR_TEST_SEEDS.mapIndexed { index, seed ->
        val pestSeat = if (index % 2 == 0) PestSeat.SEAT_ZERO else PestSeat.SEAT_ONE
        val starter = if (index < 5) StartingDeck.PEST_CONTROL else StartingDeck.MONO_RED_MADNESS
        V2OfficialAssignment(
            game = index + 1,
            seed = seed,
            seedHex = "0x${seed.toULong().toString(16).padStart(16, '0')}",
            pestSeat = pestSeat,
            redSeat = if (pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO,
            starter = starter,
            playDraw = if (starter == StartingDeck.PEST_CONTROL) "PLAY" else "DRAW",
        )
    },
)
