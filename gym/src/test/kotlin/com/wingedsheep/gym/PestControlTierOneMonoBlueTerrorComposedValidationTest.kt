package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.*
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import kotlin.time.Duration.Companion.hours

private const val TERROR_COMPOSED_VALIDATION_MODE = "VALIDATE_ONLY"

class PestControlTierOneMonoBlueTerrorComposedValidationTest : FunSpec({
    val enabled =
        System.getenv("PEST_TERROR_COMPOSED_MODE") == TERROR_COMPOSED_VALIDATION_MODE

    test("official frozen input validates while synthetic shadow runs through full stack").config(
        enabled = enabled,
        timeout = 4.hours,
    ) {
        val official =
            PestControlTierOneMonoBlueTerrorOfficialExecutionInputLoader
                .loadValidatedFromEnvironment()

        official.archiveSha256 shouldBe PEST_MONO_BLUE_TERROR_FROZEN_ARCHIVE_SHA256
        official.assignments.size shouldBe PEST_MONO_BLUE_TERROR_SMOKE_GAMES
        official.seeds.size shouldBe PEST_MONO_BLUE_TERROR_SMOKE_GAMES
        official.assignments.map { it.gameNumber } shouldBe listOf(1, 2, 3, 4)
        official.assignments.map { it.seed } shouldBe official.seeds

        val registry = CardRegistry().apply {
            register(PredefinedTokens.allTokens)
            MtgSetCatalog.all.forEach { set ->
                register(set.cards)
                register(set.basicLands)
            }
        }

        val syntheticSeeds = listOf(
            0x7E77_0B1E_3000_0001L,
            0x7E77_0B1E_3000_0002L,
            0x7E77_0B1E_3000_0003L,
            0x7E77_0B1E_3000_0004L,
        )
        syntheticSeeds.toSet().intersect(official.seeds.toSet()) shouldBe emptySet()

        val syntheticAssignments =
            PestControlTierOneMonoBlueTerrorSmokeHarness.cellTemplate()
                .mapIndexed { index, cell ->
                    val seed = syntheticSeeds[index]
                    MonoBlueTerrorSmokeAssignment(
                        gameNumber = cell.gameNumber,
                        seed = seed,
                        seedHex = terrorAssignmentSeedHex(seed),
                        pestSeat = cell.pestSeat,
                        terrorSeat =
                            if (cell.pestSeat == PestSeat.SEAT_ZERO) {
                                PestSeat.SEAT_ONE
                            } else {
                                PestSeat.SEAT_ZERO
                            },
                        startingDeck = cell.startingDeck,
                    )
                }

        val output = Path.of(
            System.getenv("PEST_TERROR_COMPOSED_OUTPUT_DIR")
                ?: error("PEST_TERROR_COMPOSED_OUTPUT_DIR required")
        )
        Files.createDirectories(output)
        val evidenceRoot = output.resolve("synthetic-evidence-root")
        Files.createDirectory(evidenceRoot)

        val executionCommit =
            System.getenv("PEST_TERROR_COMPOSED_EXECUTION_COMMIT")
                ?: error("PEST_TERROR_COMPOSED_EXECUTION_COMMIT required")
        require(executionCommit.matches(Regex("[0-9a-f]{40}")))

        val journal = MonoBlueTerrorDurableAttempts.createNew(
            evidenceRoot = evidenceRoot,
            assignments = syntheticAssignments,
            executionSourceCommit = executionCommit,
        )

        val coordinator =
            PestControlTierOneMonoBlueTerrorAuthorizedExecutionCoordinator(
                assignments = syntheticAssignments,
                vectorIdentity = official.vectorIdentity,
                persistAttemptBeforeInitialization = { attempt ->
                    attempt.seed shouldBe syntheticAssignments[attempt.gameNumber - 1].seed
                    journal.recordAttempt(attempt.gameNumber)
                },
                persistInitializationEntry = { assignment ->
                    journal.recordInitializationEntry(assignment.gameNumber)
                },
                persistCompletedGame = { assignment, raw ->
                    journal.recordResult(assignment.gameNumber, raw)
                },
            )

        val outcome = coordinator.execute { assignment ->
            val durableAttempt = journal.events().any {
                it.gameNumber == assignment.gameNumber &&
                    it.type ==
                    MonoBlueTerrorCoordinatorEventType.ATTEMPT_DURABLY_RECORDED
            }
            val initialized =
                PestControlTierOneMonoBlueTerrorAuthorizedInitializer.initialize(
                    registry = registry,
                    assignment = assignment,
                    vectorIdentity = official.vectorIdentity,
                    executionCommit = executionCommit,
                    durableAttemptRecorded = durableAttempt,
                )
            PestControlTierOneMonoBlueTerrorProductionDriver.encode(
                PestControlTierOneMonoBlueTerrorProductionDriver.drive(
                    registry,
                    initialized,
                )
            )
        }

        outcome.disposition shouldBe MonoBlueTerrorCoordinatorDisposition.VALIDATED
        outcome.attempts.map { it.gameNumber } shouldBe listOf(1, 2, 3, 4)
        outcome.initializedGames shouldBe listOf(1, 2, 3, 4)
        outcome.recordedGames shouldBe listOf(1, 2, 3, 4)
        journal.events().size shouldBe 12

        val summary = buildString {
            appendLine("status=VALIDATED_ONLY")
            appendLine("official_artifact_id=$PEST_MONO_BLUE_TERROR_FREEZE_ARTIFACT_ID")
            appendLine("official_archive_sha256=${official.archiveSha256}")
            appendLine("official_assignment_rows=${official.assignments.size}")
            appendLine("official_seed_values_loaded_for_validation=${official.seeds.size}")
            appendLine("official_seeds_consumed=0")
            appendLine("official_games_initialized=0")
            appendLine("official_actions_submitted=0")
            appendLine("official_outcome_exposure=0")
            appendLine("synthetic_shadow_games=${outcome.recordedGames.size}")
            appendLine("synthetic_seed_overlap_with_official=0")
        }.toByteArray()

        val index = outcome.artifactIndex(
            vectorIdentity = official.vectorIdentity,
            frozenSeeds = syntheticSeeds,
            summary = summary,
        )

        Files.write(
            output.resolve("validation-summary.txt"),
            summary,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        )
        Files.write(
            output.resolve("synthetic-artifact-index.json"),
            index,
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        )
        Files.writeString(
            output.resolve("official-input-validation.txt"),
            buildString {
                appendLine("artifact_id=$PEST_MONO_BLUE_TERROR_FREEZE_ARTIFACT_ID")
                appendLine("archive_sha256=${official.archiveSha256}")
                appendLine(
                    "ordered_vector_sha256=${official.vectorIdentity.orderedVectorSha256}"
                )
                appendLine(
                    "assignment_csv_sha256=${official.vectorIdentity.assignmentCsvSha256}"
                )
                appendLine(
                    "freeze_manifest_sha256=${official.vectorIdentity.freezeManifestSha256}"
                )
                appendLine("assignment_rows=${official.assignments.size}")
                appendLine("gameplay_initialized=0")
            },
            StandardOpenOption.CREATE_NEW,
            StandardOpenOption.WRITE,
        )
    }
})
