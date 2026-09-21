package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import kotlin.time.Duration.Companion.hours

private const val COMPOSED_VALIDATION_MODE = "VALIDATE_ONLY"

class PestControlTierOneGrixisComposedValidationTest : FunSpec({
    val enabled = System.getenv("PEST_GRIXIS_COMPOSED_MODE") == COMPOSED_VALIDATION_MODE

    test("official artifact validates while synthetic shadow executes through complete stack").config(
        enabled = enabled,
        timeout = 3.hours,
    ) {
        val official = PestControlTierOneGrixisOfficialExecutionInputLoader.loadValidatedFromEnvironment()
        official.seeds.size shouldBe PEST_GRIXIS_SMOKE_GAMES
        official.assignments.size shouldBe PEST_GRIXIS_SMOKE_GAMES
        official.assignments.map { it.gameNumber } shouldBe (1..PEST_GRIXIS_SMOKE_GAMES).toList()

        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        val syntheticSeeds = listOf(9_980_001L, 9_980_002L, 9_980_003L, 9_980_004L)
        val syntheticAssignments = PestControlTierOneGrixisSmokeHarness.cellTemplate().mapIndexed { index, cell ->
            val seed = syntheticSeeds[index]
            GrixisSmokeAssignment(
                gameNumber = cell.gameNumber,
                seed = seed,
                seedHex = "0x${seed.toULong().toString(16).padStart(16, '0')}",
                pestSeat = cell.pestSeat,
                grixisSeat = if (cell.pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO,
                startingDeck = cell.startingDeck,
            )
        }

        val output = Path.of(
            System.getenv("PEST_GRIXIS_COMPOSED_OUTPUT_DIR")
                ?: error("PEST_GRIXIS_COMPOSED_OUTPUT_DIR required"),
        )
        Files.createDirectories(output)
        val attempted = mutableListOf<Int>()
        val recorded = mutableListOf<Int>()

        val coordinator = PestControlTierOneGrixisAuthorizedExecutionCoordinator(
            assignments = syntheticAssignments,
            vectorIdentity = official.vectorIdentity,
            persistAttemptBeforeInitialization = { attempt ->
                attempted += attempt.gameNumber
            },
            persistCompletedGame = { assignment, raw ->
                recorded += assignment.gameNumber
                Files.write(output.resolve("synthetic-game-${assignment.gameNumber}.json"), raw)
            },
        )

        val executionCommit = System.getenv("PEST_GRIXIS_COMPOSED_EXECUTION_COMMIT")
            ?: error("PEST_GRIXIS_COMPOSED_EXECUTION_COMMIT required")

        val outcome = coordinator.execute { assignment ->
            val initialized = PestControlTierOneGrixisAuthorizedInitializer.initialize(
                registry = registry,
                assignment = assignment,
                vectorIdentity = official.vectorIdentity,
                executionCommit = executionCommit,
                durableAttemptRecorded = attempted.lastOrNull() == assignment.gameNumber,
            )
            val raw = PestControlTierOneGrixisProductionDriver.drive(registry, initialized)
            PestControlTierOneGrixisProductionDriver.encode(raw)
        }

        outcome.disposition shouldBe GrixisCoordinatorDisposition.VALIDATED
        attempted shouldBe listOf(1, 2, 3, 4)
        recorded shouldBe listOf(1, 2, 3, 4)
        outcome.recordedGames shouldBe listOf(1, 2, 3, 4)

        val summary = buildString {
            appendLine("status=VALIDATED_ONLY")
            appendLine("official_artifact_rows=${official.assignments.size}")
            appendLine("official_seed_values_exposed=0")
            appendLine("official_seeds_consumed=0")
            appendLine("official_games_initialized=0")
            appendLine("official_actions_submitted=0")
            appendLine("official_outcome_exposure=0")
            appendLine("synthetic_shadow_games=${outcome.recordedGames.size}")
        }.toByteArray()

        val index = outcome.artifactIndex(
            vectorIdentity = official.vectorIdentity,
            frozenSeeds = syntheticSeeds,
            summary = summary,
        )
        Files.write(output.resolve("validation-summary.txt"), summary)
        Files.write(output.resolve("synthetic-artifact-index.json"), index)
    }
})
