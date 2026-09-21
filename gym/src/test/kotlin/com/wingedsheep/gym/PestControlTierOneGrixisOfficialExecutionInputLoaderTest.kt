package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.*
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class PestControlTierOneGrixisOfficialExecutionInputLoaderTest : FunSpec({
    test("synthetic vector parser preserves exact order") {
        val bytes = "101\n-202\n303\n-404\n".toByteArray()
        PestControlTierOneGrixisOfficialExecutionInputLoader.parseVector(bytes) shouldBe
            listOf(101L, -202L, 303L, -404L)
    }

    test("synthetic assignment parser enforces exact frozen schema") {
        val seeds = listOf(101L, -202L, 303L, -404L)
        val rows = PestControlTierOneGrixisSmokeHarness.cellTemplate().mapIndexed { index, cell ->
            val seed = seeds[index]
            val grixis = if (cell.pestSeat == PestSeat.SEAT_ZERO) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO
            val playDraw = if (cell.startingDeck == GrixisStartingDeck.PEST_CONTROL) "PLAY" else "DRAW"
            listOf(
                PEST_GRIXIS_PREBOARD_PROTOCOL_ID,
                PEST_GRIXIS_SMOKE_BLOCK_ID,
                cell.gameNumber,
                seed,
                "0x${seed.toULong().toString(16).padStart(16, '0')}",
                cell.pestSeat,
                grixis,
                cell.startingDeck,
                playDraw,
                PEST_CONTROL_V10_HASH,
                PEST_GRIXIS_MAIN_SHA256,
                PEST_GRIXIS_SIDEBOARD_SHA256,
                PEST_GRIXIS_COMPLETE_75_SHA256,
                PEST_V2_QUALIFIED_RUNNER,
            ).joinToString(",")
        }
        val header = "protocol_id,block_id,game_number,seed_decimal,seed_hex,pest_seat,grixis_seat,starting_deck,pest_play_draw,pest_main_sha256,grixis_main_sha256,grixis_sideboard_sha256,grixis_complete75_sha256,qualified_runner"
        val bytes = (header + "\n" + rows.joinToString("\n") + "\n").toByteArray()
        val parsed = PestControlTierOneGrixisOfficialExecutionInputLoader.parseAssignments(bytes)
        parsed.map { it.gameNumber } shouldBe listOf(1, 2, 3, 4)
        parsed.map { it.seed } shouldBe seeds
        parsed.map { it.pestSeat } shouldBe PestControlTierOneGrixisSmokeHarness.cellTemplate().map { it.pestSeat }
        parsed.map { it.startingDeck } shouldBe PestControlTierOneGrixisSmokeHarness.cellTemplate().map { it.startingDeck }
    }

    test("official loader is unreachable without explicit environment acknowledgement") {
        shouldThrow<IllegalArgumentException> {
            PestControlTierOneGrixisOfficialExecutionInputLoader.loadValidatedFromEnvironment()
        }
    }
})
