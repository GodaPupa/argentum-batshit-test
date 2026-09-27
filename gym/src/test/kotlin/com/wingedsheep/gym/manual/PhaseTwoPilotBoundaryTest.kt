package com.wingedsheep.gym.manual

import com.wingedsheep.engine.core.ActionProcessor
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.registry.CardRegistry
import com.wingedsheep.engine.support.TestCards
import com.wingedsheep.gym.contract.StateDigest
import com.wingedsheep.sdk.core.Format
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.dsl.card
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Excluded invented cards and fixed fixture seed; no exact-deck or official allocations. */
class PhaseTwoPilotBoundaryTest : FunSpec({
    fun registry() = CardRegistry().apply {
        register(TestCards.all)
        register(card("Animar, Soul of Elements") {
            manaCost = "{0}"
            typeLine = "Legendary Creature — Human"
            power = 1
            toughness = 1
        })
    }
    fun setup(): Triple<CardRegistry, PhaseTwoTelemetryAdapter, List<EntityId>> {
        val registry = registry()
        val initialized = GameInitializer(registry).initializeGame(GameConfig(
            players = (0..3).map { seat -> PlayerConfig("EXCLUDED_PILOT_SEAT_$seat",
                Deck.of("Forest" to 99), commanderCardNames = listOf("Animar, Soul of Elements")) },
            format = Format.Commander(), startingPlayerIndex = 0, skipMulligans = false,
            useHandSmoother = false, seed = 0x50494c4f54424fL,
        ))
        val adapter = PhaseTwoTelemetryAdapter(initialized.state, ActionProcessor(registry),
            "a".repeat(40), initialized.playerIds, 0)
        return Triple(registry, adapter, initialized.playerIds)
    }
    fun assertMasked(viewer: EntityId, view: com.wingedsheep.gym.contract.TrainingObservation) {
        view.perspectivePlayerId shouldBe viewer
        view.agentToAct shouldBe viewer
        view.stateDigest shouldBe StateDigest.compute(view)
        val own = view.zones.single { it.ownerId == viewer && it.zoneType == Zone.HAND }
        own.cards.size shouldBe 7
        view.zones.filter { it.ownerId != viewer && it.zoneType == Zone.HAND }.forEach {
            it.size shouldBe 7
            it.cards.size shouldBe 0
            it.hidden shouldBe true
        }
        view.zones.filter { it.zoneType == Zone.LIBRARY }.forEach {
            it.cards.size shouldBe 0
            it.hidden shouldBe true
        }
    }

    test("four masked mulligan seats and a priority action pass only through traced boundary") {
        val (registry, adapter, seats) = setup()
        val chosen = mutableListOf<EntityId>()
        val policies = seats.associateWith { seat -> PhaseTwoPilotPolicy { view, prompt ->
            assertMasked(seat, view)
            chosen += seat
            when (prompt) {
                is PhaseTwoPilotPrompt.Mulligan -> PhaseTwoPilotChoice.Keep
                PhaseTwoPilotPrompt.Engine -> PhaseTwoPilotChoice.Action(
                    view.legalActions.single { it.kind == "PassPriority" }.actionId)
                is PhaseTwoPilotPrompt.Bottom -> error("No bottoming after keeping seven")
            }
        } }
        val boundary = PhaseTwoPilotBoundary(adapter, registry, policies)
        repeat(4) { boundary.step().error shouldBe null }
        chosen shouldBe seats
        boundary.step().error shouldBe null
        adapter.stop("RESOURCE_CAP", "EXCLUDED_PILOT_FIXTURE_STOP")
        val trace = adapter.finish()
        trace.steps.size shouldBe 5
        trace.steps.flatMap { it.observations }.count { it.kind == "ACTION_ACCEPTED" } shouldBe 5
        PhaseTwoTelemetryAdapter.replay(trace, ActionProcessor(registry)) shouldBe trace
    }

    test("London free mulligan and later bottom cards are selected from only the actor's hand") {
        val (registry, adapter, seats) = setup()
        var firstSeatChoices = 0
        val policies = seats.associateWith { seat -> PhaseTwoPilotPolicy { view, prompt ->
            when (prompt) {
                is PhaseTwoPilotPrompt.Mulligan -> {
                    assertMasked(seat, view)
                    if (seat == seats.first() && firstSeatChoices++ < 2) PhaseTwoPilotChoice.Mulligan
                    else PhaseTwoPilotChoice.Keep
                }
                is PhaseTwoPilotPrompt.Bottom -> {
                    seat shouldBe seats.first()
                    prompt.count shouldBe 1
                    PhaseTwoPilotChoice.Bottom(view.zones.single {
                        it.ownerId == seat && it.zoneType == Zone.HAND
                    }.cards.take(prompt.count).map { it.entityId })
                }
                PhaseTwoPilotPrompt.Engine -> error("This fixture stops after pregame")
            }
        } }
        val boundary = PhaseTwoPilotBoundary(adapter, registry, policies)
        repeat(7) { boundary.step().error shouldBe null }
        adapter.stop("RESOURCE_CAP", "EXCLUDED_PILOT_FIXTURE_STOP")
        val trace = adapter.finish()
        trace.steps.size shouldBe 7
        trace.steps.flatMap { it.observations }.filter { it.kind == "MULLIGAN_TAKEN" }
            .map { it.data.getValue("free").toString() } shouldBe listOf("true", "false")
        PhaseTwoTelemetryAdapter.replay(trace, ActionProcessor(registry)) shouldBe trace
    }

    test("invalid policy choice records integrity failure without silently passing or retrying") {
        val (registry, adapter, seats) = setup()
        val boundary = PhaseTwoPilotBoundary(adapter, registry,
            seats.associateWith { PhaseTwoPilotPolicy { _, _ -> PhaseTwoPilotChoice.Action(0) } })
        shouldThrow<IllegalArgumentException> { boundary.step() }
        shouldThrow<IllegalStateException> { boundary.step() }
        val trace = adapter.finish()
        trace.steps shouldBe emptyList()
        trace.stopObservation?.kind shouldBe "INTEGRITY_FAILURE"
    }
})
