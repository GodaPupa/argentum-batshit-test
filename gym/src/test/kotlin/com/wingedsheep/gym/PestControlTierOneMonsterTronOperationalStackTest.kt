package com.wingedsheep.gym

import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.gym.matchup.MONSTER_TRON_ASSIGNMENT_HEADER
import com.wingedsheep.gym.matchup.MonsterTronSmokeAssignment
import com.wingedsheep.gym.matchup.MonsterTronSmokeVectorIdentity
import com.wingedsheep.gym.matchup.MonsterTronStartingDeck
import com.wingedsheep.gym.matchup.PEST_CONTROL_V10_HASH
import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_FROZEN_SMOKE_ASSIGNMENTS_SHA256
import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_FROZEN_SMOKE_MANIFEST_SHA256
import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_FROZEN_SMOKE_SOURCE
import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256
import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_MAIN_SHA256
import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID
import com.wingedsheep.gym.matchup.PEST_MONSTER_TRON_SMOKE_BLOCK_ID
import com.wingedsheep.gym.matchup.PEST_V2_QUALIFIED_RUNNER
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronAuthorizedExecutionCoordinator
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronAuthorizedInitializer
import com.wingedsheep.gym.matchup.PestControlTierOneMonsterTronProductionDriver
import com.wingedsheep.gym.matchup.PestSeat
import com.wingedsheep.gym.matchup.decodeMonsterTronAssignmentText
import com.wingedsheep.gym.matchup.inspectMonsterTronPinnedArchive
import com.wingedsheep.gym.matchup.monsterTronDigest
import com.wingedsheep.gym.matchup.monsterTronSeedHex
import com.wingedsheep.mtg.sets.MtgSetCatalog
import com.wingedsheep.mtg.sets.tokens.PredefinedTokens
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class PestControlTierOneMonsterTronOperationalStackTest : FunSpec({
    val registry = CardRegistry().apply {
        register(PredefinedTokens.allTokens)
        MtgSetCatalog.all.forEach { set ->
            register(set.cards)
            register(set.basicLands)
        }
    }

    test("synthetic frozen archive primitive verifies exact member pins") {
        val members = linkedMapOf(
            "ordered-seeds.txt" to "101\n202\n303\n404\n".toByteArray(),
            "assignments.csv" to "synthetic\n".toByteArray(),
            "freeze-manifest.json" to "{}\n".toByteArray(),
            "quarantined-vector.json" to "{}\n".toByteArray(),
            "artifacts.sha256" to "synthetic\n".toByteArray(),
        )
        val archive = zip(members)
        val inspection = inspectMonsterTronPinnedArchive(
            archive,
            monsterTronDigest(archive),
            members.mapValues { monsterTronDigest(it.value) },
        )
        inspection.errors shouldBe emptyList()
        inspection.verified shouldBe true
    }

    test("synthetic assignment parser enforces frozen four-cell geometry") {
        val seeds = listOf(101L, 202L, 303L, 404L)
        val vector = seeds.joinToString("\n", postfix = "\n")
        val rows = buildString {
            appendLine(MONSTER_TRON_ASSIGNMENT_HEADER)
            seeds.forEachIndexed { index, seed ->
                val pestSeat = if (index < 2) PestSeat.SEAT_ZERO else PestSeat.SEAT_ONE
                val tronSeat = if (index < 2) PestSeat.SEAT_ONE else PestSeat.SEAT_ZERO
                val starter = if (index % 2 == 0) {
                    MonsterTronStartingDeck.PEST_CONTROL
                } else {
                    MonsterTronStartingDeck.MONSTER_TRON
                }
                appendLine(
                    listOf(
                        PEST_MONSTER_TRON_PREBOARD_PROTOCOL_ID,
                        PEST_MONSTER_TRON_SMOKE_BLOCK_ID,
                        (index + 1).toString(),
                        seed.toString(),
                        monsterTronSeedHex(seed),
                        pestSeat.name,
                        tronSeat.name,
                        starter.name,
                        if (index % 2 == 0) "PLAY" else "DRAW",
                        PEST_CONTROL_V10_HASH,
                        PEST_MONSTER_TRON_MAIN_SHA256,
                        PEST_V2_QUALIFIED_RUNNER,
                    ).joinToString(",")
                )
            }
        }
        val decoded = decodeMonsterTronAssignmentText(vector, rows)
        decoded.size shouldBe 4
        decoded.map { it.gameNumber } shouldBe listOf(1, 2, 3, 4)
        decoded.map { it.pestSeat } shouldBe listOf(
            PestSeat.SEAT_ZERO,
            PestSeat.SEAT_ZERO,
            PestSeat.SEAT_ONE,
            PestSeat.SEAT_ONE,
        )
        decoded.map { it.startingDeck } shouldBe listOf(
            MonsterTronStartingDeck.PEST_CONTROL,
            MonsterTronStartingDeck.MONSTER_TRON,
            MonsterTronStartingDeck.PEST_CONTROL,
            MonsterTronStartingDeck.MONSTER_TRON,
        )
    }

    test("authorized initializer and production driver complete excluded synthetic game") {
        val assignment = MonsterTronSmokeAssignment(
            gameNumber = 1,
            seed = 0x6d74726f6e010001L,
            seedHex = monsterTronSeedHex(0x6d74726f6e010001L),
            pestSeat = PestSeat.SEAT_ZERO,
            monsterTronSeat = PestSeat.SEAT_ONE,
            startingDeck = MonsterTronStartingDeck.PEST_CONTROL,
        )
        val identity = MonsterTronSmokeVectorIdentity(
            freezeCommit = PEST_MONSTER_TRON_FROZEN_SMOKE_SOURCE,
            orderedVectorSha256 = PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256,
            assignmentCsvSha256 = PEST_MONSTER_TRON_FROZEN_SMOKE_ASSIGNMENTS_SHA256,
            freezeManifestSha256 = PEST_MONSTER_TRON_FROZEN_SMOKE_MANIFEST_SHA256,
        )
        val game = PestControlTierOneMonsterTronAuthorizedInitializer.initialize(
            registry = registry,
            assignment = assignment,
            vectorIdentity = identity,
            executionCommit = "1".repeat(40),
            durableAttemptRecorded = true,
        )
        val raw = PestControlTierOneMonsterTronProductionDriver.drive(registry, game)
        raw.terminal?.gameOver shouldBe true
        raw.actions.isNotEmpty() shouldBe true
        raw.actions.all { it.accepted && it.rejectionReason == null } shouldBe true
    }

    test("coordinator enforces one ordered attempt per synthetic assignment") {
        val assignments = listOf(
            MonsterTronSmokeAssignment(1, 101, monsterTronSeedHex(101), PestSeat.SEAT_ZERO, PestSeat.SEAT_ONE, MonsterTronStartingDeck.PEST_CONTROL),
            MonsterTronSmokeAssignment(2, 202, monsterTronSeedHex(202), PestSeat.SEAT_ZERO, PestSeat.SEAT_ONE, MonsterTronStartingDeck.MONSTER_TRON),
            MonsterTronSmokeAssignment(3, 303, monsterTronSeedHex(303), PestSeat.SEAT_ONE, PestSeat.SEAT_ZERO, MonsterTronStartingDeck.PEST_CONTROL),
            MonsterTronSmokeAssignment(4, 404, monsterTronSeedHex(404), PestSeat.SEAT_ONE, PestSeat.SEAT_ZERO, MonsterTronStartingDeck.MONSTER_TRON),
        )
        val identity = MonsterTronSmokeVectorIdentity(
            PEST_MONSTER_TRON_FROZEN_SMOKE_SOURCE,
            PEST_MONSTER_TRON_FROZEN_SMOKE_VECTOR_SHA256,
            PEST_MONSTER_TRON_FROZEN_SMOKE_ASSIGNMENTS_SHA256,
            PEST_MONSTER_TRON_FROZEN_SMOKE_MANIFEST_SHA256,
        )
        val attempts = mutableListOf<Int>()
        val initialized = mutableListOf<Int>()
        val recorded = mutableListOf<Int>()
        val coordinator = PestControlTierOneMonsterTronAuthorizedExecutionCoordinator(
            assignments,
            identity,
            persistAttemptBeforeInitialization = { attempts += it.gameNumber },
            persistInitializationEntry = { initialized += it.gameNumber },
            persistCompletedGame = { a, _ -> recorded += a.gameNumber },
        )
        val outcome = coordinator.execute { a -> "game=${a.gameNumber}\n".toByteArray() }
        outcome.disposition.name shouldBe "VALIDATED"
        attempts shouldBe listOf(1, 2, 3, 4)
        initialized shouldBe attempts
        recorded shouldBe attempts
    }
})

private fun zip(members: Map<String, ByteArray>): ByteArray {
    val output = ByteArrayOutputStream()
    ZipOutputStream(output).use { zip ->
        members.forEach { (name, bytes) ->
            zip.putNextEntry(ZipEntry(name).apply { time = 0L })
            zip.write(bytes)
            zip.closeEntry()
        }
    }
    return output.toByteArray()
}
