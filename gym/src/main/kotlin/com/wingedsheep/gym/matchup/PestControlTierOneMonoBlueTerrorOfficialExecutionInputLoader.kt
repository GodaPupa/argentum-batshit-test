package com.wingedsheep.gym.matchup

import java.nio.file.Files
import java.nio.file.Path

const val PEST_MONO_BLUE_TERROR_EXECUTION_INPUT_ACK =
    "LOAD_FROZEN_TIER_ONE_MONO_BLUE_TERROR_4_FOR_VALIDATION_ONLY"
const val PEST_MONO_BLUE_TERROR_EXECUTION_INPUT_EXECUTE_ACK =
    "AUTOMATIC_ONE_SHOT_OFFICIAL_EXECUTION"
const val PEST_MONO_BLUE_TERROR_FREEZE_ARTIFACT_ID = 10_733_086_089L
const val PEST_MONO_BLUE_TERROR_FREEZE_COMMIT =
    "eb140403cceff8e930afdfb2874972c6e44f77e7"

data class MonoBlueTerrorOfficialExecutionInput(
    val vectorIdentity: MonoBlueTerrorSmokeVectorIdentity,
    val seeds: List<Long>,
    val assignments: List<MonoBlueTerrorSmokeAssignment>,
    val archiveSha256: String,
)

/**
 * Loads only the already-frozen official ZIP after exact acknowledgement and pinned byte validation.
 * It performs no game initialization, action submission, evidence writes, retry, replacement, or
 * seed generation.
 */
object PestControlTierOneMonoBlueTerrorOfficialExecutionInputLoader {
    fun loadValidatedFromEnvironment(): MonoBlueTerrorOfficialExecutionInput {
        require(
            System.getenv("PEST_TERROR_EXECUTION_INPUT_ACK") ==
                PEST_MONO_BLUE_TERROR_EXECUTION_INPUT_ACK
        ) { "exact Mono-Blue Terror validation acknowledgement is required" }
        return loadFromEnvironment()
    }

    fun loadForAuthorizedExecutionFromEnvironment(): MonoBlueTerrorOfficialExecutionInput {
        require(
            System.getenv("PEST_TERROR_EXECUTION_INPUT_ACK") ==
                PEST_MONO_BLUE_TERROR_EXECUTION_INPUT_EXECUTE_ACK
        ) { "exact Mono-Blue Terror execution acknowledgement is required" }
        return loadFromEnvironment()
    }

    private fun loadFromEnvironment(): MonoBlueTerrorOfficialExecutionInput {
        val path = Path.of(
            System.getenv("PEST_TERROR_EXECUTION_INPUT_ZIP")
                ?: error("PEST_TERROR_EXECUTION_INPUT_ZIP required")
        )
        require(Files.isRegularFile(path)) { "official frozen ZIP is absent" }
        return loadValidatedArchive(Files.readAllBytes(path))
    }

    internal fun loadValidatedArchive(
        archive: ByteArray,
    ): MonoBlueTerrorOfficialExecutionInput {
        val snapshot = archive.copyOf()
        val inspection =
            PestControlTierOneMonoBlueTerrorFrozenArtifactVerifier.inspect(snapshot)
        require(inspection.verified) {
            "frozen artifact verification failed: ${inspection.errors.joinToString()}"
        }
        require(inspection.archiveSha256 == PEST_MONO_BLUE_TERROR_FROZEN_ARCHIVE_SHA256)

        val assignments =
            PestControlTierOneMonoBlueTerrorAssignmentDecoder.decode(snapshot)
        require(assignments.size == PEST_MONO_BLUE_TERROR_SMOKE_GAMES)
        require(
            assignments.map { it.gameNumber } ==
                (1..PEST_MONO_BLUE_TERROR_SMOKE_GAMES).toList()
        )
        val seeds = assignments.map { it.seed }
        require(seeds.distinct().size == seeds.size && seeds.none { it == 0L })
        require(
            terrorFrozenDigest(
                seeds.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)
            ) == PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_SHA256
        )

        val identity = MonoBlueTerrorSmokeVectorIdentity(
            freezeCommit = PEST_MONO_BLUE_TERROR_FREEZE_COMMIT,
            orderedVectorSha256 = PEST_MONO_BLUE_TERROR_FROZEN_VECTOR_SHA256,
            assignmentCsvSha256 = PEST_MONO_BLUE_TERROR_FROZEN_ASSIGNMENTS_SHA256,
            freezeManifestSha256 = PEST_MONO_BLUE_TERROR_FROZEN_MANIFEST_SHA256,
        )

        assignments.zip(PestControlTierOneMonoBlueTerrorSmokeHarness.cellTemplate())
            .forEach { (assignment, cell) ->
                require(assignment.gameNumber == cell.gameNumber)
                require(assignment.pestSeat == cell.pestSeat)
                require(assignment.startingDeck == cell.startingDeck)
                require(assignment.terrorSeat != assignment.pestSeat)
            }

        return MonoBlueTerrorOfficialExecutionInput(
            vectorIdentity = identity,
            seeds = seeds.toList(),
            assignments = assignments.toList(),
            archiveSha256 = inspection.archiveSha256,
        )
    }
}
