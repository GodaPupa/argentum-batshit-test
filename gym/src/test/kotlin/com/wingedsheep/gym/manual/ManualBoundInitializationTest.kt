package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.core.spec.style.FunSpec
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import java.nio.file.Files
import java.nio.file.Path

/** Excluded synthetic initialization/replay fixtures; no official vector or opponent package. */
class ManualBoundInitializationTest : FunSpec({
    val names = listOf("Animar, Soul of Elements", "Replay Fixture Partner", "Replay Fixture Opponent")
    fun registry() = CardRegistry().apply {
        register(TestCards.all)
        names.forEach { name -> register(card(name) {
            manaCost = "{0}"; typeLine = "Legendary Creature — Human"; power = 1; toughness = 1
        }) }
    }
    val identity = ManualFixtureIdentity("manual-fixture-initialization-replay", "8".repeat(40),
        "CRUISE", List(4) { "1".repeat(64) }, List(4) { "2".repeat(64) }, "3".repeat(64))
    fun spec() = ManualInitializationSpec(
        fixtureIdentitySha256 = ManualInitializationReplay.sha256(identity.bytes()),
        seed = 0x4D414E494E4954L, startingPlayerIndex = 0, manualSeat = 0,
        players = (0..3).map { seat -> ManualReplayPlayer("EXCLUDED_REPLAY_$seat", "replay-seat-$seat",
            List(if (seat == 0) 98 else 99) { if (it % 2 == 0) "Forest" else "Island" },
            if (seat == 0) names.take(2) else listOf(names.last())) })
    fun initialize(s: ManualInitializationSpec) = GameInitializer(registry()).initializeGame(GameConfig(
        players = s.players.map { PlayerConfig(it.name, Deck(it.cards), playerId = EntityId(it.playerId),
            commanderCardNames = it.commanders) }, format = Format.Commander(), startingPlayerIndex = s.startingPlayerIndex,
        skipMulligans = false, useHandSmoother = false, seed = s.seed))
    fun adapter(s: ManualInitializationSpec): PhaseTwoTelemetryAdapter {
        val i = initialize(s)
        return PhaseTwoTelemetryAdapter(i.state, ActionProcessor(registry()), identity.sourceCommit, i.playerIds, s.manualSeat)
    }

    fun runner(s: ManualInitializationSpec): ManualPhaseTwoFixtureRunner {
        val a = adapter(s)
        return ManualPhaseTwoFixtureRunner(a, registry(), a.state.turnOrder.associateWith {
            PhaseTwoPilotPolicy { _, _ -> PhaseTwoPilotChoice.Keep }
        }, ManualFixtureStepBudget(4, 60_000))
    }
    fun root() = Files.createTempDirectory("manual-bound-spec-").toRealPath()
    fun bytes() = ManualInitializationReplay.encode(spec())
    fun pin() = ManualInitializationReplay.sha256(bytes())
    fun run(root: Path, factory: (ManualInitializationSpec) -> ManualPhaseTwoFixtureRunner = ::runner) =
        ManualBoundInitialization.runNew(root, identity, bytes(), pin(), registry(), factory)

    test("exact spec and pin precede initializer and compose with accepted reconstruction") {
        val root = root(); val directory = root.resolve(identity.fixtureId)
        val trace = run(root) { s ->
            Files.readAllBytes(directory.resolve("initialization-spec.json")).toList() shouldBe bytes().toList()
            Files.readString(directory.resolve("initialization-spec.sha256")) shouldBe pin() + "\n"
            Files.exists(directory.resolve("initialization-intent.txt")) shouldBe true
            Files.exists(directory.resolve("transitions")) shouldBe false
            runner(s)
        }
        trace.steps.size shouldBe 4
        ManualBoundInitialization.verify(directory, identity, pin(), registry()).storedTransitionCount shouldBe 4
    }
    test("independent spec pin mismatch never invokes initializer or reserves an attempt") {
        val root = root(); var calls = 0
        shouldThrowAny { ManualBoundInitialization.runNew(root, identity, bytes(), "0".repeat(64), registry()) {
            calls++; runner(it)
        } }
        calls shouldBe 0
        Files.list(root).use { it.count() } shouldBe 0L
    }
    test("failed initializer preserves committed spec and cannot retry") {
        val root = root(); var calls = 0
        shouldThrowAny { run(root) { calls++; error("synthetic initialization failure") } }
        val directory = root.resolve(identity.fixtureId)
        Files.readAllBytes(directory.resolve("initialization-spec.json")).toList() shouldBe bytes().toList()
        Files.exists(directory.resolve("fault.txt")) shouldBe true
        shouldThrowAny { run(root) { calls++; runner(it) } }
        calls shouldBe 1
    }
    test("factory that ignores committed seed fails reconstruction without replacing its evidence") {
        val root = root()
        shouldThrowAny { run(root) { runner(it.copy(seed = it.seed + 1)) } }
        val directory = root.resolve(identity.fixtureId)
        Files.exists(directory.resolve("result.bin")) shouldBe true
        Files.readAllBytes(directory.resolve("initialization-spec.json")).toList() shouldBe bytes().toList()
        shouldThrowAny { run(root) }
    }
    test("committed spec and pin tampering reject even with self-consistent new digest") {
        val root = root(); run(root)
        val directory = root.resolve(identity.fixtureId)
        val altered = ManualInitializationReplay.encode(spec().copy(seed = spec().seed + 1))
        val changedPin = ManualInitializationReplay.sha256(altered)
        Files.write(directory.resolve("initialization-spec.json"), altered)
        Files.writeString(directory.resolve("initialization-spec.sha256"), changedPin + "\n")
        shouldThrowAny { ManualBoundInitialization.verify(directory, identity, pin(), registry()) }
        shouldThrowAny { ManualBoundInitialization.verify(directory, identity, changedPin, registry()) }
    }
    test("missing marker and identity drift cannot verify stored initialization") {
        val root = root(); run(root); val directory = root.resolve(identity.fixtureId)
        shouldThrowAny { ManualBoundInitialization.verify(directory, identity.copy(runtimeSha256 = "9".repeat(64)), pin(), registry()) }
        Files.delete(directory.resolve("initialization-spec.sha256"))
        shouldThrowAny { ManualBoundInitialization.verify(directory, identity, pin(), registry()) }
    }
    test("caller mutation after commitment cannot change the detached original spec") {
        val root = root(); val input = bytes()
        ManualBoundInitialization.runNew(root, identity, input, pin(), registry()) {
            input.fill(0); runner(it)
        }
        Files.readAllBytes(root.resolve(identity.fixtureId).resolve("initialization-spec.json")).toList() shouldBe bytes().toList()
    }
})
