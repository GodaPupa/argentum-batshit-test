package com.wingedsheep.gym.matchup

import java.nio.file.Files
import java.nio.file.Path

const val PEST_MONO_BLUE_TERROR_REPLICATION_GAMES = 12
const val PEST_MONO_BLUE_TERROR_REPLICATION_BLOCK_ID =
    "${PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID}_REPLICATION_12"
const val PEST_MONO_BLUE_TERROR_REPLICATION_VECTOR_SHA256 =
    "445542e6cdf9902cc435b4db276a747e6b2200ff4f24ec9ac896b517a44bd34d"
const val PEST_MONO_BLUE_TERROR_REPLICATION_ASSIGNMENTS_SHA256 =
    "24c1ce43362dbb2ae61fa79926437182fb74d0b76ff28a6ec44d040c2a434b70"
const val PEST_MONO_BLUE_TERROR_REPLICATION_MANIFEST_SHA256 =
    "ad10e52e6b64d85aa4d890ff772aa31a271435ed100564490f63ec9afc1d6862"
const val PEST_MONO_BLUE_TERROR_REPLICATION_QUARANTINE_SHA256 =
    "ce4a2d5a64eef96357b9bf1a0bbe558c711512a362c7052ff6091dc3b4f1d7be"
const val PEST_MONO_BLUE_TERROR_REPLICATION_CHECKSUMS_SHA256 =
    "1e42a4045960e2cc967b4bc85681d9f21d2558b940524aef4db0fe7c9e05f3d3"
const val PEST_MONO_BLUE_TERROR_REPLICATION_FREEZE_COMMIT =
    "e418f746b4d19457aa7ef748329c8a25f40e92c8"

data class MonoBlueTerrorReplicationExecutionInput(
    val vectorIdentity: MonoBlueTerrorSmokeVectorIdentity,
    val seeds: List<Long>,
    val assignments: List<MonoBlueTerrorSmokeAssignment>,
)

object PestControlTierOneMonoBlueTerrorReplicationExecutionInputLoader {
    fun loadValidated(dir: Path): MonoBlueTerrorReplicationExecutionInput {
        require(Files.isDirectory(dir)) { "replication input directory is absent" }
        val vector = Files.readAllBytes(dir.resolve("ordered-seeds.txt"))
        val csv = Files.readAllBytes(dir.resolve("assignments.csv"))
        val manifest = Files.readAllBytes(dir.resolve("freeze-manifest.json"))
        val quarantine = Files.readAllBytes(dir.resolve("quarantined-vector.json"))
        val inventory = Files.readAllBytes(dir.resolve("artifacts.sha256"))

        require(sha256(vector) == PEST_MONO_BLUE_TERROR_REPLICATION_VECTOR_SHA256)
        require(sha256(csv) == PEST_MONO_BLUE_TERROR_REPLICATION_ASSIGNMENTS_SHA256)
        require(sha256(manifest) == PEST_MONO_BLUE_TERROR_REPLICATION_MANIFEST_SHA256)
        require(sha256(quarantine) == PEST_MONO_BLUE_TERROR_REPLICATION_QUARANTINE_SHA256)
        require(sha256(inventory) == PEST_MONO_BLUE_TERROR_REPLICATION_CHECKSUMS_SHA256)

        val seeds = parseVector(vector)
        val assignments = parseAssignments(csv)
        require(seeds.size == PEST_MONO_BLUE_TERROR_REPLICATION_GAMES)
        require(seeds.distinct().size == seeds.size && seeds.none { it == 0L })
        require(assignments.size == PEST_MONO_BLUE_TERROR_REPLICATION_GAMES)
        require(assignments.map { it.seed } == seeds)
        require(assignments.map { it.gameNumber } == (1..PEST_MONO_BLUE_TERROR_REPLICATION_GAMES).toList())

        assignments.zip(replicationCells()).forEach { (assignment, cell) ->
            require(assignment.gameNumber == cell.gameNumber)
            require(assignment.pestSeat == cell.pestSeat)
            require(assignment.startingDeck == cell.startingDeck)
            require(assignment.terrorSeat != assignment.pestSeat)
        }

        return MonoBlueTerrorReplicationExecutionInput(
            vectorIdentity = MonoBlueTerrorSmokeVectorIdentity(
                freezeCommit = PEST_MONO_BLUE_TERROR_REPLICATION_FREEZE_COMMIT,
                orderedVectorSha256 = PEST_MONO_BLUE_TERROR_REPLICATION_VECTOR_SHA256,
                assignmentCsvSha256 = PEST_MONO_BLUE_TERROR_REPLICATION_ASSIGNMENTS_SHA256,
                freezeManifestSha256 = PEST_MONO_BLUE_TERROR_REPLICATION_MANIFEST_SHA256,
            ),
            seeds = seeds.toList(),
            assignments = assignments.toList(),
        )
    }

    internal fun parseVector(bytes: ByteArray): List<Long> {
        val text = bytes.decodeToString()
        require(text.isNotEmpty() && !text.contains('\r') && text.endsWith('\n'))
        return text.trimEnd('\n').lines().map { token ->
            val seed = token.toLong()
            require(seed.toString() == token && seed != 0L)
            seed
        }
    }

    internal fun parseAssignments(bytes: ByteArray): List<MonoBlueTerrorSmokeAssignment> {
        val text = bytes.decodeToString()
        require(text.isNotEmpty() && !text.contains('\r') && text.endsWith('\n'))
        val lines = text.trimEnd('\n').lines()
        val header =
            "protocol_id,block_id,game_number,seed_decimal,seed_hex,pest_seat,terror_seat," +
                "starting_deck,pest_play_draw,pest_main_sha256,terror_main_sha256," +
                "terror_sideboard_sha256,terror_complete75_sha256,qualified_runner"
        require(lines.first() == header)
        return lines.drop(1).map { line ->
            require('"' !in line)
            val c = line.split(',')
            require(c.size == 14)
            require(c[0] == PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID)
            require(c[1] == PEST_MONO_BLUE_TERROR_REPLICATION_BLOCK_ID)
            require(c[9] == PEST_CONTROL_V10_HASH)
            require(c[10] == PEST_MONO_BLUE_TERROR_MAIN_SHA256)
            require(c[11] == PEST_MONO_BLUE_TERROR_SIDEBOARD_SHA256)
            require(c[12] == PEST_MONO_BLUE_TERROR_COMPLETE_75_SHA256)
            require(c[13] == PEST_V2_QUALIFIED_RUNNER)

            val seed = c[3].toLong()
            require(seed != 0L)
            require(c[4] == terrorAssignmentSeedHex(seed))
            val starter = MonoBlueTerrorStartingDeck.valueOf(c[7])
            require(c[8] == if (starter == MonoBlueTerrorStartingDeck.PEST_CONTROL) "PLAY" else "DRAW")

            MonoBlueTerrorSmokeAssignment(
                gameNumber = c[2].toInt(),
                seed = seed,
                seedHex = c[4],
                pestSeat = PestSeat.valueOf(c[5]),
                terrorSeat = PestSeat.valueOf(c[6]),
                startingDeck = starter,
            )
        }
    }

    internal fun replicationCells(): List<MonoBlueTerrorSmokeCell> =
        List(3) { PestControlTierOneMonoBlueTerrorSmokeHarness.cellTemplate() }
            .flatten()
            .mapIndexed { index, cell -> cell.copy(gameNumber = index + 1) }
}
