package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path

const val PEST_GRIXIS_OFFICIAL_LOADER_DISABLED_SHA256 =
    "0c5dfd0d9d09754f50f29f4be42eb874433fa4ef9a881e6072e4543c7f9adfb6"
const val PEST_GRIXIS_OFFICIAL_LOADER_VALIDATED_SHA256 =
    "8f3634a183b40b71329c900eed4e9a649cb46b76f6f5051462f9098f4eafbee1"
const val PEST_GRIXIS_OFFICIAL_LOADER_DISABLED_STATUS = "OFFICIAL_ARTIFACT_LOADER_DISABLED"
const val PEST_GRIXIS_OFFICIAL_LOADER_VALIDATED_STATUS =
    "OFFICIAL_ARTIFACT_VERIFIED_UNCONSUMED_EXECUTION_NOT_AUTHORIZED"

enum class GrixisOfficialArtifactLoaderMode {
    DISABLED,
    VALIDATE_PATH_FROM_ENVIRONMENT,
}

data class GrixisDisabledOfficialArtifactLoaderInspection(
    val errors: List<String>,
    val loaderSha256: String,
    val status: String,
    val officialArtifactBytesLoaded: Boolean,
    val officialSeedValuesParsed: Int,
    val officialSeedValuesVisible: Int = 0,
    val officialAssignmentsParsed: Int,
    val cellsValidated: Int,
    val officialSeedsConsumed: Int = 0,
    val initializerEnabled: Boolean = false,
    val runnerEnabled: Boolean = false,
    val officialGamesAuthorized: Int = 0,
    val officialGamesInitialized: Int = 0,
    val submittedActions: Int = 0,
    val artifactsWithOutcomes: Int = 0,
    val outcomeExposure: Int = 0,
) {
    val green: Boolean get() = errors.isEmpty()
}

/** Disabled official-byte loader. It verifies artifacts but cannot initialize or execute a game. */
object PestControlTierOneGrixisDisabledOfficialArtifactLoader {
    private val expectedHeader = listOf(
        "protocol_id", "block_id", "game_number", "seed_decimal", "seed_hex", "pest_seat",
        "grixis_seat", "starting_deck", "pest_play_draw", "pest_main_sha256",
        "grixis_main_sha256", "grixis_sideboard_sha256", "grixis_complete75_sha256",
        "qualified_runner",
    ).joinToString(",")

    fun inspect(
        registry: CardRegistry,
        mode: GrixisOfficialArtifactLoaderMode = GrixisOfficialArtifactLoaderMode.DISABLED,
    ): GrixisDisabledOfficialArtifactLoaderInspection {
        val errors = mutableListOf<String>()
        val parser = PestControlTierOneGrixisPrivateSyntheticParser.inspect(registry)
        if (!parser.green) errors += "private synthetic parser is not green"
        if (parser.parserSha256 != PEST_GRIXIS_PRIVATE_SYNTHETIC_PARSER_SHA256) {
            errors += "private synthetic parser proof mismatch"
        }
        val loaded = if (mode == GrixisOfficialArtifactLoaderMode.VALIDATE_PATH_FROM_ENVIRONMENT) {
            runCatching { loadOfficialArtifact() }.getOrElse {
                errors += "official artifact validation failure: ${it.message ?: it::class.simpleName}"
                LoadedOfficialArtifact(0, 0, 0)
            }
        } else {
            LoadedOfficialArtifact(0, 0, 0)
        }
        val bytesLoaded = mode == GrixisOfficialArtifactLoaderMode.VALIDATE_PATH_FROM_ENVIRONMENT &&
            loaded.seedCount > 0 && loaded.assignmentCount > 0
        val status = if (bytesLoaded) {
            PEST_GRIXIS_OFFICIAL_LOADER_VALIDATED_STATUS
        } else {
            PEST_GRIXIS_OFFICIAL_LOADER_DISABLED_STATUS
        }

        val proofBytes = listOf(
            "pest-control-tier-one-grixis-disabled-official-artifact-loader-v1",
            "status=$status",
            "syntheticParserSha256=${parser.parserSha256}",
            "artifactEnvelopeSha256=$PEST_GRIXIS_DISABLED_ARTIFACT_ENVELOPE_SHA256",
            "orderedVectorSha256=$PEST_GRIXIS_FROZEN_VECTOR_SHA256",
            "assignmentCsvSha256=$PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256",
            "freezeManifestSha256=$PEST_GRIXIS_FROZEN_MANIFEST_SHA256",
            "quarantinedVectorSha256=$PEST_GRIXIS_FROZEN_QUARANTINE_SHA256",
            "checksumInventorySha256=$PEST_GRIXIS_FROZEN_CHECKSUM_INVENTORY_SHA256",
            "artifactArchiveSha256=$PEST_GRIXIS_FROZEN_ARCHIVE_SHA256",
            "officialArtifactBytesLoaded=$bytesLoaded",
            "officialSeedValuesParsed=${loaded.seedCount}",
            "officialSeedValuesVisible=0",
            "officialAssignmentsParsed=${loaded.assignmentCount}",
            "cellsValidated=${loaded.cellsValidated}",
            "officialSeedsConsumed=0",
            "initializerEnabled=false",
            "runnerEnabled=false",
            "officialGamesAuthorized=0",
            "officialGamesInitialized=0",
            "submittedActions=0",
            "artifactsWithOutcomes=0",
            "outcomeExposure=0",
        ).joinToString("\n", postfix = "\n").toByteArray()
        val loaderSha256 = sha256(proofBytes)
        val expectedProof = if (bytesLoaded) {
            PEST_GRIXIS_OFFICIAL_LOADER_VALIDATED_SHA256
        } else {
            PEST_GRIXIS_OFFICIAL_LOADER_DISABLED_SHA256
        }
        if (loaderSha256 != expectedProof) errors += "official artifact loader proof mismatch"
        return GrixisDisabledOfficialArtifactLoaderInspection(
            errors = errors.distinct(),
            loaderSha256 = loaderSha256,
            status = status,
            officialArtifactBytesLoaded = bytesLoaded,
            officialSeedValuesParsed = loaded.seedCount,
            officialAssignmentsParsed = loaded.assignmentCount,
            cellsValidated = loaded.cellsValidated,
        )
    }

    private fun loadOfficialArtifact(): LoadedOfficialArtifact {
        require(System.getenv("PEST_GRIXIS_OFFICIAL_LOADER_ACK") ==
            "VALIDATE_TIER_ONE_GRIXIS_OFFICIAL_ARTIFACT_BYTES_NO_GAMEPLAY")
        val input = Path.of(
            System.getenv("PEST_GRIXIS_OFFICIAL_ARTIFACT_DIR") ?: error("official artifact directory required"),
        )
        require(Files.isDirectory(input))
        val vector = Files.readAllBytes(input.resolve("ordered-seeds.txt"))
        val csv = Files.readAllBytes(input.resolve("assignments.csv"))
        val manifest = Files.readAllBytes(input.resolve("freeze-manifest.json"))
        val quarantine = Files.readAllBytes(input.resolve("quarantined-vector.json"))
        val inventory = Files.readAllBytes(input.resolve("artifacts.sha256"))
        require(sha256(vector) == PEST_GRIXIS_FROZEN_VECTOR_SHA256)
        require(sha256(csv) == PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256)
        require(sha256(manifest) == PEST_GRIXIS_FROZEN_MANIFEST_SHA256)
        require(sha256(quarantine) == PEST_GRIXIS_FROZEN_QUARANTINE_SHA256)
        require(sha256(inventory) == PEST_GRIXIS_FROZEN_CHECKSUM_INVENTORY_SHA256)
        validateInventory(inventory)
        val seeds = parseVector(vector)
        val assignments = parseAssignments(csv)
        require(seeds.size == PEST_GRIXIS_SMOKE_GAMES && seeds.distinct().size == seeds.size)
        require(seeds.none { it == 0L })
        require(assignments.size == PEST_GRIXIS_SMOKE_GAMES)
        require(assignments.map { it.game } == (1..PEST_GRIXIS_SMOKE_GAMES).toList())
        require(assignments.map { it.seed } == seeds)
        val expectedCells = PestControlTierOneGrixisSmokeHarness.cellTemplate()
        assignments.zip(expectedCells).forEach { (assignment, expected) ->
            require(assignment.pestSeat == expected.pestSeat)
            require(assignment.grixisSeat != assignment.pestSeat)
            require(assignment.startingDeck == expected.startingDeck)
            require(assignment.playDraw == if (expected.startingDeck == GrixisStartingDeck.PEST_CONTROL) "PLAY" else "DRAW")
        }
        validateManifest(manifest)
        validateQuarantine(quarantine, seeds)
        return LoadedOfficialArtifact(seeds.size, assignments.size, expectedCells.size)
    }

    private fun parseVector(bytes: ByteArray): List<Long> {
        val text = bytes.decodeToString()
        require(text.isNotEmpty() && !text.contains('\r') && text.endsWith('\n'))
        return text.trimEnd('\n').lines().map(String::toLong)
    }

    private fun parseAssignments(bytes: ByteArray): List<OfficialAssignment> {
        val text = bytes.decodeToString()
        require(text.isNotEmpty() && !text.contains('\r') && text.endsWith('\n'))
        val lines = text.trimEnd('\n').lines()
        require(lines.first() == expectedHeader)
        return lines.drop(1).map { line ->
            val columns = line.split(',')
            require(columns.size == 14)
            require(columns[0] == PEST_GRIXIS_PREBOARD_PROTOCOL_ID)
            require(columns[1] == PEST_GRIXIS_SMOKE_BLOCK_ID)
            require(columns[9] == PEST_CONTROL_V10_HASH)
            require(columns[10] == PEST_GRIXIS_MAIN_SHA256)
            require(columns[11] == PEST_GRIXIS_SIDEBOARD_SHA256)
            require(columns[12] == PEST_GRIXIS_COMPLETE_75_SHA256)
            require(columns[13] == PEST_V2_QUALIFIED_RUNNER)
            val seed = columns[3].toLong()
            require(columns[4] == "0x${seed.toULong().toString(16).padStart(16, '0')}")
            OfficialAssignment(
                game = columns[2].toInt(),
                seed = seed,
                pestSeat = PestSeat.valueOf(columns[5]),
                grixisSeat = PestSeat.valueOf(columns[6]),
                startingDeck = GrixisStartingDeck.valueOf(columns[7]),
                playDraw = columns[8],
            )
        }
    }

    private fun validateManifest(bytes: ByteArray) {
        val manifest = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        require(manifest.getValue("status").jsonPrimitive.content == "FROZEN_UNEXECUTED")
        require(manifest.getValue("protocol_id").jsonPrimitive.content == PEST_GRIXIS_PREBOARD_PROTOCOL_ID)
        require(manifest.getValue("block_id").jsonPrimitive.content == PEST_GRIXIS_SMOKE_BLOCK_ID)
        require(manifest.getValue("qualified_runner").jsonPrimitive.content == PEST_V2_QUALIFIED_RUNNER)
        require(manifest.getValue("runner_state").jsonPrimitive.content == "DISABLED")
        val hashes = manifest.getValue("artifact_hashes").jsonObject
        require(hashes.getValue("ordered_vector_sha256").jsonPrimitive.content == PEST_GRIXIS_FROZEN_VECTOR_SHA256)
        require(hashes.getValue("assignment_csv_sha256").jsonPrimitive.content == PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256)
        val counters = manifest.getValue("official_counters").jsonObject
        require(counters.values.all { it.jsonPrimitive.content == "0" })
    }

    private fun validateQuarantine(bytes: ByteArray, seeds: List<Long>) {
        val quarantine = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        require(quarantine.getValue("block_id").jsonPrimitive.content == PEST_GRIXIS_SMOKE_BLOCK_ID)
        require(quarantine.getValue("status").jsonPrimitive.content == "QUARANTINED_UNATTEMPTED")
        require(quarantine.getValue("seeds_decimal").jsonArray.map { it.jsonPrimitive.content.toLong() } == seeds)
    }

    private fun validateInventory(bytes: ByteArray) {
        val expected = setOf(
            "$PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256  assignments.csv",
            "$PEST_GRIXIS_FROZEN_MANIFEST_SHA256  freeze-manifest.json",
            "$PEST_GRIXIS_FROZEN_VECTOR_SHA256  ordered-seeds.txt",
            "$PEST_GRIXIS_FROZEN_QUARANTINE_SHA256  quarantined-vector.json",
        )
        val text = bytes.decodeToString()
        require(!text.contains('\r') && text.endsWith('\n'))
        require(text.trimEnd('\n').lines().toSet() == expected)
    }

    private data class LoadedOfficialArtifact(
        val seedCount: Int,
        val assignmentCount: Int,
        val cellsValidated: Int,
    )

    private data class OfficialAssignment(
        val game: Int,
        val seed: Long,
        val pestSeat: PestSeat,
        val grixisSeat: PestSeat,
        val startingDeck: GrixisStartingDeck,
        val playDraw: String,
    )
}
