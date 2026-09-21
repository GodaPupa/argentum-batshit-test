package com.wingedsheep.gym.matchup

import com.wingedsheep.engine.registry.CardRegistry
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val PEST_GRIXIS_PRIVATE_SYNTHETIC_PARSER_SHA256 =
    "e8d7c4d6cca0339e6b2752449f83d23376db8c5c7904281a99ee2878a9f1148c"
const val PEST_GRIXIS_PRIVATE_SYNTHETIC_PARSER_STATUS =
    "SYNTHETIC_PARSER_VALID_OFFICIAL_BYTES_NOT_LOADED_EXECUTION_NOT_AUTHORIZED"
const val PEST_GRIXIS_SYNTHETIC_FIXTURE_BUNDLE_SHA256 =
    "0b0506b91202c8891d5dcc59d990ada4b1ac0d8308b83e593275f71f1e375897"

enum class GrixisSyntheticParserFixtureVariant {
    CANONICAL,
    DUPLICATE_SEED,
    MANIFEST_HASH_MISMATCH,
}

data class GrixisPrivateSyntheticParserInspection(
    val errors: List<String>,
    val parserSha256: String,
    val status: String,
    val syntheticBytesParsed: Boolean,
    val syntheticVectorRows: Int,
    val syntheticAssignmentRows: Int,
    val syntheticUniqueNonzeroSeeds: Int,
    val officialArtifactBytesLoaded: Boolean = false,
    val officialSeedValuesVisible: Int = 0,
    val officialAssignments: Int = PEST_GRIXIS_SMOKE_GAMES,
    val officialSeedsGenerated: Int = PEST_GRIXIS_SMOKE_GAMES,
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

/** Private parser boundary exercised only with fixed nonexperimental fixture bytes. */
object PestControlTierOneGrixisPrivateSyntheticParser {
    private const val fixtureProtocol = "NONEXPERIMENTAL_PEST_CONTROL_VS_GRIXIS_PARSER_FIXTURE_V1"
    private const val fixtureBlock =
        "NONEXPERIMENTAL_PEST_CONTROL_VS_GRIXIS_PARSER_FIXTURE_V1_SYNTHETIC_4"
    private val header = listOf(
        "protocol_id", "block_id", "game_number", "seed_decimal", "seed_hex", "pest_seat",
        "grixis_seat", "starting_deck", "pest_play_draw", "pest_main_sha256",
        "grixis_main_sha256", "grixis_sideboard_sha256", "grixis_complete75_sha256",
        "qualified_runner",
    ).joinToString(",")

    fun inspect(
        registry: CardRegistry,
        variant: GrixisSyntheticParserFixtureVariant = GrixisSyntheticParserFixtureVariant.CANONICAL,
    ): GrixisPrivateSyntheticParserInspection {
        val errors = mutableListOf<String>()
        val envelope = PestControlTierOneGrixisDisabledArtifactEnvelope.inspect(registry)
        if (!envelope.green) errors += "disabled artifact envelope is not green"
        if (envelope.envelopeSha256 != PEST_GRIXIS_DISABLED_ARTIFACT_ENVELOPE_SHA256) {
            errors += "disabled artifact envelope proof mismatch"
        }
        val fixture = buildFixture(variant)
        val fixtureBundleSha256 = sha256(fixture.vector + fixture.csv + fixture.manifest)
        if (fixtureBundleSha256 != PEST_GRIXIS_SYNTHETIC_FIXTURE_BUNDLE_SHA256) {
            errors += "synthetic fixture bundle hash mismatch"
        }
        val parsed = runCatching { parseFixture(fixture) }.getOrElse {
            errors += "synthetic fixture parse failure"
            ParsedFixture(emptyList(), emptyList())
        }
        if (parsed.seeds.size != PEST_GRIXIS_SMOKE_GAMES) errors += "synthetic vector row count mismatch"
        if (parsed.assignments.size != PEST_GRIXIS_SMOKE_GAMES) {
            errors += "synthetic assignment row count mismatch"
        }
        val uniqueNonzero = parsed.seeds.filter { it != 0L }.distinct().size
        if (uniqueNonzero != PEST_GRIXIS_SMOKE_GAMES) errors += "synthetic seeds are not unique nonzero"
        if (parsed.assignments.map { it.seed } != parsed.seeds) {
            errors += "synthetic assignment/vector seed mismatch"
        }

        val proofBytes = listOf(
            "pest-control-tier-one-grixis-private-synthetic-parser-v1",
            "status=$PEST_GRIXIS_PRIVATE_SYNTHETIC_PARSER_STATUS",
            "envelopeSha256=${envelope.envelopeSha256}",
            "fixtureClassification=NONEXPERIMENTAL_FIXTURE",
            "parserSchema=grixis-smoke-artifact-parser@v1",
            "syntheticFixtureBundleSha256=$fixtureBundleSha256",
            "syntheticBytesParsed=${parsed.seeds.isNotEmpty() && parsed.assignments.isNotEmpty()}",
            "syntheticVectorRows=${parsed.seeds.size}",
            "syntheticAssignmentRows=${parsed.assignments.size}",
            "syntheticUniqueNonzeroSeeds=$uniqueNonzero",
            "officialArtifactBytesLoaded=false",
            "officialSeedValuesVisible=0",
            "officialAssignments=4",
            "officialSeedsGenerated=4",
            "initializerEnabled=false",
            "runnerEnabled=false",
            "officialGamesAuthorized=0",
            "officialGamesInitialized=0",
            "submittedActions=0",
            "artifactsWithOutcomes=0",
            "outcomeExposure=0",
        ).joinToString("\n", postfix = "\n").toByteArray()
        val parserSha256 = sha256(proofBytes)
        if (parserSha256 != PEST_GRIXIS_PRIVATE_SYNTHETIC_PARSER_SHA256) {
            errors += "private synthetic parser hash mismatch"
        }
        return GrixisPrivateSyntheticParserInspection(
            errors = errors.distinct(),
            parserSha256 = parserSha256,
            status = PEST_GRIXIS_PRIVATE_SYNTHETIC_PARSER_STATUS,
            syntheticBytesParsed = parsed.seeds.isNotEmpty() && parsed.assignments.isNotEmpty(),
            syntheticVectorRows = parsed.seeds.size,
            syntheticAssignmentRows = parsed.assignments.size,
            syntheticUniqueNonzeroSeeds = uniqueNonzero,
        )
    }

    private fun buildFixture(variant: GrixisSyntheticParserFixtureVariant): SyntheticFixture {
        val canonicalSeeds = (1L..4L).map { 0x4752_4958_0000_0000L + it }
        val seeds = if (variant == GrixisSyntheticParserFixtureVariant.DUPLICATE_SEED) {
            canonicalSeeds.dropLast(1) + canonicalSeeds.first()
        } else {
            canonicalSeeds
        }
        val vector = seeds.joinToString("\n", postfix = "\n").toByteArray()
        val cells = listOf(
            listOf("SEAT_ZERO", "SEAT_ONE", "PEST_CONTROL", "PLAY"),
            listOf("SEAT_ZERO", "SEAT_ONE", "GRIXIS_AFFINITY", "DRAW"),
            listOf("SEAT_ONE", "SEAT_ZERO", "PEST_CONTROL", "PLAY"),
            listOf("SEAT_ONE", "SEAT_ZERO", "GRIXIS_AFFINITY", "DRAW"),
        )
        val csv = buildString {
            appendLine(header)
            seeds.forEachIndexed { index, seed ->
                val cell = cells[index]
                appendLine(
                    listOf(
                        fixtureProtocol, fixtureBlock, index + 1, seed,
                        "0x${seed.toULong().toString(16).padStart(16, '0')}",
                        cell[0], cell[1], cell[2], cell[3], PEST_CONTROL_V10_HASH,
                        PEST_GRIXIS_MAIN_SHA256, PEST_GRIXIS_SIDEBOARD_SHA256,
                        PEST_GRIXIS_COMPLETE_75_SHA256, PEST_V2_QUALIFIED_RUNNER,
                    ).joinToString(","),
                )
            }
        }.toByteArray()
        val assignmentHash = if (variant == GrixisSyntheticParserFixtureVariant.MANIFEST_HASH_MISMATCH) {
            "0".repeat(64)
        } else {
            sha256(csv)
        }
        val manifest = (
            "{\"artifact_hashes\":{\"assignment_csv_sha256\":\"$assignmentHash\"," +
                "\"ordered_vector_sha256\":\"${sha256(vector)}\"}," +
                "\"block_id\":\"$fixtureBlock\",\"classification\":\"NONEXPERIMENTAL_FIXTURE\"," +
                "\"protocol_id\":\"$fixtureProtocol\",\"qualified_runner\":\"$PEST_V2_QUALIFIED_RUNNER\"," +
                "\"runner_state\":\"DISABLED\",\"status\":\"NONEXPERIMENTAL_FIXTURE\"}\n"
            ).toByteArray()
        return SyntheticFixture(vector, csv, manifest)
    }

    private fun parseFixture(fixture: SyntheticFixture): ParsedFixture {
        val vectorText = fixture.vector.decodeToString()
        require(vectorText.isNotEmpty() && !vectorText.contains('\r') && vectorText.endsWith('\n'))
        val seeds = vectorText.trimEnd('\n').lines().map(String::toLong)
        val csvText = fixture.csv.decodeToString()
        require(csvText.isNotEmpty() && !csvText.contains('\r') && csvText.endsWith('\n'))
        val lines = csvText.trimEnd('\n').lines()
        require(lines.first() == header)
        val assignments = lines.drop(1).map { line ->
            val columns = line.split(',')
            require(columns.size == 14)
            require(columns[0] == fixtureProtocol && columns[1] == fixtureBlock)
            require(columns[9] == PEST_CONTROL_V10_HASH)
            require(columns[10] == PEST_GRIXIS_MAIN_SHA256)
            require(columns[11] == PEST_GRIXIS_SIDEBOARD_SHA256)
            require(columns[12] == PEST_GRIXIS_COMPLETE_75_SHA256)
            require(columns[13] == PEST_V2_QUALIFIED_RUNNER)
            val seed = columns[3].toLong()
            require(columns[4] == "0x${seed.toULong().toString(16).padStart(16, '0')}")
            require(PestSeat.valueOf(columns[5]) != PestSeat.valueOf(columns[6]))
            val starter = GrixisStartingDeck.valueOf(columns[7])
            require(columns[8] == if (starter == GrixisStartingDeck.PEST_CONTROL) "PLAY" else "DRAW")
            SyntheticAssignment(columns[2].toInt(), seed, columns[5], columns[6], starter.name)
        }
        require(assignments.map { it.game } == (1..PEST_GRIXIS_SMOKE_GAMES).toList())
        val manifest = Json.parseToJsonElement(fixture.manifest.decodeToString()).jsonObject
        require(manifest.getValue("status").jsonPrimitive.content == "NONEXPERIMENTAL_FIXTURE")
        require(manifest.getValue("classification").jsonPrimitive.content == "NONEXPERIMENTAL_FIXTURE")
        require(manifest.getValue("protocol_id").jsonPrimitive.content == fixtureProtocol)
        require(manifest.getValue("block_id").jsonPrimitive.content == fixtureBlock)
        require(manifest.getValue("qualified_runner").jsonPrimitive.content == PEST_V2_QUALIFIED_RUNNER)
        require(manifest.getValue("runner_state").jsonPrimitive.content == "DISABLED")
        val hashes = manifest.getValue("artifact_hashes").jsonObject
        require(hashes.getValue("ordered_vector_sha256").jsonPrimitive.content == sha256(fixture.vector))
        require(hashes.getValue("assignment_csv_sha256").jsonPrimitive.content == sha256(fixture.csv))
        return ParsedFixture(seeds, assignments)
    }

    private data class SyntheticFixture(
        val vector: ByteArray,
        val csv: ByteArray,
        val manifest: ByteArray,
    )

    private data class ParsedFixture(
        val seeds: List<Long>,
        val assignments: List<SyntheticAssignment>,
    )

    private data class SyntheticAssignment(
        val game: Int,
        val seed: Long,
        val pestSeat: String,
        val grixisSeat: String,
        val starter: String,
    )
}
