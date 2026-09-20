package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.PEST_MONO_RED_PREBOARD_PROTOCOL_ID
import com.wingedsheep.gym.matchup.PestControlPreboardDecks
import com.wingedsheep.gym.matchup.PestSeat
import com.wingedsheep.gym.matchup.StartingDeck
import com.wingedsheep.gym.matchup.sha256
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val V2_PROTOCOL = "PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1"
private const val V2_BLOCK = "${V2_PROTOCOL}_V2_OFFICIAL_10"
private const val V2_QUALIFIED_RUNNER = "9829ee98869343cd48dceaa9a27c56ed27c6b3bc"
private const val V2_VECTOR_SHA256 = "c88d45352f531a08a484004f35ffbcc44ef08331f2c7844e5c11449e02c2d104"
private const val V2_CSV_SHA256 = "0df747d1928753b92b7fa812fbeccdd3f639490a26a9fdab99a9d203b3e9857f"

private data class V2Assignment(
    val game: Int,
    val seed: Long,
    val seedHex: String,
    val pestSeat: PestSeat,
    val redSeat: PestSeat,
    val starter: StartingDeck,
    val playDraw: String,
)

private object PestControlV2OfficialExecutionPreflight {
    fun validate(
        vector: ByteArray,
        csv: ByteArray,
        qualifiedRunner: String,
        protocol: String,
        block: String,
    ): List<String> {
        val errors = mutableListOf<String>()
        if (qualifiedRunner != V2_QUALIFIED_RUNNER) errors += "qualified runner mismatch"
        if (protocol != V2_PROTOCOL || protocol != PEST_MONO_RED_PREBOARD_PROTOCOL_ID) errors += "protocol mismatch"
        if (block != V2_BLOCK) errors += "block mismatch"
        if (sha256(vector) != V2_VECTOR_SHA256) errors += "ordered vector hash mismatch"
        if (sha256(csv) != V2_CSV_SHA256) errors += "assignment CSV hash mismatch"
        runCatching { PestControlPreboardDecks.verifyFrozenIdentities() }
            .onFailure { errors += "deck identity mismatch" }

        val seeds = vector.decodeToString().trimEnd('\n').lines().mapNotNull(String::toLongOrNull)
        if (seeds.size != 10 || seeds.distinct().size != 10 || seeds.any { it == 0L }) errors += "invalid frozen vector"

        val assignments = runCatching { parse(csv) }.getOrElse {
            errors += "assignment CSV parse failure"
            emptyList()
        }
        if (assignments.size != 10) errors += "assignment count mismatch"
        if (assignments.map { it.game } != (1..10).toList()) errors += "assignment order mismatch"
        if (assignments.map { it.seed } != seeds) errors += "assignment/vector seed mismatch"
        if (assignments.map { it.seed }.distinct().size != assignments.size) errors += "duplicate assignment seed"
        if (assignments.count { it.playDraw == "PLAY" } != 5 || assignments.count { it.playDraw == "DRAW" } != 5) {
            errors += "play/draw allocation mismatch"
        }
        if (assignments.count { it.pestSeat == PestSeat.SEAT_ZERO } != 5 ||
            assignments.count { it.pestSeat == PestSeat.SEAT_ONE } != 5) errors += "seat allocation mismatch"
        if (assignments.groupingBy { it.pestSeat to it.playDraw }.eachCount().values.sorted() != listOf(2,2,3,3)) {
            errors += "joint-cell allocation mismatch"
        }
        assignments.forEach { a ->
            if (a.pestSeat == a.redSeat) errors += "seat collision game ${a.game}"
            val expected = if (a.starter == StartingDeck.PEST_CONTROL) "PLAY" else "DRAW"
            if (a.playDraw != expected) errors += "play/draw mismatch game ${a.game}"
            if (a.seedHex != "0x${a.seed.toULong().toString(16).padStart(16, '0')}") errors += "seed hex mismatch game ${a.game}"
        }
        return errors
    }

    private fun parse(bytes: ByteArray): List<V2Assignment> {
        require(bytes.isNotEmpty() && bytes.take(3) != listOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()))
        val text = bytes.decodeToString()
        require(!text.contains('\r'))
        val lines = text.trimEnd('\n').lines()
        require(lines.first() == "protocol_id,block_id,game_number,seed_decimal,seed_hex,pest_seat,mono_red_seat,starting_player,pest_play_draw")
        return lines.drop(1).map { line ->
            val c = line.split(',')
            require(c.size == 9)
            require(c[0] == V2_PROTOCOL && c[1] == V2_BLOCK)
            V2Assignment(c[2].toInt(), c[3].toLong(), c[4], PestSeat.valueOf(c[5]), PestSeat.valueOf(c[6]), StartingDeck.valueOf(c[7]), c[8])
        }
    }
}

class PestControlV2OfficialExecutionHarnessContractTest : FunSpec({
    val vector = """
6660016068771281417
-4139589792468706634
1998588852542826204
718484881177075787
7524655099681191593
-6892342376218010140
4544063357793098919
172526489656479496
-4048075176218254755
-4008763202748494261
""".trimIndent().plus("\n").toByteArray()

    val csv = """protocol_id,block_id,game_number,seed_decimal,seed_hex,pest_seat,mono_red_seat,starting_player,pest_play_draw
PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1,PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_V2_OFFICIAL_10,1,6660016068771281417,0x5c6d215409817a09,SEAT_ONE,SEAT_ZERO,MONO_RED_MADNESS,DRAW
PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1,PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_V2_OFFICIAL_10,2,-4139589792468706634,0xc68d39042811dab6,SEAT_ZERO,SEAT_ONE,PEST_CONTROL,PLAY
PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1,PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_V2_OFFICIAL_10,3,1998588852542826204,0x1bbc69f8efdee6dc,SEAT_ONE,SEAT_ZERO,MONO_RED_MADNESS,DRAW
PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1,PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_V2_OFFICIAL_10,4,718484881177075787,0x09f89231570c604b,SEAT_ZERO,SEAT_ONE,MONO_RED_MADNESS,DRAW
PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1,PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_V2_OFFICIAL_10,5,7524655099681191593,0x686cf1f221d436a9,SEAT_ONE,SEAT_ZERO,PEST_CONTROL,PLAY
PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1,PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_V2_OFFICIAL_10,6,-6892342376218010140,0xa0597b2043275de4,SEAT_ONE,SEAT_ZERO,MONO_RED_MADNESS,DRAW
PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1,PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_V2_OFFICIAL_10,7,4544063357793098919,0x3f0fc18c66205ca7,SEAT_ZERO,SEAT_ONE,MONO_RED_MADNESS,DRAW
PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1,PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_V2_OFFICIAL_10,8,172526489656479496,0x0264efeda251cf08,SEAT_ZERO,SEAT_ONE,PEST_CONTROL,PLAY
PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1,PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_V2_OFFICIAL_10,9,-4048075176218254755,0xc7d25913296f225d,SEAT_ZERO,SEAT_ONE,PEST_CONTROL,PLAY
PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1,PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1_V2_OFFICIAL_10,10,-4008763202748494261,0xc85e031b3f84ce4b,SEAT_ONE,SEAT_ZERO,PEST_CONTROL,PLAY
""".toByteArray()

    test("accepted frozen V2 vector passes preflight without executing gameplay") {
        PestControlV2OfficialExecutionPreflight.validate(vector, csv, V2_QUALIFIED_RUNNER, V2_PROTOCOL, V2_BLOCK) shouldBe emptyList()
    }

    test("preflight fails closed on provenance or frozen-input mutation") {
        PestControlV2OfficialExecutionPreflight.validate(vector, csv, "bad", V2_PROTOCOL, V2_BLOCK).isNotEmpty() shouldBe true
        PestControlV2OfficialExecutionPreflight.validate(vector + 0, csv, V2_QUALIFIED_RUNNER, V2_PROTOCOL, V2_BLOCK).isNotEmpty() shouldBe true
        PestControlV2OfficialExecutionPreflight.validate(vector, csv + 0, V2_QUALIFIED_RUNNER, V2_PROTOCOL, V2_BLOCK).isNotEmpty() shouldBe true
        PestControlV2OfficialExecutionPreflight.validate(vector, csv, V2_QUALIFIED_RUNNER, "bad", V2_BLOCK).isNotEmpty() shouldBe true
    }
})
