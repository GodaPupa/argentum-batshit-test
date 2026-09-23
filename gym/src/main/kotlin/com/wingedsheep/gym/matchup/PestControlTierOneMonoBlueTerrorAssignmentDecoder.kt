package com.wingedsheep.gym.matchup

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections
import java.util.zip.ZipInputStream

internal const val TERROR_ASSIGNMENT_HEADER =
    "protocol_id,block_id,game_number,seed_decimal,seed_hex,pest_seat,terror_seat," +
        "starting_deck,pest_play_draw,pest_main_sha256,terror_main_sha256," +
        "terror_sideboard_sha256,terror_complete75_sha256,qualified_runner"

/** Decodes only the sealed archive. Reading assignments does not authorize their execution. */
internal object PestControlTierOneMonoBlueTerrorAssignmentDecoder {
    fun decode(archive: ByteArray): List<MonoBlueTerrorSmokeAssignment> {
        require(archive.size in 1..TERROR_FROZEN_MAX_ARCHIVE_BYTES) { "archive size outside bounds" }
        val snapshot = archive.copyOf()
        val inspected = PestControlTierOneMonoBlueTerrorFrozenArtifactVerifier.inspect(snapshot)
        require(inspected.verified) { inspected.errors.joinToString("; ") }
        val members = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(snapshot)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == "ordered-seeds.txt" || entry.name == "assignments.csv") {
                    // The immutable snapshot already passed the total expanded-byte limit.
                    members[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
            }
        }
        return decodeTerrorAssignmentText(
            strictTerrorUtf8(members.getValue("ordered-seeds.txt")),
            strictTerrorUtf8(members.getValue("assignments.csv")),
        )
    }
}

internal fun strictTerrorUtf8(bytes: ByteArray): String = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes)).toString()
} catch (failure: java.nio.charset.CharacterCodingException) {
    throw IllegalArgumentException("invalid UTF-8", failure)
}

/** Internal parser for synthetic tests; it neither loads files nor grants execution authority. */
internal fun decodeTerrorAssignmentText(
    vectorText: String,
    assignmentText: String,
): List<MonoBlueTerrorSmokeAssignment> {
    fun lines(text: String): List<String> {
        require(text.endsWith("\n") && '\r' !in text && '\u0000' !in text) {
            "noncanonical LF text"
        }
        return text.dropLast(1).split('\n')
    }
    val vector = lines(vectorText)
    require(vector.size == 4) { "vector must contain four seeds" }
    val seeds = vector.map { token ->
        val seed = token.toLongOrNull()
        require(seed != null && seed != 0L && seed.toString() == token) {
            "invalid canonical nonzero signed seed"
        }
        seed
    }
    require(seeds.distinct().size == 4) { "duplicate seed" }
    val rows = lines(assignmentText)
    require(rows.size == 5 && rows.first() == TERROR_ASSIGNMENT_HEADER) { "CSV shape/header mismatch" }
    val assignments = rows.drop(1).mapIndexed { index, line ->
        require('"' !in line) { "quoted fields are not canonical" }
        val fields = line.split(',')
        require(fields.size == 14) { "assignment column count mismatch" }
        val pestSeat = if (index < 2) PestSeat.SEAT_ZERO else PestSeat.SEAT_ONE
        val terrorSeat = if (index < 2) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO
        val starter = if (index % 2 == 0) {
            MonoBlueTerrorStartingDeck.PEST_CONTROL
        } else {
            MonoBlueTerrorStartingDeck.MONO_BLUE_TERROR
        }
        val expected = listOf(
            PEST_MONO_BLUE_TERROR_PREBOARD_PROTOCOL_ID,
            PEST_MONO_BLUE_TERROR_SMOKE_BLOCK_ID,
            (index + 1).toString(), seeds[index].toString(), terrorAssignmentSeedHex(seeds[index]),
            pestSeat.name, terrorSeat.name, starter.name,
            if (index % 2 == 0) "PLAY" else "DRAW",
            PEST_CONTROL_V10_HASH, PEST_MONO_BLUE_TERROR_MAIN_SHA256,
            PEST_MONO_BLUE_TERROR_SIDEBOARD_SHA256, PEST_MONO_BLUE_TERROR_COMPLETE_75_SHA256,
            PEST_V2_QUALIFIED_RUNNER,
        )
        require(fields == expected) { "assignment identity mismatch at game ${index + 1}" }
        MonoBlueTerrorSmokeAssignment(index + 1, seeds[index], fields[4], pestSeat, terrorSeat, starter)
    }
    return Collections.unmodifiableList(assignments)
}

internal fun terrorAssignmentSeedHex(seed: Long): String =
    "0x${seed.toULong().toString(16).padStart(16, '0')}"
