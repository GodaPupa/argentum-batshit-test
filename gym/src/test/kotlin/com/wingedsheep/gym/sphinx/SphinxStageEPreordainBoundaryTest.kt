package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.CardsSelectedResponse
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.SubmitDecision
import com.wingedsheep.engine.core.TypecycleCard
import com.wingedsheep.engine.core.YesNoResponse
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.handlers.effects.ZoneEntryOptions
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ActorInput
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.completeActorLegalActions
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.serialization.json.Json
import com.wingedsheep.gym.actorinput.ActorInputCodec
import io.kotest.assertions.throwables.shouldThrowAny

/** Two prospective real-engine accepted-transition/replay traces. Prior memory bank is not rerun. */
class SphinxStageEPreordainBoundaryTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_PILOT_FIXTURE_BUDGET.json")) }
    private val epoch = ActorEpoch("stage-e-ponder-memory-trace-v1", "excluded-initialized-ponder-memory", 0)
    private val observation = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)

    private fun name(state: GameState, id: EntityId) = state.getEntity(id)!!.get<CardComponent>()!!.name

    private inner class Seat(val actor: EntityId, val other: EntityId, val sourceId: EntityId,
                             val pilot: SphinxStageEInitializedSeat, var state: GameState) {
        var actorStep = 1L

        fun advance(action: GameAction) {
            val result = actionProcessor.process(state, action).result
            result.error shouldBe null
            state = result.state
        }

        fun resolveToQuestion() {
            repeat(4) {
                if (state.pendingDecision == null) advance(PassPriority(requireNotNull(state.priorityPlayerId)))
            }
            requireNotNull(state.pendingDecision)
        }

        fun input(): ActorInput = observation.build(state, actor,
            completeActorLegalActions(state, actor, enumerator), epoch.copy(step = actorStep++), 0x5350_0006L)

        fun proposed(): SubmitDecision {
            val input = input()
            return pilot.decideVisibleChoice(input, input.epoch)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>().proposal.action
                .shouldBeInstanceOf<SubmitDecision>()
        }
    }

    private fun setup(identity: String, sourceName: String, landsOnBoard: Int): Seat {
        val bytes = Files.readAllBytes(root.resolve("sphinx-approach/decks/$identity.csv"))
        val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
        val names = own.cards.flatMap { (name, count) -> List(count) { name } }
        val actor = EntityId.of("stage-e-continuation-actor")
        val other = EntityId.of("stage-e-continuation-opponent")
        val initial = GameInitializer(cardRegistry).initializeGame(GameConfig(
            players = listOf(PlayerConfig("Actual frozen 60", Deck(names), playerId = actor),
                PlayerConfig("Passive excluded seat", Deck(List(60) { "Island" }), playerId = other)),
            startingHandSize = 7, skipMulligans = false, useHandSmoother = false,
            startingPlayerIndex = 0, seed = 0x5350_4849_4E58_0007L,
        ))
        val all = (initial.state.getHand(actor) + initial.state.getLibrary(actor)).sortedBy { it.value }
        val source = all.first { name(initial.state, it) == sourceName }
        val islands = all.filter { name(initial.state, it) == "Island" }
        val hand = listOf(source) + islands.take(3) + all.filter {
            it != source && it !in islands.take(3) &&
                name(initial.state, it) !in setOf("Goliath Sphinx", "Counterspell", "Ponder")
        }.take(3)
        require(hand.size == 7 && hand.distinct().size == 7)
        val remainder = all.filter { it !in hand }
        val sphinx = remainder.filter { name(initial.state, it) == "Goliath Sphinx" }
        val otherCards = remainder.filter { it !in sphinx }
        val library = otherCards.take(4) + sphinx + otherCards.drop(4)
        val arranged = initial.copy(state = initial.state.copy(zones = initial.state.zones +
            (ZoneKey(actor, Zone.HAND) to hand) + (ZoneKey(actor, Zone.LIBRARY) to library)))
        val pilot = SphinxStageEInitializedSeat.bindOpening(arranged, actor, bytes, epoch)
        val seat = Seat(actor, other, source, pilot, arranged.state)
        seat.advance(KeepHand(actor))
        seat.advance(KeepHand(other))
        islands.take(landsOnBoard).forEach {
            seat.state = ZoneTransitionService.moveToZone(seat.state, it, Zone.BATTLEFIELD,
                ZoneEntryOptions(controllerId = actor)).state
        }
        seat.state = seat.state.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN,
            activePlayerId = actor, priorityPlayerId = actor)
        return seat
    }

    private fun pending(source: String = "Preordain"): Seat {
        val seat = setup("closest-no-approach-v01", source, 1)
        seat.advance(CastSpell(seat.actor, seat.sourceId)); seat.resolveToQuestion()
        return seat
    }
    private fun proposal(seat: Seat, input: ActorInput) =
        SphinxStageEWholeActor.decide(input, input.epoch, seat.pilot)
            .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>().proposal
    private fun boundary(seat: Seat, input: ActorInput) = SphinxStageEPreordainBoundary(
        seat.state, seat.pilot, input.epoch, input.policyRngState, cardRegistry)
    init {
        test("current Preordain selection composes accepted WholeActor and actual engine once") {
            val seat = pending(); val input = seat.input()
            input.decision.shouldBeInstanceOf<SelectCardsDecision>()
            val p = proposal(seat, input); val owner = boundary(seat, input)
            val accepted = owner.executeOnce(input, p)
            accepted shouldBe actionProcessor.process(seat.state, p.action).result
            accepted.error shouldBe null
            shouldThrowAny { owner.executeOnce(input, p) }
        }
        test("resealed altered projection cannot bypass trusted current state") {
            val seat = pending(); val input = seat.input(); val p = proposal(seat, input)
            val bad = ActorInputCodec.seal(input.copy(observation = input.observation.copy(
                activePlayerId = seat.other)))
            val owner = boundary(seat, input)
            shouldThrowAny { owner.executeOnce(bad, p.copy(inputBindingHash = bad.bindingHash)) }
            shouldThrowAny { owner.executeOnce(input, p) }
        }
        test("stale epoch and policy RNG inputs consume without acceptance") {
            val seat = pending(); val input = seat.input(); val p = proposal(seat, input)
            for (bad in listOf(ActorInputCodec.seal(input.copy(epoch = input.epoch.copy(step = input.epoch.step + 1))),
                ActorInputCodec.seal(input.copy(policyRngState = input.policyRngState + 1)))) {
                val owner = boundary(seat, input)
                shouldThrowAny { owner.executeOnce(bad, p.copy(inputBindingHash = bad.bindingHash)) }
                shouldThrowAny { owner.executeOnce(input, p) }
            }
        }
        test("wrong actor action and stale proposal hash reject without fallback") {
            val seat = pending(); val input = seat.input(); val p = proposal(seat, input)
            val action = p.action.shouldBeInstanceOf<SubmitDecision>()
            for (bad in listOf(p.copy(inputBindingHash = "stale"), p.copy(action = action.copy(playerId = seat.other)))) {
                val owner = boundary(seat, input)
                shouldThrowAny { owner.executeOnce(input, bad) }
                shouldThrowAny { owner.executeOnce(input, p) }
            }
        }
        test("another accepted typed source is not silently generalized into Preordain scope") {
            val seat = pending("Ponder"); val input = seat.input(); val p = proposal(seat, input)
            shouldThrowAny { boundary(seat, input).executeOnce(input, p) }
        }
        test("previous decision projection cannot be submitted to its successor state") {
            val seat = pending(); val input = seat.input(); val p = proposal(seat, input)
            val result = boundary(seat, input).executeOnce(input, p)
            val owner = SphinxStageEPreordainBoundary(result.state, seat.pilot,
                input.epoch.copy(step = input.epoch.step + 1), input.policyRngState, cardRegistry)
            shouldThrowAny { owner.executeOnce(input, p) }
        }
    }
}
