package com.wingedsheep.gym.matchup

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

const val PEST_V2_QUALIFICATION_SHARD_ALLOCATION_SHA256 =
    "2cd9fa2a74a8b03a0ecb4c0b35196810cde066c7a93cc8394c3e2c60573d2c64"

data class V2QualificationAssignment(
    val game: Int,
    val seed: Long,
    val seedHex: String,
    val pestSeat: PestSeat,
    val redSeat: PestSeat,
    val starter: StartingDeck,
    val playDraw: String,
)

data class V2QualificationPreflightResult(
    val errors: List<String>,
    val seeds: List<Long>,
    val assignments: List<V2QualificationAssignment>,
)

/** Pure artifact inspection. This object cannot initialize a game or authorize a runner. */
object PestControlV2QualificationExecutionPreflight {
    private val expectedHeader = listOf(
        "protocol_id", "block_id", "game_number", "seed_decimal", "seed_hex", "pest_seat",
        "mono_red_seat", "starting_player", "pest_play_draw", "pest_main_sha256",
        "pest_sideboard_sha256", "pest_complete75_sha256", "mono_red_main_sha256",
        "mono_red_sideboard_sha256", "mono_red_complete75_sha256", "qualified_runner",
    ).joinToString(",")

    fun inspect(
        vector: ByteArray,
        csv: ByteArray,
        manifest: ByteArray,
        shardAllocation: ByteArray,
    ): V2QualificationPreflightResult {
        val errors = mutableListOf<String>()
        val freeze = PEST_V2_QUALIFICATION_FREEZE_IDENTITY
        if (sha256(vector) != freeze.orderedVectorSha256) errors += "ordered vector hash mismatch"
        if (sha256(csv) != freeze.assignmentCsvSha256) errors += "assignment CSV hash mismatch"
        if (sha256(manifest) != freeze.freezeManifestSha256) errors += "freeze manifest hash mismatch"
        if (sha256(shardAllocation) != PEST_V2_QUALIFICATION_SHARD_ALLOCATION_SHA256) {
            errors += "shard allocation hash mismatch"
        }
        runCatching { PestControlPreboardDecks.verifyFrozenIdentities() }
            .onFailure { errors += "deck identity mismatch" }
        if (PestControlV2QualificationReadiness.validationErrors(V2QualificationReadiness()).isNotEmpty()) {
            errors += "qualification readiness mismatch"
        }

        val seeds = runCatching { parseVector(vector) }.getOrElse {
            errors += "ordered vector parse failure"
            emptyList()
        }
        if (seeds.size != PEST_V2_QUALIFICATION_GAMES ||
            seeds.distinct().size != PEST_V2_QUALIFICATION_GAMES || seeds.any { it == 0L }
        ) errors += "invalid frozen vector"

        val assignments = runCatching { parseCsv(csv) }.getOrElse {
            errors += "assignment CSV parse failure"
            emptyList()
        }
        if (assignments.size != 50) errors += "assignment count mismatch"
        if (assignments.map { it.game } != (1..50).toList()) errors += "assignment order mismatch"
        if (assignments.map { it.seed } != seeds) errors += "assignment/vector seed mismatch"
        if (assignments.count { it.playDraw == "PLAY" } != 25 ||
            assignments.count { it.playDraw == "DRAW" } != 25
        ) errors += "play/draw allocation mismatch"
        if (assignments.count { it.pestSeat == PestSeat.SEAT_ZERO } != 25 ||
            assignments.count { it.pestSeat == PestSeat.SEAT_ONE } != 25
        ) errors += "seat allocation mismatch"
        val globalCells = assignments.groupingBy { it.pestSeat to it.playDraw }.eachCount().values.sorted()
        if (globalCells != listOf(12, 12, 13, 13)) errors += "joint-cell allocation mismatch"
        assignments.chunked(25).forEachIndexed { index, shard ->
            if (shard.size != 25 || shard.groupingBy { it.pestSeat to it.playDraw }.eachCount().values.sorted() !=
                listOf(6, 6, 6, 7)
            ) errors += "shard ${index + 1} allocation mismatch"
        }
        assignments.forEach { assignment ->
            if (assignment.pestSeat == assignment.redSeat) errors += "seat collision game ${assignment.game}"
            val expectedPlayDraw = if (assignment.starter == StartingDeck.PEST_CONTROL) "PLAY" else "DRAW"
            if (assignment.playDraw != expectedPlayDraw) errors += "play/draw mismatch game ${assignment.game}"
            val expectedHex = "0x${assignment.seed.toULong().toString(16).padStart(16, '0')}"
            if (assignment.seedHex != expectedHex) errors += "seed hex mismatch game ${assignment.game}"
        }
        runCatching { validateJson(manifest, shardAllocation) }
            .onFailure { errors += "manifest or shard metadata mismatch" }
        return V2QualificationPreflightResult(errors.distinct(), seeds, assignments)
    }

    private fun parseVector(bytes: ByteArray): List<Long> {
        require(bytes.isNotEmpty() && !bytes.decodeToString().contains('\r'))
        val text = bytes.decodeToString()
        require(text.endsWith('\n'))
        return text.trimEnd('\n').lines().map(String::toLong)
    }

    private fun parseCsv(bytes: ByteArray): List<V2QualificationAssignment> {
        require(bytes.isNotEmpty() && bytes.take(3) != listOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()))
        val text = bytes.decodeToString()
        require(!text.contains('\r') && text.endsWith('\n'))
        val lines = text.trimEnd('\n').lines()
        require(lines.first() == expectedHeader)
        return lines.drop(1).map { line ->
            val columns = line.split(',')
            require(columns.size == 16)
            require(columns[0] == PEST_V2_OFFICIAL_PROTOCOL && columns[1] == PEST_V2_QUALIFICATION_BLOCK)
            require(columns[9] == PEST_CONTROL_V10_HASH && columns[10] == PEST_CONTROL_V10_SIDEBOARD_HASH)
            require(columns[11] == PEST_CONTROL_V10_75_HASH && columns[12] == SOTERX_MONO_RED_MAIN_HASH)
            require(columns[13] == SOTERX_MONO_RED_SIDEBOARD_HASH && columns[14] == SOTERX_MONO_RED_75_HASH)
            require(columns[15] == PEST_V2_QUALIFIED_RUNNER)
            V2QualificationAssignment(
                game = columns[2].toInt(), seed = columns[3].toLong(), seedHex = columns[4],
                pestSeat = PestSeat.valueOf(columns[5]), redSeat = PestSeat.valueOf(columns[6]),
                starter = StartingDeck.valueOf(columns[7]), playDraw = columns[8],
            )
        }
    }

    private fun validateJson(manifestBytes: ByteArray, shardBytes: ByteArray) {
        val manifest = Json.parseToJsonElement(manifestBytes.decodeToString()).jsonObject
        require(manifest.getValue("status").jsonPrimitive.content == "FROZEN_UNEXECUTED")
        require(manifest.getValue("protocol_id").jsonPrimitive.content == PEST_V2_OFFICIAL_PROTOCOL)
        require(manifest.getValue("block_id").jsonPrimitive.content == PEST_V2_QUALIFICATION_BLOCK)
        require(manifest.getValue("qualified_runner").jsonPrimitive.content == PEST_V2_QUALIFIED_RUNNER)
        require(manifest.getValue("readiness").jsonObject.getValue("runner_state").jsonPrimitive.content == "DISABLED")
        val hashes = manifest.getValue("artifact_hashes").jsonObject
        require(hashes.getValue("ordered_vector_sha256").jsonPrimitive.content ==
            PEST_V2_QUALIFICATION_FREEZE_IDENTITY.orderedVectorSha256)
        require(hashes.getValue("assignment_csv_sha256").jsonPrimitive.content ==
            PEST_V2_QUALIFICATION_FREEZE_IDENTITY.assignmentCsvSha256)
        require(hashes.getValue("shard_allocation_sha256").jsonPrimitive.content ==
            PEST_V2_QUALIFICATION_SHARD_ALLOCATION_SHA256)
        val shards = Json.parseToJsonElement(shardBytes.decodeToString()).jsonObject
        require(shards.getValue("block_id").jsonPrimitive.content == PEST_V2_QUALIFICATION_BLOCK)
        require(shards.getValue("global_order_preserved").jsonPrimitive.content == "true")
        require(shards.getValue("global_assignment_csv_sha256").jsonPrimitive.content ==
            PEST_V2_QUALIFICATION_FREEZE_IDENTITY.assignmentCsvSha256)
    }
}
