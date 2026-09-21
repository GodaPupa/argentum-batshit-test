package com.wingedsheep.gym.matchup

import java.nio.file.Files
import java.nio.file.Path

const val PEST_GRIXIS_EXECUTION_INPUT_ACK =
    "LOAD_FROZEN_TIER_ONE_GRIXIS_4_FOR_VALIDATION_ONLY"

data class GrixisOfficialExecutionInput(
    val vectorIdentity: GrixisSmokeVectorIdentity,
    val seeds: List<Long>,
    val assignments: List<GrixisSmokeAssignment>,
)

object PestControlTierOneGrixisOfficialExecutionInputLoader {
    fun loadValidatedFromEnvironment(): GrixisOfficialExecutionInput {
        require(System.getenv("PEST_GRIXIS_EXECUTION_INPUT_ACK") == PEST_GRIXIS_EXECUTION_INPUT_ACK)
        val dir = Path.of(
            System.getenv("PEST_GRIXIS_EXECUTION_INPUT_DIR")
                ?: error("PEST_GRIXIS_EXECUTION_INPUT_DIR required"),
        )
        require(Files.isDirectory(dir))
        val vector = Files.readAllBytes(dir.resolve("ordered-seeds.txt"))
        val csv = Files.readAllBytes(dir.resolve("assignments.csv"))
        val manifest = Files.readAllBytes(dir.resolve("freeze-manifest.json"))
        val quarantine = Files.readAllBytes(dir.resolve("quarantined-vector.json"))
        val inventory = Files.readAllBytes(dir.resolve("artifacts.sha256"))

        require(sha256(vector) == PEST_GRIXIS_FROZEN_VECTOR_SHA256)
        require(sha256(csv) == PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256)
        require(sha256(manifest) == PEST_GRIXIS_FROZEN_MANIFEST_SHA256)
        require(sha256(quarantine) == PEST_GRIXIS_FROZEN_QUARANTINE_SHA256)
        require(sha256(inventory) == PEST_GRIXIS_FROZEN_CHECKSUM_INVENTORY_SHA256)

        val seeds = parseVector(vector)
        val assignments = parseAssignments(csv)
        require(seeds.size == PEST_GRIXIS_SMOKE_GAMES)
        require(seeds.distinct().size == seeds.size && seeds.none { it == 0L })
        require(assignments.size == PEST_GRIXIS_SMOKE_GAMES)
        require(assignments.map { it.seed } == seeds)
        require(assignments.map { it.gameNumber } == (1..PEST_GRIXIS_SMOKE_GAMES).toList())

        val identity = GrixisSmokeVectorIdentity(
            freezeCommit = "6465548adfa7039ff02edb8834e33318231903f6",
            orderedVectorSha256 = PEST_GRIXIS_FROZEN_VECTOR_SHA256,
            assignmentCsvSha256 = PEST_GRIXIS_FROZEN_ASSIGNMENTS_SHA256,
            freezeManifestSha256 = PEST_GRIXIS_FROZEN_MANIFEST_SHA256,
        )
        assignments.zip(PestControlTierOneGrixisSmokeHarness.cellTemplate()).forEach { (a, cell) ->
            require(a.gameNumber == cell.gameNumber)
            require(a.pestSeat == cell.pestSeat)
            require(a.startingDeck == cell.startingDeck)
            require(a.grixisSeat != a.pestSeat)
        }
        return GrixisOfficialExecutionInput(identity, seeds, assignments)
    }

    internal fun parseVector(bytes: ByteArray): List<Long> {
        val text = bytes.decodeToString()
        require(text.isNotEmpty() && !text.contains('\r') && text.endsWith('\n'))
        return text.trimEnd('\n').lines().map(String::toLong)
    }

    internal fun parseAssignments(bytes: ByteArray): List<GrixisSmokeAssignment> {
        val text = bytes.decodeToString()
        require(text.isNotEmpty() && !text.contains('\r') && text.endsWith('\n'))
        val lines = text.trimEnd('\n').lines()
        val header = "protocol_id,block_id,game_number,seed_decimal,seed_hex,pest_seat,grixis_seat,starting_deck,pest_play_draw,pest_main_sha256,grixis_main_sha256,grixis_sideboard_sha256,grixis_complete75_sha256,qualified_runner"
        require(lines.first() == header)
        return lines.drop(1).map { line ->
            val c = line.split(',')
            require(c.size == 14)
            require(c[0] == PEST_GRIXIS_PREBOARD_PROTOCOL_ID)
            require(c[1] == PEST_GRIXIS_SMOKE_BLOCK_ID)
            require(c[9] == PEST_CONTROL_V10_HASH)
            require(c[10] == PEST_GRIXIS_MAIN_SHA256)
            require(c[11] == PEST_GRIXIS_SIDEBOARD_SHA256)
            require(c[12] == PEST_GRIXIS_COMPLETE_75_SHA256)
            require(c[13] == PEST_V2_QUALIFIED_RUNNER)
            val seed = c[3].toLong()
            require(c[4] == "0x${seed.toULong().toString(16).padStart(16, '0')}")
            GrixisSmokeAssignment(
                gameNumber = c[2].toInt(),
                seed = seed,
                seedHex = c[4],
                pestSeat = PestSeat.valueOf(c[5]),
                grixisSeat = PestSeat.valueOf(c[6]),
                startingDeck = GrixisStartingDeck.valueOf(c[7]),
            )
        }
    }
}
