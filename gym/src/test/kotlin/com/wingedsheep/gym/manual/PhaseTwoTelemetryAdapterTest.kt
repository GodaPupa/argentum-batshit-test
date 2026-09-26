package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.components.identity.CardComponent
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

/** Invented cards and deterministic adapter fixtures only; never a frozen experiment deck. */
class PhaseTwoTelemetryAdapterTest : FunSpec({
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
    fun observations(trace: PhaseTwoEngineTrace) = trace.initialObservations +
        trace.steps.flatMap { it.observations } + listOfNotNull(trace.stopObservation)
    fun toMain(a: PhaseTwoTelemetryAdapter, initial: InitializationResult) {
        initial.playerIds.forEach { a.process(KeepHand(it)).error shouldBe null }
        var passes = 0
        while (a.state.step != Step.PRECOMBAT_MAIN) {
            check(passes++ < 40) { "Fixture failed to reach main phase" }
            a.process(PassPriority(requireNotNull(a.state.priorityPlayerId))).error shouldBe null
        }
    }
    fun resolve(a: PhaseTwoTelemetryAdapter) {
        var passes = 0
        while (a.state.stack.isNotEmpty()) {
            check(passes++ < 20)
            a.process(PassPriority(requireNotNull(a.state.priorityPlayerId))).error shouldBe null
        }
    }

    test("accepted engine actions produce ordered evidence and real free mulligan flags") {
        val initial = initial()
        val a = adapter(initial)
        repeat(2) { a.process(TakeMulligan(initial.playerIds[0])).error shouldBe null }
        a.stop("RESOURCE_CAP", "EXCLUDED_FIXTURE_END")
        val trace = a.finish()
        trace.steps.size shouldBe 2
        trace.steps.map { it.sequence } shouldBe listOf(1, 2)
        observations(trace).filter { it.kind == "MULLIGAN_TAKEN" }
            .map { it.data.getValue("free").jsonPrimitive.boolean } shouldBe listOf(true, false)
        observations(trace).count { it.kind == "ACTION_ACCEPTED" } shouldBe 2
        PhaseTwoTelemetryAdapter.replay(trace, ActionProcessor(registry())) shouldBe trace
    }

    test("partner commanders remain distinct and Animar departure follows its exact entity") {
        val initial = initial()
        val a = adapter(initial)
        toMain(a, initial)
        for (name in names.take(2)) {
            val id = a.state.getZone(initial.playerIds[0], Zone.COMMAND).single {
                a.state.requireEntity(it).get<CardComponent>()!!.name == name
            }
            a.process(CastSpell(initial.playerIds[0], id)).error shouldBe null
            resolve(a)
        }
        a.process(Concede(initial.playerIds[0])).error shouldBe null
        a.stop("RESOURCE_CAP", "EXCLUDED_FIXTURE_END")
        val trace = a.finish()
        observations(trace).filter { it.kind == "COMMANDER_CAST" }
            .map { it.data.getValue("commander_identity").jsonPrimitive.content } shouldBe names.take(2)
        observations(trace).count { it.kind == "ANIMAR_REMOVED" } shouldBe 1
        observations(trace).count { it.kind == "PLAYER_ELIMINATED" } shouldBe 1
        PhaseTwoTelemetryAdapter.replay(trace, ActionProcessor(registry())) shouldBe trace
    }

    test("personal turn counters use authoritative engine counts including departed seats") {
        val initial = initial()
        val a = adapter(initial)
        toMain(a, initial)
        a.process(Concede(initial.playerIds[1])).error shouldBe null
        var passes = 0
        while (a.state.turnNumber < 2) {
            check(passes++ < 100)
            a.process(PassPriority(requireNotNull(a.state.priorityPlayerId))).error shouldBe null
        }
        a.stop("TIMEOUT", "EXCLUDED_FIXTURE_END")
        val trace = a.finish()
        val clock = observations(trace).last { it.kind == "WINDOW" }.data
        clock.getValue("turn").jsonPrimitive.int shouldBe 2
        val rounds = clock.getValue("personal_rounds").jsonObject
        rounds[initial.playerIds[0].value]!!.jsonPrimitive.int shouldBe 1
        rounds[initial.playerIds[1].value]!!.jsonPrimitive.int shouldBe 0
        rounds[initial.playerIds[2].value]!!.jsonPrimitive.int shouldBe 1
        rounds[initial.playerIds[3].value]!!.jsonPrimitive.int shouldBe 0
        PhaseTwoTelemetryAdapter.replay(trace, ActionProcessor(registry())) shouldBe trace
    }

    test("rejected action is preserved as an invalid trace and never accepted or a deck loss") {
        val initial = initial()
        val a = adapter(initial)
        a.process(CastSpell(initial.playerIds[0], com.wingedsheep.sdk.model.EntityId("missing-fixture-card")))
            .error.isNullOrBlank() shouldBe false
        val trace = a.finish()
        trace.steps.single().accepted shouldBe false
        trace.steps.single().beforeStateSha256 shouldBe trace.steps.single().afterStateSha256
        observations(trace).count { it.kind == "ACTION_ACCEPTED" } shouldBe 0
        observations(trace).last().kind shouldBe "INTEGRITY_FAILURE"
        PhaseTwoTelemetryAdapter.replay(trace, ActionProcessor(registry())) shouldBe trace
        shouldThrow<IllegalStateException> { a.process(Concede(initial.playerIds[0])) }
    }

    test("actual terminal event carries winner and ordered eliminations through JSON roundtrip replay") {
        val initial = initial()
        val a = adapter(initial)
        initial.playerIds.take(3).forEach { a.process(Concede(it)).error shouldBe null }
        val trace = a.finish()
        observations(trace).filter { it.kind == "PLAYER_ELIMINATED" }
            .map { it.data.getValue("player_id").jsonPrimitive.content } shouldBe initial.playerIds.take(3).map { it.value }
        observations(trace).last().kind shouldBe "GAME_WON"
        observations(trace).last().data.getValue("winner").jsonPrimitive.content shouldBe initial.playerIds[3].value
        val json = PhaseTwoTelemetryAdapter.JSON
        val bytes = json.encodeToString(PhaseTwoEngineTrace.serializer(), trace)
        val restored = json.decodeFromString(PhaseTwoEngineTrace.serializer(), bytes)
        PhaseTwoTelemetryAdapter.replay(restored, ActionProcessor(registry())) shouldBe trace
        System.getenv("MT_P2_ENGINE_TRACE_OUTPUT")?.let { output ->
            val directory = Path.of(output)
            Files.createDirectories(directory)
            Files.writeString(directory.resolve("excluded-fixture-engine-trace.json"), bytes)
        }
    }

    test("independent engine replay rejects altered action extraction state hashes and ordering") {
        val initial = initial()
        val a = adapter(initial)
        repeat(2) { a.process(TakeMulligan(initial.playerIds[0])) }
        a.stop("RESOURCE_CAP", "EXCLUDED_FIXTURE_END")
        val trace = a.finish()
        val first = trace.steps.first()
        val changedObservation = first.observations.first().copy(data = buildJsonObject {
            put("player_id", initial.playerIds[1].value)
        })
        val corruptions = listOf(
            trace.copy(steps = listOf(first.copy(observations = listOf(changedObservation))) + trace.steps.drop(1)),
            trace.copy(steps = listOf(first.copy(afterStateSha256 = "0".repeat(64))) + trace.steps.drop(1)),
            trace.copy(steps = trace.steps.reversed()),
            trace.copy(executionAuthorizedByThisComponent = true),
        )
        corruptions.forEach { corrupted ->
            shouldThrow<IllegalArgumentException> { PhaseTwoTelemetryAdapter.replay(corrupted, ActionProcessor(registry())) }
        }
    }

    test("unfinished evidence cannot be sealed and explicit limits stay separate from draws") {
        val a = adapter()
        shouldThrow<IllegalStateException> { a.finish() }
        shouldThrow<IllegalArgumentException> { a.stop("RULES_DRAW", "fixture cap cannot be a draw") }
        a.stop("TIMEOUT", "EXCLUDED_FIXTURE_END")
        val trace = a.finish()
        observations(trace).last().kind shouldBe "TIMEOUT"
        observations(trace).any { it.kind == "GAME_WON" || it.kind == "RULES_DRAW" } shouldBe false
        shouldThrow<IllegalStateException> { a.finish() }
        PhaseTwoTelemetryAdapter.replay(trace, ActionProcessor(registry())) shouldBe trace
    }

    test("throwing engine seals and preserves unknown acceptance without permitting retry") {
        val initial = initial()
        val a = adapter(initial)
        val action = TakeMulligan(initial.playerIds[0])
        shouldThrow<IllegalStateException> {
            a.processBoundary(action) { _, _ -> error("EXCLUDED_FIXTURE_ENGINE_FAILURE") }
        }
        shouldThrow<IllegalStateException> { a.process(action) }
        val trace = a.finish()
        trace.steps.single().failurePhase shouldBe "ENGINE"
        trace.steps.single().accepted shouldBe null
        trace.steps.single().afterStateSha256 shouldBe null
        observations(trace).last().kind shouldBe "INTEGRITY_FAILURE"
        shouldThrow<IllegalArgumentException> { PhaseTwoTelemetryAdapter.replay(trace, ActionProcessor(registry())) }
    }

    test("throwing collector retains actual engine result and seals without permitting retry") {
        val initial = initial()
        val a = adapter(initial)
        val action = TakeMulligan(initial.playerIds[0])
        shouldThrow<IllegalArgumentException> {
            a.processBoundary(action) { state, submitted ->
                val actual = ActionProcessor(registry()).process(state, submitted).result
                // Deliberately corrupt the test-only result's terminal event contract after the
                // real engine action, so the extractor fails after acceptance is already known.
                actual.copy(state = actual.state.copy(gameOver = true))
            }
        }
        shouldThrow<IllegalStateException> { a.process(action) }
        val trace = a.finish()
        trace.steps.single().failurePhase shouldBe "COLLECTOR"
        trace.steps.single().accepted shouldBe true
        observations(trace).count { it.kind == "ACTION_ACCEPTED" } shouldBe 1
        observations(trace).last().kind shouldBe "INTEGRITY_FAILURE"
        shouldThrow<IllegalArgumentException> { PhaseTwoTelemetryAdapter.replay(trace, ActionProcessor(registry())) }
    }
})
