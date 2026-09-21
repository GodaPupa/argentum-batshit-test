package com.wingedsheep.gym.matchup

import java.nio.file.Files
import java.nio.file.Path

const val PEST_GRIXIS_REPLICATION_GAMES = 12
const val PEST_GRIXIS_REPLICATION_BLOCK_ID =
    "${PEST_GRIXIS_PREBOARD_PROTOCOL_ID}_REPLICATION_12"
const val PEST_GRIXIS_REPLICATION_VECTOR_SHA256 =
    "5cd8a78fb62a59495d07ed31c4579fab7bafe2bc9c075aa7757a7f67953c75d4"
const val PEST_GRIXIS_REPLICATION_ASSIGNMENTS_SHA256 =
    "d85a30dcf46132609bdcd29d3a2b2e8621dba82b4e2111d95a385fb7642b4fab"
const val PEST_GRIXIS_REPLICATION_MANIFEST_SHA256 =
    "5adea1d05defd4232d5117822c5a0afdf01532d75e05f180ea488b1b83e41a65"
const val PEST_GRIXIS_REPLICATION_EXECUTE_ACK =
    "EXECUTE_FROZEN_TIER_ONE_GRIXIS_REPLICATION_12_EXACTLY_ONCE"

data class GrixisReplicationExecutionInput(
    val vectorIdentity: GrixisSmokeVectorIdentity,
    val seeds: List<Long>,
    val assignments: List<GrixisSmokeAssignment>,
)

object PestControlTierOneGrixisReplicationExecutionInputLoader {
    fun loadForAuthorizedExecution(dir: Path): GrixisReplicationExecutionInput {
        require(Files.isDirectory(dir))
        val vector = Files.readAllBytes(dir.resolve("ordered-seeds.txt"))
        val csv = Files.readAllBytes(dir.resolve("assignments.csv"))
        val manifest = Files.readAllBytes(dir.resolve("freeze-manifest.json"))

        require(sha256(vector) == PEST_GRIXIS_REPLICATION_VECTOR_SHA256)
        require(sha256(csv) == PEST_GRIXIS_REPLICATION_ASSIGNMENTS_SHA256)
        require(sha256(manifest) == PEST_GRIXIS_REPLICATION_MANIFEST_SHA256)

        val seeds = parseVector(vector)
        val assignments = parseAssignments(csv)
        require(seeds.size == PEST_GRIXIS_REPLICATION_GAMES)
        require(seeds.distinct().size == seeds.size && seeds.none { it == 0L })
        require(assignments.size == PEST_GRIXIS_REPLICATION_GAMES)
        require(assignments.map { it.seed } == seeds)
        require(assignments.map { it.gameNumber } == (1..PEST_GRIXIS_REPLICATION_GAMES).toList())

        val cells = replicationCells()
        assignments.zip(cells).forEach { (a, cell) ->
            require(a.gameNumber == cell.gameNumber)
            require(a.pestSeat == cell.pestSeat)
            require(a.startingDeck == cell.startingDeck)
            require(a.grixisSeat != a.pestSeat)
        }

        return GrixisReplicationExecutionInput(
            vectorIdentity = GrixisSmokeVectorIdentity(
                freezeCommit = "5234db81bc87b6061bc3dfb891544205e66acc93",
                orderedVectorSha256 = PEST_GRIXIS_REPLICATION_VECTOR_SHA256,
                assignmentCsvSha256 = PEST_GRIXIS_REPLICATION_ASSIGNMENTS_SHA256,
                freezeManifestSha256 = PEST_GRIXIS_REPLICATION_MANIFEST_SHA256,
            ),
            seeds = seeds,
            assignments = assignments,
        )
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
            require(c[1] == PEST_GRIXIS_REPLICATION_BLOCK_ID)
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

    internal fun replicationCells(): List<GrixisSmokeCell> =
        List(3) { PestControlTierOneGrixisSmokeHarness.cellTemplate() }.flatten()
            .mapIndexed { index, cell -> cell.copy(gameNumber = index + 1) }
}
