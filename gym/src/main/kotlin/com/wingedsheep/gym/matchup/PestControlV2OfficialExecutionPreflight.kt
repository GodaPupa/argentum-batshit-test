package com.wingedsheep.gym.matchup

data class V2OfficialAssignment(
    val game: Int,
    val seed: Long,
    val seedHex: String,
    val pestSeat: PestSeat,
    val redSeat: PestSeat,
    val starter: StartingDeck,
    val playDraw: String,
)

data class V2OfficialPreflightResult(
    val errors: List<String>,
    val seeds: List<Long>,
    val assignments: List<V2OfficialAssignment>,
)

object PestControlV2OfficialExecutionPreflight {
    fun validate(
        vector: ByteArray,
        csv: ByteArray,
        qualifiedRunner: String,
        protocol: String,
        block: String,
    ): List<String> = inspect(vector, csv, qualifiedRunner, protocol, block).errors

    fun inspect(
        vector: ByteArray,
        csv: ByteArray,
        qualifiedRunner: String,
        protocol: String,
        block: String,
    ): V2OfficialPreflightResult {
        val errors = mutableListOf<String>()
        if (qualifiedRunner != PEST_V2_QUALIFIED_RUNNER) errors += "qualified runner mismatch"
        if (protocol != PEST_V2_OFFICIAL_PROTOCOL || protocol != PEST_MONO_RED_PREBOARD_PROTOCOL_ID) {
            errors += "protocol mismatch"
        }
        if (block != PEST_V2_OFFICIAL_BLOCK) errors += "block mismatch"
        if (sha256(vector) != PEST_V2_ORDERED_VECTOR_SHA256) errors += "ordered vector hash mismatch"
        if (sha256(csv) != PEST_V2_ASSIGNMENT_CSV_SHA256) errors += "assignment CSV hash mismatch"
        runCatching { PestControlPreboardDecks.verifyFrozenIdentities() }
            .onFailure { errors += "deck identity mismatch" }

        val seeds = vector.decodeToString().trimEnd('\n').lines().mapNotNull(String::toLongOrNull)
        if (seeds.size != PEST_V2_EXPECTED_GAMES ||
            seeds.distinct().size != PEST_V2_EXPECTED_GAMES ||
            seeds.any { it == 0L }
        ) {
            errors += "invalid frozen vector"
        }

        val assignments = runCatching { parse(csv) }.getOrElse {
            errors += "assignment CSV parse failure"
            emptyList()
        }
        if (assignments.size != PEST_V2_EXPECTED_GAMES) errors += "assignment count mismatch"
        if (assignments.map { it.game } != (1..PEST_V2_EXPECTED_GAMES).toList()) errors += "assignment order mismatch"
        if (assignments.map { it.seed } != seeds) errors += "assignment/vector seed mismatch"
        if (assignments.map { it.seed }.distinct().size != assignments.size) errors += "duplicate assignment seed"
        if (assignments.count { it.playDraw == "PLAY" } != PEST_V2_EXPECTED_GAMES / 2 ||
            assignments.count { it.playDraw == "DRAW" } != PEST_V2_EXPECTED_GAMES / 2
        ) {
            errors += "play/draw allocation mismatch"
        }
        if (assignments.count { it.pestSeat == PestSeat.SEAT_ZERO } != PEST_V2_EXPECTED_GAMES / 2 ||
            assignments.count { it.pestSeat == PestSeat.SEAT_ONE } != PEST_V2_EXPECTED_GAMES / 2
        ) {
            errors += "seat allocation mismatch"
        }
        if (assignments.groupingBy { it.pestSeat to it.playDraw }.eachCount().values.sorted() != listOf(2, 2, 3, 3)) {
            errors += "joint-cell allocation mismatch"
        }
        assignments.forEach { assignment ->
            if (assignment.pestSeat == assignment.redSeat) errors += "seat collision game ${assignment.game}"
            val expectedPlayDraw = if (assignment.starter == StartingDeck.PEST_CONTROL) "PLAY" else "DRAW"
            if (assignment.playDraw != expectedPlayDraw) errors += "play/draw mismatch game ${assignment.game}"
            val expectedHex = "0x${assignment.seed.toULong().toString(16).padStart(16, '0')}"
            if (assignment.seedHex != expectedHex) errors += "seed hex mismatch game ${assignment.game}"
        }
        return V2OfficialPreflightResult(errors, seeds, assignments)
    }

    private fun parse(bytes: ByteArray): List<V2OfficialAssignment> {
        require(bytes.isNotEmpty() && bytes.take(3) != listOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()))
        val text = bytes.decodeToString()
        require(!text.contains('\r'))
        val lines = text.trimEnd('\n').lines()
        require(
            lines.first() ==
                "protocol_id,block_id,game_number,seed_decimal,seed_hex,pest_seat,mono_red_seat,starting_player,pest_play_draw",
        )
        return lines.drop(1).map { line ->
            val columns = line.split(',')
            require(columns.size == 9)
            require(columns[0] == PEST_V2_OFFICIAL_PROTOCOL && columns[1] == PEST_V2_OFFICIAL_BLOCK)
            V2OfficialAssignment(
                game = columns[2].toInt(),
                seed = columns[3].toLong(),
                seedHex = columns[4],
                pestSeat = PestSeat.valueOf(columns[5]),
                redSeat = PestSeat.valueOf(columns[6]),
                starter = StartingDeck.valueOf(columns[7]),
                playDraw = columns[8],
            )
        }
    }
}
