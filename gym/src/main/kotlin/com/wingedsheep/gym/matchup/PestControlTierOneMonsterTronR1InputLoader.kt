package com.wingedsheep.gym.matchup

import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Collections
import java.util.zip.ZipInputStream

const val PEST_MONSTER_TRON_R1_INPUT_VALIDATE_ACK =
    "LOAD_FROZEN_MONSTER_TRON_R1_FOR_VALIDATION_ONLY"
const val PEST_MONSTER_TRON_R1_INPUT_EXECUTE_ACK =
    "AUTOMATIC_ONE_SHOT_MONSTER_TRON_R1_EXECUTION"

internal const val MONSTER_TRON_R1_ASSIGNMENT_HEADER =
    "protocol_id,block_id,game_number,seed_decimal,seed_hex,pest_seat,monster_tron_seat," +
        "starting_deck,pest_play_draw,pest_main_sha256,monster_tron_main_sha256,qualified_runner"

internal fun monsterTronR1InputMemberPins(): Map<String, String> = linkedMapOf(
    "ordered-seeds.txt" to PEST_MONSTER_TRON_R1_VECTOR_SHA256,
    "assignments.csv" to PEST_MONSTER_TRON_R1_INPUT_ASSIGNMENTS_SHA256,
    "freeze-manifest.json" to PEST_MONSTER_TRON_R1_FREEZE_MANIFEST_SHA256,
    "quarantined-vector.json" to PEST_MONSTER_TRON_R1_QUARANTINE_SHA256,
    "artifacts.sha256" to PEST_MONSTER_TRON_R1_INPUT_CHECKSUMS_SHA256,
)

object PestControlTierOneMonsterTronR1InputLoader {
    fun loadValidatedFromEnvironment(): MonsterTronOfficialExecutionInput {
        require(
            System.getenv("PEST_MONSTER_TRON_R1_EXECUTION_INPUT_ACK") ==
                PEST_MONSTER_TRON_R1_INPUT_VALIDATE_ACK
        ) { "exact R1 validation acknowledgement is required" }
        return loadFromEnvironment()
    }

    fun loadForAuthorizedExecutionFromEnvironment(): MonsterTronOfficialExecutionInput {
        require(
            System.getenv("PEST_MONSTER_TRON_R1_EXECUTION_INPUT_ACK") ==
                PEST_MONSTER_TRON_R1_INPUT_EXECUTE_ACK
        ) { "exact R1 execution acknowledgement is required" }
        return loadFromEnvironment()
    }

    private fun loadFromEnvironment(): MonsterTronOfficialExecutionInput {
        val path = Path.of(
            System.getenv("PEST_MONSTER_TRON_R1_EXECUTION_INPUT_ZIP")
                ?: error("PEST_MONSTER_TRON_R1_EXECUTION_INPUT_ZIP required")
        )
        require(Files.isRegularFile(path)) { "R1 frozen ZIP is absent" }
        return loadValidatedArchive(Files.readAllBytes(path))
    }

    fun loadValidatedArchive(archive: ByteArray): MonsterTronOfficialExecutionInput {
        val snapshot = archive.copyOf()
        val inspection = inspectMonsterTronPinnedArchive(
            archive = snapshot,
            expectedArchiveSha256 = PEST_MONSTER_TRON_R1_ARCHIVE_SHA256,
            expectedMembers = monsterTronR1InputMemberPins(),
        )
        require(inspection.verified) {
            "R1 frozen artifact verification failed: ${inspection.errors.joinToString()}"
        }

        val members = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(snapshot)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "ordered-seeds.txt" || entry.name == "assignments.csv") {
                    members[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        val assignments = decodeMonsterTronR1AssignmentText(
            strictMonsterTronUtf8(members.getValue("ordered-seeds.txt")),
            strictMonsterTronUtf8(members.getValue("assignments.csv")),
        )
        val seeds = assignments.map { it.seed }
        require(
            seeds == listOf(
                8509670981736218459L,
                -8564666863904712979L,
                -8259800499619080267L,
                3489680325849498530L,
            )
        )
        require(
            monsterTronDigest(
                seeds.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8)
            ) == PEST_MONSTER_TRON_R1_VECTOR_SHA256
        )
        require(
            monsterTronDigest(authoritativeR1Assignments(assignments).toByteArray(Charsets.UTF_8)) ==
                PEST_MONSTER_TRON_R1_ASSIGNMENTS_SHA256
        )

        return MonsterTronOfficialExecutionInput(
            vectorIdentity = MonsterTronSmokeVectorIdentity(
                freezeCommit = PEST_MONSTER_TRON_R1_FREEZE_COMMIT,
                orderedVectorSha256 = PEST_MONSTER_TRON_R1_VECTOR_SHA256,
                assignmentCsvSha256 = PEST_MONSTER_TRON_R1_ASSIGNMENTS_SHA256,
                freezeManifestSha256 = PEST_MONSTER_TRON_R1_FREEZE_MANIFEST_SHA256,
            ),
            seeds = seeds.toList(),
            assignments = assignments.toList(),
            archiveSha256 = inspection.archiveSha256,
        )
    }
}

internal fun decodeMonsterTronR1AssignmentText(
    vectorText: String,
    assignmentText: String,
): List<MonsterTronSmokeAssignment> {
    fun lines(text: String): List<String> {
        require(text.endsWith("\n") && '\r' !in text && '\u0000' !in text) {
            "noncanonical LF text"
        }
        return text.dropLast(1).split('\n')
    }

    val vector = lines(vectorText)
    require(vector.size == 4)
    val seeds = vector.map { token ->
        val seed = token.toLongOrNull()
        require(seed != null && seed != 0L && seed.toString() == token) {
            "invalid canonical R1 seed"
        }
        seed
    }
    require(seeds.distinct().size == 4)

    val rows = lines(assignmentText)
    require(rows.size == 5)
    require(rows.first() == MONSTER_TRON_R1_ASSIGNMENT_HEADER)
    val assignments = rows.drop(1).mapIndexed { index, line ->
        require('"' !in line)
        val fields = line.split(',')
        require(fields.size == 12)

        val pestSeat = if (index < 2) PestSeat.SEAT_ZERO else PestSeat.SEAT_ONE
        val tronSeat = if (index < 2) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO
        val starter =
            if (index % 2 == 0) MonsterTronStartingDeck.PEST_CONTROL
            else MonsterTronStartingDeck.MONSTER_TRON
        val expected = listOf(
            PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID,
            PEST_MONSTER_TRON_R1_BLOCK_ID,
            (index + 1).toString(),
            seeds[index].toString(),
            monsterTronSeedHex(seeds[index]),
            pestSeat.name,
            tronSeat.name,
            starter.name,
            if (index % 2 == 0) "PLAY" else "DRAW",
            PEST_CONTROL_V10_HASH,
            PEST_MONSTER_TRON_MAIN_SHA256,
            PEST_V2_QUALIFIED_RUNNER,
        )
        require(fields == expected) { "R1 assignment identity mismatch at game ${index + 1}" }
        MonsterTronSmokeAssignment(
            gameNumber = index + 1,
            seed = seeds[index],
            seedHex = fields[4],
            pestSeat = pestSeat,
            monsterTronSeat = tronSeat,
            startingDeck = starter,
        )
    }
    return Collections.unmodifiableList(assignments)
}

internal fun authoritativeR1Assignments(assignments: List<MonsterTronSmokeAssignment>): String =
    buildString {
        appendLine(
            "r1_block,game_number,seed_decimal,seed_hex,pest_seat,monster_tron_seat," +
                "starting_deck,pest_play_draw,pest_main_sha256,monster_tron_main_sha256"
        )
        assignments.forEach { row ->
            appendLine(
                listOf(
                    PEST_MONSTER_TRON_R1_BLOCK_ID,
                    row.gameNumber.toString(),
                    row.seed.toString(),
                    row.seedHex,
                    row.pestSeat.index.toString(),
                    row.monsterTronSeat.index.toString(),
                    row.startingDeck.name,
                    if (row.startingDeck == MonsterTronStartingDeck.PEST_CONTROL) "PLAY" else "DRAW",
                    PEST_CONTROL_V10_HASH,
                    PEST_MONSTER_TRON_MAIN_SHA256,
                ).joinToString(",")
            )
        }
    }
