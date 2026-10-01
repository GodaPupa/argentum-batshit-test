package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.sdk.core.Subtype
import com.wingedsheep.sdk.model.CardDefinition
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.nio.file.Files

/** Prospective engine integration fixtures only. No official Stage-E deck, seed or pilot is read. */
class SphinxEngineFixtureIntegrationTest : FunSpec({
    val source = "400c617df38775398b468833dee5921e79263599"
    // New fixed fixture input, not random sampling. Excluded from any future official seed pool.
    val seed = -202610010217L
    val players = listOf(EntityId("sphinx-integration-a"), EntityId("sphinx-integration-b"))
    val deck = Deck.of("Forest" to 40)
    fun registry() = CardRegistry().apply { register(CardDefinition.basicLand("Forest", Subtype.FOREST)) }
    fun config() = GameConfig(players = players.mapIndexed { i, id -> PlayerConfig("Fixture-$i", deck, playerId = id) },
        seed = seed, startingPlayerIndex = 0, useHandSmoother = false, skipMulligans = false)
    fun identity(label: String) = SphinxRunnerFixtureIdentity("sphinx-engine-fixture-$label", source,
        SphinxEngineFixtureCodec.fixtureDeckSha256(deck), "1".repeat(64),
        SphinxEngineFixtureCodec.fixtureDeckSha256(deck), "2".repeat(64), "3".repeat(64))
    // Pilot/runtime pins above are explicitly synthetic placeholders, not authenticated admission.
    fun root() = Files.createTempDirectory("sphinx-engine-integration-").toRealPath()

    test("real initialization is journaled and restored by a fresh codec without another initializer") {
        val root = root(); val id = identity("initial"); val registry = registry()
        SphinxStageEEngineFixture.create(root, id, registry, config()).use { it.stopFixture("CAPABILITY_STOP") }
        val directory = root.resolve(id.fixtureId)
        val before = Files.readAllBytes(directory.resolve("journal.txt"))
        val report = SphinxStageEFixtureReplay.verify(directory, id, SphinxEngineFixtureCodec(registry, source))
        report.verifiedActions shouldBe 0
        report.completeRecordedSequence shouldBe true
        report.recordedStop shouldBe "CAPABILITY_STOP"
        Files.readAllBytes(directory.resolve("journal.txt")).contentEquals(before) shouldBe true
    }

    test("two real keep decisions replay exactly with their full events and state envelopes") {
        val root = root(); val id = identity("keeps"); val registry = registry()
        SphinxStageEEngineFixture.create(root, id, registry, config()).use { owner ->
            owner.submitTrusted(KeepHand(players[0])).result.error shouldBe null
            owner.submitTrusted(KeepHand(players[1])).result.error shouldBe null
            owner.stopFixture("RESOURCE_STOP")
        }
        val report = SphinxStageEFixtureReplay.verify(root.resolve(id.fixtureId), id, SphinxEngineFixtureCodec(registry, source))
        report.verifiedActions shouldBe 2
        report.completeRecordedSequence shouldBe true
        report.recordedStop shouldBe "RESOURCE_STOP"
    }

    test("real mulligan changes RNG and replays its complete state and events") {
        val root = root(); val id = identity("mulligan"); val registry = registry()
        val codec = SphinxEngineFixtureCodec(registry, source)
        SphinxStageEEngineFixture.create(root, id, registry, config()).use { owner ->
            val initial = SphinxStageEWriteAheadJournal.inspect(root.resolve(id.fixtureId), id).entries[1].payload
            val initialState = codec.restoreRecorded(initial)
            val actual = owner.submitTrusted(TakeMulligan(players[0]))
            actual.result.error shouldBe null
            (actual.result.state.rng != initialState.result.state.rng) shouldBe true
            actual.result.events.isNotEmpty() shouldBe true
            owner.stopFixture("CAPABILITY_STOP")
        }
        SphinxStageEFixtureReplay.verify(root.resolve(id.fixtureId), id, SphinxEngineFixtureCodec(registry, source))
            .verifiedActions shouldBe 1
    }

    test("wrong deck pin and absent explicit seed are refused before fixture reservation") {
        val root = root(); val registry = registry(); val wrong = identity("wrong-deck").copy(ownDeckSha256 = "0".repeat(64))
        shouldThrowAny { SphinxStageEEngineFixture.create(root, wrong, registry, config()) }
        Files.exists(root.resolve(wrong.fixtureId)) shouldBe false
        val noSeed = identity("missing-seed")
        shouldThrowAny { SphinxStageEEngineFixture.create(root, noSeed, registry, config().copy(seed = null)) }
        Files.exists(root.resolve(noSeed.fixtureId)) shouldBe false
    }

    test("closed real initialized prefix remains incomplete and cannot be reopened as a new attempt") {
        val root = root(); val id = identity("prefix"); val registry = registry()
        SphinxStageEEngineFixture.create(root, id, registry, config()).close()
        val report = SphinxStageEFixtureReplay.verify(root.resolve(id.fixtureId), id, SphinxEngineFixtureCodec(registry, source))
        report.verifiedActions shouldBe 0
        report.completeRecordedSequence shouldBe false
        shouldThrowAny { SphinxStageEEngineFixture.create(root, id, registry, config()) }
    }

    test("new codec with incorrect expected source rejects recorded real engine material without mutation") {
        val root = root(); val id = identity("wrong-source"); val registry = registry()
        SphinxStageEEngineFixture.create(root, id, registry, config()).use { it.stopFixture("CAPABILITY_STOP") }
        val file = root.resolve(id.fixtureId).resolve("journal.txt"); val before = Files.readAllBytes(file)
        shouldThrowAny { SphinxStageEFixtureReplay.verify(root.resolve(id.fixtureId), id, SphinxEngineFixtureCodec(registry, "f".repeat(40))) }
        Files.readAllBytes(file).contentEquals(before) shouldBe true
    }
})
