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
class ManualInitializationReplayTest : FunSpec({
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
    fun verify(dir: Path, s: ManualInitializationSpec, id: ManualFixtureIdentity = identity): ManualInitializationCheck {
        val bytes = ManualInitializationReplay.encode(s)
        return ManualInitializationReplay.verify(dir, bytes, ManualInitializationReplay.sha256(bytes), id, registry())
    }
    fun recorded(s: ManualInitializationSpec): Path {
        val dir = Files.createTempDirectory("manual-init-replay-").toRealPath().resolve("transitions")
        adapter(s).attachJournal(dir, identity)
        return dir
    }
    test("reconstructs exact initialization behind the accepted four-seat journaled runner") {
        val s = spec()
        val root = Files.createTempDirectory("manual-init-runner-").toRealPath()
        ManualPhaseTwoFixtureRunner.runNewJournaledFixture(root, identity, {
            val a = adapter(s)
            ManualPhaseTwoFixtureRunner(a, registry(), a.state.turnOrder.associateWith {
                PhaseTwoPilotPolicy { _, _ -> PhaseTwoPilotChoice.Keep }
            }, ManualFixtureStepBudget(4, 60_000))
        }, ManualPhaseTwoStoredFixtureReplay::encode)
        val check = verify(root.resolve(identity.fixtureId).resolve("transitions"), s)
        check.initialStateSha256 shouldBe ManualInitializationReplay.sha256(adapter(s).journalInitialEnvelope())
        check.storedTransitionCount shouldBe 4
        check.interruptedIntent shouldBe false
        check.rawStopClassification shouldBe "RESOURCE_CAP"
    }
    test("independently pinned specification rejects changed bytes before reconstruction") {
        val s = spec(); val dir = recorded(s)
        val original = ManualInitializationReplay.encode(s)
        val changed = ManualInitializationReplay.encode(s.copy(seed = s.seed + 1))
        shouldThrowAny { ManualInitializationReplay.verify(dir, changed,
            ManualInitializationReplay.sha256(original), identity, registry()) }
    }
    test("self-consistent changed seed cannot match recorded initial state") {
        val s = spec(); val dir = recorded(s)
        shouldThrowAny { verify(dir, s.copy(seed = s.seed + 1)) }
    }
    test("deck order commander and starting-seat drift cannot match initialization") {
        val s = spec(); val dir = recorded(s)
        val player = s.players.first()
        for (changed in listOf(
            s.copy(players = listOf(player.copy(cards = player.cards.reversed())) + s.players.drop(1)),
            s.copy(players = listOf(player.copy(commanders = player.commanders.reversed())) + s.players.drop(1)),
            s.copy(startingPlayerIndex = 1))) {
            shouldThrowAny { verify(dir, changed) }
        }
    }
    test("identity and designated Manual seat drift reject") {
        val s = spec(); val dir = recorded(s)
        shouldThrowAny { verify(dir, s, identity.copy(runtimeSha256 = "4".repeat(64))) }
        shouldThrowAny { verify(dir, s.copy(manualSeat = 1)) }
    }
    test("reframed altered initial state is not accepted as semantic replay") {
        val s = spec(); val dir = recorded(s)
        val i = initialize(s)
        val changed = PhaseTwoTelemetryAdapter.JSON.encodeToString(
            com.wingedsheep.engine.state.GameState.serializer(), i.state.copy(turnNumber = 44)).toByteArray()
        val frame = "${changed.size}:${ManualInitializationReplay.sha256(changed)}\n".toByteArray() + changed
        Files.write(dir.resolve("initial"), frame)
        shouldThrowAny { verify(dir, s) }
    }
    test("unknown or noncanonical specification and truncated frame reject") {
        val s = spec(); val dir = recorded(s)
        val bytes = ManualInitializationReplay.encode(s) + byteArrayOf(32)
        shouldThrowAny { ManualInitializationReplay.verify(dir, bytes,
            ManualInitializationReplay.sha256(bytes), identity, registry()) }
        shouldThrowAny { verify(dir, s.copy(schema = "UNKNOWN")) }
        Files.write(dir.resolve("initial"), byteArrayOf(1))
        shouldThrowAny { verify(dir, s) }
    }
    test("interrupted transition stays interrupted and initialization check never certifies actions") {
        val s = spec()
        val dir = Files.createTempDirectory("manual-init-interruption-").toRealPath().resolve("transitions")
        val a = adapter(s); a.attachJournal(dir, identity)
        shouldThrowAny { a.processBoundary(KeepHand(a.state.turnOrder.first())) { _, _ -> error("synthetic interruption") } }
        val before = Files.list(dir).use { stream -> stream.toList().associate { it.fileName.toString() to Files.readAllBytes(it).toList() } }
        val check = verify(dir, s)
        check.interruptedIntent shouldBe true
        check.storedTransitionCount shouldBe 0
        check.rawStopClassification shouldBe null
        val after = Files.list(dir).use { stream -> stream.toList().associate { it.fileName.toString() to Files.readAllBytes(it).toList() } }
        after shouldBe before
    }
})
