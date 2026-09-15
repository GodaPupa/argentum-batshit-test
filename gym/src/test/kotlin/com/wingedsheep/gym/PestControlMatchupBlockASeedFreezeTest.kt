package com.wingedsheep.gym

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

private const val BLOCK_A_PROTOCOL =
    "PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1"
private const val BLOCK_A_ID = "${BLOCK_A_PROTOCOL}_BLOCK_A"
private const val BLOCK_A_GATE4_SOURCE = "d81ec35f0be74422315f9bb5bf8d69c395b2d872"
private const val BLOCK_A_REGISTRY_SHA256 = "62cf99b9b4b003752c2552f2a5965e96cc9026ea0d025cf8ffcba4bbab721842"
private const val BLOCK_A_VECTOR_SHA256 = "48459229c8e261022c37fa52915c382a7ab9b95405ce2124254ff82b57ecf135"
private const val BLOCK_A_CSV_SHA256 = "b23d02ca18d604572f554cdc0aa6c99ce85a32a5e98515ee982cc266074911b9"
private const val BLOCK_A_MANIFEST_SHA256 = "4f3a82da8343a8176c9e98ffb8de2531a254e6f19072837ea3d997507e473e16"
private const val BLOCK_A_PEST_MAIN = "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5"
private const val BLOCK_A_PEST_SIDEBOARD = "c1910468c228662b21647eb7ca8481cd11691906e56e0a37990e00f22886368c"
private const val BLOCK_A_PEST_75 = "2927737eb084657cda58fd1877db933037c383273062f0bd209c7ff3046c1cf5"
private const val BLOCK_A_RED_MAIN = "38c7850d1b9b070637502cedfffc6116d3504a525db8b51223505d7935134258"
private const val BLOCK_A_RED_SIDEBOARD = "d0aab592e6c82ad019dba0eadc028db75ef5cf13c9b3e4e0531a04d513bfc77a"
private const val BLOCK_A_RED_75 = "e9ff7ecbdbc8f41ebe526fe8fee4f87f706e0630491f9d78121677922fbd647d"

private val blockCsvPath = Path.of(
    "src", "test", "resources",
    "pest-control-v10-vs-mono-red-madness-soterx-2026-09-11-preboard-v1-block-a-seeds.csv",
)
private val blockVectorPath = Path.of("..", "docs", "experiments", "pest-control", "matchup-block-a-ordered-seeds.txt")
private val blockRegistryPath = Path.of("..", "docs", "experiments", "pest-control", "matchup-block-a-seed-registry.csv")
private val blockManifestPath = Path.of("..", "docs", "experiments", "pest-control", "matchup-block-a-seed-freeze-manifest.json")
private val blockManifestHashPath = Path.of("..", "docs", "experiments", "pest-control", "matchup-block-a-seed-freeze-manifest.sha256")

class PestControlMatchupBlockASeedFreezeTest : FunSpec({
    val header = listOf(
        "protocol_id", "block_id", "game_number", "seed_decimal", "seed_hex", "pest_seat",
        "mono_red_seat", "starting_player", "pest_play_draw", "pest_main_sha256",
        "pest_sideboard_sha256", "pest_complete75_sha256", "mono_red_main_sha256",
        "mono_red_sideboard_sha256", "mono_red_complete75_sha256", "gate4_source_commit",
    )

    test("Block A freezes exactly fifty unique balanced nonoverlapping assignments") {
        val csvBytes = Files.readAllBytes(blockCsvPath)
        assertCanonicalText(csvBytes)
        sha256(csvBytes) shouldBe BLOCK_A_CSV_SHA256
        val lines = csvBytes.decodeToString().trimEnd('\n').lines()
        lines.first().split(',') shouldBe header
        val rows = lines.drop(1).map { line ->
            line.split(',').also { it.size shouldBe header.size }.let { header.zip(it).toMap() }
        }
        rows.size shouldBe 50
        rows.map { it.getValue("game_number").toInt() } shouldBe (1..50).toList()

        val seeds = rows.map { it.getValue("seed_decimal").toLong() }
        seeds.none { it == 0L } shouldBe true
        seeds.distinct().size shouldBe 50
        rows.forEach { row ->
            val seed = row.getValue("seed_decimal").toLong()
            row.getValue("seed_hex") shouldBe "0x${seed.toULong().toString(16).padStart(16, '0')}"
            row.getValue("protocol_id") shouldBe BLOCK_A_PROTOCOL
            row.getValue("block_id") shouldBe BLOCK_A_ID
            row.getValue("pest_main_sha256") shouldBe BLOCK_A_PEST_MAIN
            row.getValue("pest_sideboard_sha256") shouldBe BLOCK_A_PEST_SIDEBOARD
            row.getValue("pest_complete75_sha256") shouldBe BLOCK_A_PEST_75
            row.getValue("mono_red_main_sha256") shouldBe BLOCK_A_RED_MAIN
            row.getValue("mono_red_sideboard_sha256") shouldBe BLOCK_A_RED_SIDEBOARD
            row.getValue("mono_red_complete75_sha256") shouldBe BLOCK_A_RED_75
            row.getValue("gate4_source_commit") shouldBe BLOCK_A_GATE4_SOURCE
            setOf(row.getValue("pest_seat"), row.getValue("mono_red_seat")) shouldBe setOf("SEAT_ZERO", "SEAT_ONE")
        }
        rows.count { it.getValue("pest_play_draw") == "PLAY" } shouldBe 25
        rows.count { it.getValue("pest_play_draw") == "DRAW" } shouldBe 25
        rows.count { it.getValue("pest_seat") == "SEAT_ZERO" } shouldBe 25
        rows.count { it.getValue("pest_seat") == "SEAT_ONE" } shouldBe 25
        val cells = rows.groupingBy { "${it.getValue("pest_seat")}_${it.getValue("pest_play_draw")}" }.eachCount()
        cells.values.sorted() shouldBe listOf(12, 12, 13, 13)
        rows.forEach { row ->
            row.getValue("starting_player") shouldBe if (row.getValue("pest_play_draw") == "PLAY") {
                "PEST_CONTROL"
            } else {
                "MONO_RED_MADNESS"
            }
        }

        val vectorBytes = Files.readAllBytes(blockVectorPath)
        assertCanonicalText(vectorBytes)
        sha256(vectorBytes) shouldBe BLOCK_A_VECTOR_SHA256
        vectorBytes.decodeToString().trimEnd('\n').lines().map(String::toLong) shouldBe seeds

        val registryBytes = Files.readAllBytes(blockRegistryPath)
        assertCanonicalText(registryBytes)
        sha256(registryBytes) shouldBe BLOCK_A_REGISTRY_SHA256
        val registryLines = registryBytes.decodeToString().trimEnd('\n').lines()
        registryLines.size shouldBe 364
        val registryHeader = registryLines.first().split(',')
        val decimalIndex = registryHeader.indexOf("seed_decimal")
        decimalIndex shouldBe 3
        val excluded = registryLines.drop(1).map { it.split(',')[decimalIndex].toLong() }.toSet()
        excluded.size shouldBe 363
        seeds.intersect(excluded).shouldBeEmpty()
    }

    test("manifest reconciles every frozen row hash and canonical byte contract") {
        val csvBytes = Files.readAllBytes(blockCsvPath)
        val csvRows = csvBytes.decodeToString().trimEnd('\n').lines().drop(1).map { line ->
            val values = line.split(',')
            listOf(
                "protocol_id", "block_id", "game_number", "seed_decimal", "seed_hex", "pest_seat",
                "mono_red_seat", "starting_player", "pest_play_draw", "pest_main_sha256",
                "pest_sideboard_sha256", "pest_complete75_sha256", "mono_red_main_sha256",
                "mono_red_sideboard_sha256", "mono_red_complete75_sha256", "gate4_source_commit",
            ).zip(values).toMap()
        }
        val manifestBytes = Files.readAllBytes(blockManifestPath)
        assertCanonicalText(manifestBytes)
        sha256(manifestBytes) shouldBe BLOCK_A_MANIFEST_SHA256
        Files.readString(blockManifestHashPath) shouldBe
            "$BLOCK_A_MANIFEST_SHA256  matchup-block-a-seed-freeze-manifest.json\n"
        val manifest = Json.parseToJsonElement(manifestBytes.decodeToString()).jsonObject
        manifest.getValue("status").jsonPrimitive.content shouldBe "FROZEN_UNEXECUTED"
        manifest.getValue("protocol_id").jsonPrimitive.content shouldBe BLOCK_A_PROTOCOL
        manifest.getValue("block_id").jsonPrimitive.content shouldBe BLOCK_A_ID
        manifest.getValue("gate4_source_commit").jsonPrimitive.content shouldBe BLOCK_A_GATE4_SOURCE
        manifest.getValue("ordered_vector_sha256").jsonPrimitive.content shouldBe BLOCK_A_VECTOR_SHA256
        manifest.getValue("seed_csv_sha256").jsonPrimitive.content shouldBe BLOCK_A_CSV_SHA256
        manifest.getValue("seed_registry_input_sha256").jsonPrimitive.content shouldBe BLOCK_A_REGISTRY_SHA256
        val reconciliation = manifest.getValue("reconciliation").jsonArray
        reconciliation.size shouldBe 50
        reconciliation.forEachIndexed { index, element ->
            val record = element.jsonObject
            csvRows[index].forEach { (name, value) -> record.getValue(name).jsonPrimitive.content shouldBe value }
        }
        val collision = manifest.getValue("collision_audit").jsonObject
        collision.getValue("result").jsonPrimitive.content shouldBe "PASS"
        collision.getValue("overlap_count").jsonPrimitive.content shouldBe "0"
        collision.getValue("new_seed_count").jsonPrimitive.content shouldBe "50"
        collision.getValue("new_unique_count").jsonPrimitive.content shouldBe "50"
    }

    test("all thirteen Pest gameplay runners remain hard disabled") {
        val source = Files.readString(Path.of("src", "test", "kotlin", "com", "wingedsheep", "gym", "PestControlGoldfishTest.kt"))
        Regex("enabled\\s*=\\s*false").findAll(source).count() shouldBe 13
        Regex("enabled\\s*=\\s*true").findAll(source).count() shouldBe 0
    }
})

private fun assertCanonicalText(bytes: ByteArray) {
    bytes.copyOfRange(0, minOf(3, bytes.size))
        .contentEquals(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte())) shouldBe false
    bytes.contains('\r'.code.toByte()) shouldBe false
    bytes.last() shouldBe '\n'.code.toByte()
}

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes)
    .joinToString("") { "%02x".format(it) }
