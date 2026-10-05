package com.wingedsheep.gym.pest

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

class PestCurrentPairC2ReplayJournalTest : FunSpec({
    val identity = PestCurrentPairC2ReplayIdentity(
        sourceCommit = "5106ecf6abe473f7c3d9089946b2a3c4c38a3bbd",
        sourceTree = "e7c37f620c8cefa0352742e38635ca196e887637",
        pestMainSha256 = "7be61a66e2c7654428043d56b411afb4d406f02dfcc4eb7f15a62295d4e906f5",
        monsterMainSha256 = "79ffc53ac331beafeb1ef4510ce174d01fb2685d963a4c1485f04edbf47c064f",
        observationAdapterBlob = "17cbc1bdb65d9da597eff9811c1b4fb2f7cefee0",
        actorActionMenuBlob = "7af655daf3e6a94333255e0c7396ff64bc652bce",
        strategistBlob = "d8dd4cb300746dbc7dc4840220e970dd180ce20d",
        decisionResponderBlob = "090b6fb24392b06a32034030d937baca768e2201",
        pestProfileBlob = "43bbad79273806f56703fad420e9f6a495d05309",
        monsterPolicyBlob = "2d070f45a8e07f0ba7d46ec6a56727bd6402728a",
        monsterAdvisorBlob = "69ae4cc111288a4e6a283f5b566cc140153a2bdf",
    )

    test("fresh current-pair synthetic journal is durable ordered no-clobber and replay deterministic") {
        val root = Files.createTempDirectory("pest-c2-p07-").toRealPath()
        val journal = PestCurrentPairC2ReplayJournal.create(root, "fresh-current-pair-replay", identity)
        val pass = "{\"type\":\"PassPriority\",\"player\":\"pest\"}"
        val land = "{\"type\":\"PlayLand\",\"card\":\"Urza's Tower\",\"player\":\"monster\"}"

        journal.recordIntent(PestCurrentPairC2ReplayIntent(1, "pest", pass, "1".repeat(64), "2".repeat(64)))
        journal.recordResult(PestCurrentPairC2ReplayResult(
            1, pass, listOf("{\"type\":\"PriorityPassed\"}"), "3".repeat(64), false,
        ))
        runCatching {
            journal.recordIntent(PestCurrentPairC2ReplayIntent(1, "pest", pass, "1".repeat(64), "2".repeat(64)))
        }.isFailure shouldBe true

        journal.recordIntent(PestCurrentPairC2ReplayIntent(2, "monster", land, "3".repeat(64), "4".repeat(64)))
        journal.recordResult(PestCurrentPairC2ReplayResult(
            2, land, listOf("{\"type\":\"LandPlayed\",\"card\":\"Urza's Tower\"}"), "5".repeat(64), true,
        ))
        journal.finish()
        journal.close()

        val inspection = PestCurrentPairC2ReplayJournal.replay(root.resolve("fresh-current-pair-replay"), identity)
        inspection.records shouldBe 2
        inspection.terminal shouldBe true
        inspection.journalSha256.length shouldBe 64

        runCatching {
            PestCurrentPairC2ReplayJournal.create(root, "fresh-current-pair-replay", identity)
        }.isFailure shouldBe true
    }

    test("incomplete intent cannot be promoted to a replay result") {
        val root = Files.createTempDirectory("pest-c2-p07-incomplete-").toRealPath()
        val journal = PestCurrentPairC2ReplayJournal.create(root, "incomplete", identity)
        val action = "{\"type\":\"PassPriority\",\"player\":\"pest\"}"
        journal.recordIntent(PestCurrentPairC2ReplayIntent(1, "pest", action, "a".repeat(64), "b".repeat(64)))
        runCatching { journal.finish() }.isFailure shouldBe true
        journal.close()
        runCatching { PestCurrentPairC2ReplayJournal.replay(root.resolve("incomplete"), identity) }.isFailure shouldBe true
    }

    test("result action must exactly match durable intent and terminal must be final") {
        val root = Files.createTempDirectory("pest-c2-p07-action-").toRealPath()
        val journal = PestCurrentPairC2ReplayJournal.create(root, "action-match", identity)
        val action = "{\"type\":\"PassPriority\",\"player\":\"monster\"}"
        journal.recordIntent(PestCurrentPairC2ReplayIntent(1, "monster", action, "c".repeat(64), "d".repeat(64)))
        runCatching {
            journal.recordResult(PestCurrentPairC2ReplayResult(
                1, "{\"type\":\"PassPriority\",\"player\":\"pest\"}", emptyList(), "e".repeat(64), true,
            ))
        }.isFailure shouldBe true
        journal.recordResult(PestCurrentPairC2ReplayResult(1, action, emptyList(), "e".repeat(64), true))
        runCatching {
            journal.recordIntent(PestCurrentPairC2ReplayIntent(2, "pest", action, "e".repeat(64), "f".repeat(64)))
        }.isFailure shouldBe true
        journal.finish()
        journal.close()
        PestCurrentPairC2ReplayJournal.replay(root.resolve("action-match"), identity).records shouldBe 1
    }
})
