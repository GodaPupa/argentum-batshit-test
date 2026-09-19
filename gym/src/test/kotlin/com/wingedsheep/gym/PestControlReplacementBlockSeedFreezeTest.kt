package com.wingedsheep.gym

import com.wingedsheep.gym.matchup.FrozenMatchupAssignment
import com.wingedsheep.gym.matchup.PestControlMatchupSharding
import com.wingedsheep.gym.matchup.PestControlReplacementShardRunnerGuard
import com.wingedsheep.gym.matchup.PestSeat
import com.wingedsheep.gym.matchup.StartingDeck
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

private const val REPLACEMENT_PROTOCOL =
    "PEST_CONTROL_V10_VS_MONO_RED_MADNESS_SOTERX_2026_09_11_PREBOARD_V1"
private const val REPLACEMENT_BLOCK = "${REPLACEMENT_PROTOCOL}_REPLACEMENT_BLOCK_1"
private const val REPLACEMENT_SOURCE = "c52d5de4018e4dc6909d1197d941413df2d5ec51"
private const val REPLACEMENT_TREE = "688ae8e4df7f9622998ec53170272b949ccb38fb"
private const val REPLACEMENT_VECTOR_SHA = "41f5e00b85234e0757082af5c824fd1af28f2c3a66d739620dcd0fa9042b0f3f"
private const val REPLACEMENT_CSV_SHA = "2ccf75a77e8350482e47f44938485a3c3943aa66dfa7e126cad2ae30adcd5b42"
private const val REPLACEMENT_REGISTRY_SHA = "fcc09ba4db85c0b157de7301b5506a1c9cd29c1d85ccb3f6482d6cfe9260a246"
private const val REPLACEMENT_MANIFEST_SHA = "8f6f4a6d84b47221459c709dac3c9aa43d48cf59889ddf4dbe6a4ae16596a456"
private const val REPLACEMENT_QUARANTINE_SHA = "fe4295445d00a09deda4b16bd1512afcba1de264570993d9966de81ba40aca20"
private const val REPLACEMENT_SHARDS_SHA = "698e28151944bd9b0058c491b4bb03e278455f42f3e38cd2935fd50d1d855593"
private const val SHARD_ONE_ASSIGNMENT_SHA = "a994ba7b380be5fe5c77359b1e515034c59d5e08b637efec5fd17cf8cce2ca0e"
private const val SHARD_TWO_ASSIGNMENT_SHA = "0a3ef3adfb9b9be2c4d280270ff8f6dfb506516c06b4a56608d2a2ce92d99541"

private val replacementRoot = Path.of("..")
private val replacementDocs = replacementRoot.resolve("docs/experiments/pest-control")
private val replacementCsv = Path.of(
    "src", "test", "resources",
    "pest-control-v10-vs-mono-red-madness-soterx-2026-09-11-preboard-v1-replacement-block-1-seeds.csv",
)

/** This suite reads frozen bytes only. It never initializes a game or attempts a seed. */
class PestControlReplacementBlockSeedFreezeTest : FunSpec({
    val header = listOf(
        "protocol_id", "block_id", "game_number", "seed_decimal", "seed_hex", "pest_seat",
        "mono_red_seat", "starting_player", "pest_play_draw", "pest_main_sha256",
        "pest_sideboard_sha256", "pest_complete75_sha256", "mono_red_main_sha256",
        "mono_red_sideboard_sha256", "mono_red_complete75_sha256", "gate4_source_commit",
    )

    test("replacement block freezes one unique nonzero vector in unchanged global order") {
        val csvBytes = Files.readAllBytes(replacementCsv)
        sha256(csvBytes) shouldBe REPLACEMENT_CSV_SHA
        val rows = csvBytes.decodeToString().trimEnd('\n').lines().let { lines ->
            lines.first().split(',') shouldBe header
            lines.drop(1).map { header.zip(it.split(',')).toMap() }
        }
        rows.size shouldBe 50
        rows.map { it.getValue("game_number").toInt() } shouldBe (1..50).toList()
        rows.map { it.getValue("protocol_id") }.distinct() shouldBe listOf(REPLACEMENT_PROTOCOL)
        rows.map { it.getValue("block_id") }.distinct() shouldBe listOf(REPLACEMENT_BLOCK)
        val seeds = rows.map { it.getValue("seed_decimal").toLong() }
        seeds.none { it == 0L } shouldBe true
        seeds.distinct().size shouldBe 50
        rows.forEach { row ->
            val seed = row.getValue("seed_decimal").toLong()
            row.getValue("seed_hex") shouldBe "0x${seed.toULong().toString(16).padStart(16, '0')}"
        }
        rows.count { it.getValue("pest_play_draw") == "PLAY" } shouldBe 25
        rows.count { it.getValue("pest_play_draw") == "DRAW" } shouldBe 25
        rows.count { it.getValue("pest_seat") == "SEAT_ZERO" } shouldBe 25
        rows.count { it.getValue("pest_seat") == "SEAT_ONE" } shouldBe 25
        rows.groupingBy { "${it.getValue("pest_seat")}_${it.getValue("pest_play_draw")}" }
            .eachCount().values.sorted() shouldBe listOf(12, 12, 13, 13)

        val vector = Files.readAllBytes(replacementDocs.resolve("matchup-replacement-block-1-ordered-seeds.txt"))
        sha256(vector) shouldBe REPLACEMENT_VECTOR_SHA
        vector.decodeToString().trimEnd('\n').lines().map(String::toLong) shouldBe seeds

        fun assignment(row: Map<String, String>) = FrozenMatchupAssignment(
            protocolId = row.getValue("protocol_id"), blockId = row.getValue("block_id"),
            gameNumber = row.getValue("game_number").toInt(), seedDecimal = row.getValue("seed_decimal").toLong(),
            seedHex = row.getValue("seed_hex"), pestSeat = PestSeat.valueOf(row.getValue("pest_seat")),
            monoRedSeat = PestSeat.valueOf(row.getValue("mono_red_seat")),
            startingPlayer = StartingDeck.valueOf(row.getValue("starting_player")),
            pestPlayDraw = row.getValue("pest_play_draw"), pestMainSha256 = row.getValue("pest_main_sha256"),
            pestSideboardSha256 = row.getValue("pest_sideboard_sha256"),
            pestComplete75Sha256 = row.getValue("pest_complete75_sha256"),
            monoRedMainSha256 = row.getValue("mono_red_main_sha256"),
            monoRedSideboardSha256 = row.getValue("mono_red_sideboard_sha256"),
            monoRedComplete75Sha256 = row.getValue("mono_red_complete75_sha256"),
            gate4SourceCommit = row.getValue("gate4_source_commit"),
        )
        val assignments = rows.map(::assignment)
        PestControlMatchupSharding.assignmentSha256(assignments.take(25)) shouldBe SHARD_ONE_ASSIGNMENT_SHA
        PestControlMatchupSharding.assignmentSha256(assignments.drop(25)) shouldBe SHARD_TWO_ASSIGNMENT_SHA
        assignments.chunked(25).forEach { shard ->
            shard.groupingBy { it.pestSeat to it.pestPlayDraw }.eachCount().values.sorted() shouldBe listOf(6, 6, 6, 7)
        }
    }

    test("complete registry contains each historical Block A and replacement seed exactly once") {
        val registryBytes = Files.readAllBytes(replacementDocs.resolve("matchup-block-a-seed-registry.csv"))
        sha256(registryBytes) shouldBe REPLACEMENT_REGISTRY_SHA
        val lines = registryBytes.decodeToString().trimEnd('\n').lines()
        lines.size shouldBe 464
        val registryHeader = lines.first().split(',')
        val rows = lines.drop(1).map { registryHeader.zip(it.split(',')).toMap() }
        rows.map { it.getValue("seed_decimal").toLong() }.distinct().size shouldBe 463
        val blockA = rows.filter { it.getValue("identity") == "${REPLACEMENT_PROTOCOL}_BLOCK_A" }
        blockA.size shouldBe 50
        blockA.map { it.getValue("disposition") }.distinct() shouldBe listOf("REJECTED_RETIRED")
        val replacement = rows.filter { it.getValue("identity") == REPLACEMENT_BLOCK }
        replacement.size shouldBe 50
        replacement.map { it.getValue("position").toInt() } shouldBe (1..50).toList()
        replacement.map { it.getValue("disposition") }.distinct() shouldBe listOf("FROZEN_UNEXECUTED")
        replacement.map { it.getValue("seed_decimal") }.distinct().size shouldBe 50
    }

    test("manifest binds exact source decks readiness disabled runners shards and artifact bytes") {
        val manifestPath = replacementDocs.resolve("matchup-replacement-block-1-seed-freeze-manifest.json")
        val manifestBytes = Files.readAllBytes(manifestPath)
        sha256(manifestBytes) shouldBe REPLACEMENT_MANIFEST_SHA
        val manifest = Json.parseToJsonElement(manifestBytes.decodeToString()).jsonObject
        manifest.getValue("status").jsonPrimitive.content shouldBe "FROZEN_UNEXECUTED"
        manifest.getValue("block_id").jsonPrimitive.content shouldBe REPLACEMENT_BLOCK
        manifest.getValue("freeze_source").jsonObject.let { source ->
            source.getValue("commit").jsonPrimitive.content shouldBe REPLACEMENT_SOURCE
            source.getValue("tree").jsonPrimitive.content shouldBe REPLACEMENT_TREE
        }
        manifest.getValue("collision_audit").jsonObject.let { audit ->
            audit.getValue("complete_registry_exclusion_count").jsonPrimitive.content shouldBe "413"
            audit.getValue("new_seed_count").jsonPrimitive.content shouldBe "50"
            audit.getValue("new_unique_count").jsonPrimitive.content shouldBe "50"
            audit.getValue("overlap_count").jsonPrimitive.content shouldBe "0"
            audit.getValue("result").jsonPrimitive.content shouldBe "PASS"
        }
        manifest.getValue("readiness").jsonObject.let { readiness ->
            readiness.getValue("runner_guard").jsonPrimitive.content shouldBe "PestControlReplacementShardRunnerGuard"
            readiness.getValue("runner_state").jsonPrimitive.content shouldBe "DISABLED"
            readiness.getValue("shard_games").jsonArray.map { it.jsonPrimitive.content } shouldBe listOf("25", "25")
        }
        PestControlReplacementShardRunnerGuard.CONFIGURED_STATE shouldBe "DISABLED"
        val gameplay = Files.readString(Path.of("src/test/kotlin/com/wingedsheep/gym/PestControlGoldfishTest.kt"))
        Regex("enabled\\s*=\\s*false").findAll(gameplay).count() shouldBe 13
        Regex("enabled\\s*=\\s*true").findAll(gameplay).count() shouldBe 0

        val expected = mapOf(
            "gym/src/test/resources/pest-control-v10-vs-mono-red-madness-soterx-2026-09-11-preboard-v1-replacement-block-1-seeds.csv" to REPLACEMENT_CSV_SHA,
            "docs/experiments/pest-control/matchup-replacement-block-1-ordered-seeds.txt" to REPLACEMENT_VECTOR_SHA,
            "docs/experiments/pest-control/matchup-replacement-block-1-quarantined-vector.json" to REPLACEMENT_QUARANTINE_SHA,
            "docs/experiments/pest-control/matchup-replacement-block-1-seed-freeze-manifest.json" to REPLACEMENT_MANIFEST_SHA,
            "docs/experiments/pest-control/matchup-replacement-block-1-shard-allocation.json" to REPLACEMENT_SHARDS_SHA,
            "docs/experiments/pest-control/matchup-block-a-seed-registry.csv" to REPLACEMENT_REGISTRY_SHA,
        )
        val checksumRows = Files.readAllLines(replacementDocs.resolve("matchup-replacement-block-1-artifacts.sha256"))
            .associate { line -> line.split("  ", limit = 2).let { it[1] to it[0] } }
        checksumRows shouldBe expected
        checksumRows.forEach { (relative, digest) -> sha256(Files.readAllBytes(replacementRoot.resolve(relative))) shouldBe digest }
        manifest.getValue("artifact_hashes").jsonObject.let { hashes ->
            hashes.getValue("assignment_csv_sha256").jsonPrimitive.content shouldBe REPLACEMENT_CSV_SHA
            hashes.getValue("ordered_vector_sha256").jsonPrimitive.content shouldBe REPLACEMENT_VECTOR_SHA
            hashes.getValue("quarantined_vector_sha256").jsonPrimitive.content shouldBe REPLACEMENT_QUARANTINE_SHA
            hashes.getValue("seed_registry_updated_sha256").jsonPrimitive.content shouldBe REPLACEMENT_REGISTRY_SHA
            hashes.getValue("shard_allocation_sha256").jsonPrimitive.content shouldBe REPLACEMENT_SHARDS_SHA
        }
    }
})

private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
    .digest(bytes).joinToString("") { "%02x".format(it) }
