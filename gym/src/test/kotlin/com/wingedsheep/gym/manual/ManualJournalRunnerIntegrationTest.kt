package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.player.MulliganStateComponent
import com.wingedsheep.engine.state.components.player.PlayerTurnsTakenComponent
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import com.wingedsheep.sdk.model.EntityId

/** Invented cards and deterministic adapter fixtures only; never a frozen experiment deck. */
class ManualJournalRunnerIntegrationTest : FunSpec({
    // Names identify designated commander entities for extraction. These zero-cost vanilla fixtures
    // do not implement or qualify the printed card's mechanics and never enter the shared registry.
    fun commander(name: String) = card(name) {
        manaCost = "{0}"
        typeLine = "Legendary Creature — Human"
        power = 1
        toughness = 1
    }
    val names = listOf("Animar, Soul of Elements", "Fixture Partner", "Fixture Opponent")
    fun registry() = CardRegistry().apply { register(TestCards.all); names.forEach { register(commander(it)) } }
    fun initial(): InitializationResult = GameInitializer(registry()).initializeGame(GameConfig(
        players = (0..3).map { seat -> PlayerConfig("EXCLUDED_FIXTURE_SEAT_$seat",
            Deck.of("Forest" to if (seat == 0) 98 else 99),
            commanderCardNames = if (seat == 0) names.take(2) else listOf(names.last())) },
        format = Format.Commander(), startingPlayerIndex = 0, skipMulligans = false,
        useHandSmoother = false, seed = 0x4d54503254454cL,
    ))
    fun adapter(initial: InitializationResult = initial()) = PhaseTwoTelemetryAdapter(
        initial.state, ActionProcessor(registry()), "8".repeat(40), initial.playerIds, 0)

    fun identity() = ManualFixtureIdentity("manual-fixture-journal-integration", "8".repeat(40),
        "CRUISE", List(4) { "1".repeat(64) }, List(4) { "2".repeat(64) }, "3".repeat(64))
    fun directory() = Files.createTempDirectory("manual-journal-integration-").toRealPath().resolve("transitions")
    fun inspect(a: PhaseTwoTelemetryAdapter, dir: Path, id: ManualFixtureIdentity = identity()) =
        ManualTransitionJournal.inspect(dir, id.bytes(), a.journalInitialEnvelope())
    fun classification(row: ManualJournalTransition) = Json.parseToJsonElement(
        row.resultEnvelope.toString(Charsets.UTF_8)).jsonObject.getValue("classification").jsonPrimitive.content

    test("accepted engine result is persisted before runner state advances") {
        val i = initial(); val a = adapter(i); val dir = directory()
        a.attachJournal(dir, identity())
        val response = a.process(KeepHand(i.playerIds[0]))
        response.error shouldBe null
        a.state shouldBe response.state
        val row = inspect(a, dir).transitions.single()
        classification(row) shouldBe "ACCEPTED"
        val envelope = Json.parseToJsonElement(row.resultEnvelope.toString(Charsets.UTF_8)).jsonObject
        PhaseTwoTelemetryAdapter.JSON.decodeFromJsonElement(ExecutionResult.serializer(),
            envelope.getValue("response")) shouldBe response
        Json.parseToJsonElement(row.action.toString(Charsets.UTF_8)).jsonObject
            .getValue("source").jsonPrimitive.content shouldBe identity().sourceCommit
        a.stop("RESOURCE_CAP", "synthetic test limit")
        inspect(a, dir).rawStopClassification shouldBe "RESOURCE_CAP"
    }
    test("actual engine rejection is stored and never advances accepted state") {
        val i = initial(); val a = adapter(i); val dir = directory()
        a.attachJournal(dir, identity())
        val response = a.process(CastSpell(i.playerIds[0], EntityId("missing-fixture-card")))
        (response.error != null) shouldBe true
        a.state shouldBe i.state
        classification(inspect(a, dir).transitions.single()) shouldBe "REJECTED"
        inspect(a, dir).rawStopClassification shouldBe "ENGINE_REJECTED_ACTION"
        a.finish().steps.single().accepted shouldBe false
        shouldThrow<IllegalStateException> { a.process(KeepHand(i.playerIds[0])) }
    }
    test("malformed rejection is preserved but its state is never promoted") {
        val i = initial(); val a = adapter(i); val dir = directory()
        a.attachJournal(dir, identity())
        shouldThrow<IllegalArgumentException> {
            a.processBoundary(KeepHand(i.playerIds[0])) { before, _ ->
                ExecutionResult.error(before.copy(turnNumber = 77), "injected malformed rejection")
            }
        }
        a.state shouldBe i.state
        classification(inspect(a, dir).transitions.single()) shouldBe "INVALID_REJECTION"
        inspect(a, dir).rawStopClassification shouldBe "INTEGRITY_FAILURE"
        a.finish().steps.single().failurePhase shouldBe "COLLECTOR"
    }
    test("engine exception leaves an interrupted intent and consumes writer and adapter") {
        val i = initial(); val a = adapter(i); val dir = directory()
        a.attachJournal(dir, identity())
        shouldThrow<IllegalStateException> {
            a.processBoundary(KeepHand(i.playerIds[0])) { _, _ -> error("injected engine interruption") }
        }
        inspect(a, dir).interruptedIntent shouldBe true
        inspect(a, dir).rawStopClassification shouldBe null
        a.state shouldBe i.state
        a.finish().steps.single().failurePhase shouldBe "ENGINE"
        shouldThrow<IllegalStateException> { a.process(KeepHand(i.playerIds[0])) }
        shouldThrow<java.nio.file.FileAlreadyExistsException> { adapter(i).attachJournal(dir, identity()) }
    }
    test("failed result persistence cannot promote returned engine state or permit retry") {
        val i = initial(); val a = adapter(i); val dir = directory()
        a.attachJournal(dir, identity())
        Files.createDirectory(dir.resolve("0.result")) // Deliberate durable-storage collision.
        shouldThrow<java.nio.file.FileAlreadyExistsException> { a.process(KeepHand(i.playerIds[0])) }
        a.state shouldBe i.state
        Files.exists(dir.resolve("0.intent")) shouldBe true
        shouldThrow<IllegalArgumentException> { inspect(a, dir) }
        a.finish().steps.single().failurePhase shouldBe "JOURNAL"
        shouldThrow<IllegalStateException> { a.process(KeepHand(i.playerIds[0])) }
    }
    test("mismatched engine source is rejected before journal creation") {
        val a = adapter(); val dir = directory()
        shouldThrow<IllegalArgumentException> { a.attachJournal(dir, identity().copy(sourceCommit = "9".repeat(40))) }
        Files.exists(dir) shouldBe false
    }
    test("journal identity verification rejects deck policy runtime and gear drift") {
        val a = adapter(); val dir = directory(); val id = identity()
        a.attachJournal(dir, id)
        listOf(id.copy(gear = "RACE"), id.copy(deckSha256 = List(4) { "4".repeat(64) }),
            id.copy(pilotSha256 = List(4) { "5".repeat(64) }), id.copy(runtimeSha256 = "6".repeat(64)))
            .forEach { changed -> shouldThrow<IllegalArgumentException> { inspect(a, dir, changed) } }
    }
    test("whole runner reserves before initialization and persists four accepted keeps and cap") {
        val root = Files.createTempDirectory("manual-journal-runner-").toRealPath()
        val id = identity()
        lateinit var a: PhaseTwoTelemetryAdapter
        var initialized = 0
        val factory = {
            initialized++
            Files.exists(root.resolve(id.fixtureId).resolve("initialization-intent.txt")) shouldBe true
            val i = initial(); a = adapter(i)
            ManualPhaseTwoFixtureRunner(a, registry(), i.playerIds.associateWith {
                PhaseTwoPilotPolicy { _, prompt ->
                    (prompt is PhaseTwoPilotPrompt.Mulligan) shouldBe true
                    PhaseTwoPilotChoice.Keep
                }
            }, ManualFixtureStepBudget(4, 60_000))
        }
        val codec: (PhaseTwoEngineTrace) -> ByteArray = {
            PhaseTwoTelemetryAdapter.JSON.encodeToString(PhaseTwoEngineTrace.serializer(), it).toByteArray()
        }
        val trace = ManualPhaseTwoFixtureRunner.runNewJournaledFixture(root, id, factory, codec)
        trace.steps.size shouldBe 4
        trace.steps.all { it.accepted == true } shouldBe true
        val stored = inspect(a, root.resolve(id.fixtureId).resolve("transitions"))
        stored.transitions.map(::classification) shouldBe List(4) { "ACCEPTED" }
        stored.rawStopClassification shouldBe "RESOURCE_CAP"
        Files.exists(root.resolve(id.fixtureId).resolve("complete.txt")) shouldBe true
        shouldThrow<java.nio.file.FileAlreadyExistsException> {
            ManualPhaseTwoFixtureRunner.runNewJournaledFixture(root, id, factory, codec)
        }
        initialized shouldBe 1
    }
})
