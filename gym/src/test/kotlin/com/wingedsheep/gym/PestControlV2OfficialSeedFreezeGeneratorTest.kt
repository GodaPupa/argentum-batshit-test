package com.wingedsheep.gym

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path
import java.security.SecureRandom
import java.security.MessageDigest

private const val V2_FREEZE_ACK = "GENERATE_V2_OFFICIAL_SEEDS_NO_GAMEPLAY"
private const val V2_QUALIFIED_RUNNER = "43d9c29cbde78d070761bc617570a72a225f3e0f"
private const val V2_PROTOCOL = "PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1"
private const val V2_BLOCK = "${V2_PROTOCOL}_V2_OFFICIAL_10"

class PestControlV2OfficialSeedFreezeGeneratorTest : FunSpec({
    test("generate and quarantine exactly ten V2 official seeds without gameplay").config(
        enabled = System.getenv("PEST_V2_FREEZE") == "true",
    ) {
        System.getenv("PEST_V2_FREEZE_ACK") shouldBe V2_FREEZE_ACK
        System.getenv("PEST_V2_QUALIFIED_RUNNER") shouldBe V2_QUALIFIED_RUNNER
        val root = Path.of("").toAbsolutePath().parent
        val docs = root.resolve("docs/experiments/pest-control")
        val registryPath = docs.resolve("matchup-block-a-seed-registry.csv")
        val historical = Files.readAllLines(registryPath).drop(1).mapNotNull { line ->
            line.split(',').getOrNull(2)?.toLongOrNull()
        }.toMutableSet()
        // Permanently exclude the nonexperimental V2 qualifying-smoke entropy too.
        historical += 0x5045_5354_5632_0001L

        val rng = SecureRandom()
        val seeds = mutableListOf<Long>()
        while (seeds.size < 10) {
            val candidate = rng.nextLong()
            if (candidate != 0L && candidate !in historical && candidate !in seeds) seeds += candidate
        }
        seeds.distinct().size shouldBe 10

        val cells = listOf(
            "SEAT_ZERO,PLAY","SEAT_ZERO,PLAY","SEAT_ZERO,PLAY",
            "SEAT_ZERO,DRAW","SEAT_ZERO,DRAW",
            "SEAT_ONE,PLAY","SEAT_ONE,PLAY",
            "SEAT_ONE,DRAW","SEAT_ONE,DRAW","SEAT_ONE,DRAW",
        )
        val shuffledCells = cells.shuffled(rng)
        val out = root.resolve("build/reports/pest-control-v2-freeze")
        Files.createDirectories(out)
        val vector = seeds.joinToString("\n", postfix="\n")
        Files.writeString(out.resolve("v2-official-ordered-seeds.txt"), vector)
        val csv = buildString {
            appendLine("protocol_id,block_id,game_number,seed_decimal,seed_hex,pest_seat,mono_red_seat,starting_player,pest_play_draw")
            seeds.forEachIndexed { i, seed ->
                val (seat, pd) = shuffledCells[i].split(',')
                val redSeat = if (seat == "SEAT_ZERO") "SEAT_ONE" else "SEAT_ZERO"
                val starter = if (pd == "PLAY") "PEST_CONTROL" else "MONO_RED_MADNESS"
                appendLine("$V2_PROTOCOL,$V2_BLOCK,${i+1},$seed,0x${seed.toULong().toString(16).padStart(16,'0')},$seat,$redSeat,$starter,$pd")
            }
        }
        Files.writeString(out.resolve("v2-official-assignments.csv"), csv)
        val vectorSha = sha256(vector.toByteArray())
        val csvSha = sha256(csv.toByteArray())
        val manifest = """{"status":"FROZEN_UNEXECUTED","protocol_id":"$V2_PROTOCOL","block_id":"$V2_BLOCK","qualified_runner":"$V2_QUALIFIED_RUNNER","seed_count":10,"registry_exclusion_count":${historical.size},"overlap_count":0,"play_draw":"5/5","seat":"5/5","joint_cells":"2/2/3/3","ordered_vector_sha256":"$vectorSha","assignment_csv_sha256":"$csvSha"}
"""
        Files.writeString(out.resolve("v2-official-freeze-manifest.json"), manifest)
        Files.writeString(out.resolve("v2-official-artifacts.sha256"),
            "$vectorSha  v2-official-ordered-seeds.txt\n$csvSha  v2-official-assignments.csv\n${sha256(manifest.toByteArray())}  v2-official-freeze-manifest.json\n")
    }
})

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
