package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.OrderedResponse
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

/** New excluded engine-backed reorder continuation qualification; accepted source remains unchanged. */
class SphinxStageEPreordainSplitTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_PILOT_FIXTURE_BUDGET.json")) }
    private val epoch = ActorEpoch("e1250a48cf98520f3c8d4450c894786f4c723231", "excluded-preordain-splits", 0)
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
    private fun split(bottomCount: Int, reverse: Boolean): Pair<Seat, List<EntityId>> {
        val seat = setup("closest-no-approach-v01", "Preordain", 5)
        val library = seat.state.getLibrary(seat.actor)
        val low = library.filter { name(seat.state, it) == "Island" }.take(bottomCount)
        low.size shouldBe bottomCount
        val high = library.first { name(seat.state, it) == "Counterspell" }
        val top = (if (bottomCount == 1) low + high else low).let { if (reverse) it.reversed() else it }
        val prefix = if (bottomCount == 2) top + high else top
        seat.state = seat.state.copy(zones = seat.state.zones +
            (ZoneKey(seat.actor, Zone.LIBRARY) to (prefix + library.filter { it !in prefix })))
        seat.advance(CastSpell(seat.actor, seat.sourceId)); seat.resolveToQuestion()
        seat.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        return seat to low
    }
    private fun completeSplit(bottomCount: Int, reverse: Boolean) {
        val (seat, low) = split(bottomCount, reverse)
        val library = seat.state.getLibrary(seat.actor)
        val hand = seat.state.getHand(seat.actor).toSet()
        val drawn = library.first { it !in low }
        val first = seat.input(); val q = first.decision as SelectCardsDecision
        q.context.sourceId shouldBe seat.sourceId
        val p = proposal(seat, first)
        (p.action as SubmitDecision).response.shouldBeInstanceOf<CardsSelectedResponse>().selectedCards.toSet() shouldBe low.toSet()
        var bottomOrder = low
        val owner = boundary(seat, first)
        seat.state = owner.executeOnce(first, p).state
        shouldThrowAny { owner.executeOnce(first, p) }
        if (seat.state.pendingDecision != null) {
            val next = seat.input()
            val reorder = next.decision.shouldBeInstanceOf<ReorderLibraryDecision>()
            reorder.context.sourceId shouldBe seat.sourceId
            reorder.context.sourceName shouldBe "Preordain"
            reorder.playerId shouldBe seat.actor
            (reorder.id != q.id) shouldBe true
            reorder.cards.toSet() shouldBe low.toSet()
            val ordered = proposal(seat, next)
            bottomOrder = ((ordered.action as SubmitDecision).response as OrderedResponse).orderedObjects
            bottomOrder shouldBe low.sortedBy { it.value }
            val second = boundary(seat, next)
            seat.state = second.executeOnce(next, ordered).state
            shouldThrowAny { second.executeOnce(next, ordered) }
        }
        seat.state.pendingDecision shouldBe null
        seat.state.stack.isEmpty() shouldBe true
        seat.state.continuationStack.isEmpty() shouldBe true
        seat.state.getHand(seat.actor).toSet() shouldBe hand + drawn
        seat.state.getHand(seat.actor).size shouldBe hand.size + 1
        seat.state.getLibrary(seat.actor) shouldBe library.filter { it !in low && it != drawn } + bottomOrder
        seat.state.getLibrary(seat.actor).takeLast(bottomCount).toSet() shouldBe low.toSet()
        (seat.sourceId in seat.state.zones[ZoneKey(seat.actor, Zone.GRAVEYARD)].orEmpty()) shouldBe true
        (drawn in seat.state.getLibrary(seat.actor)) shouldBe false
    }
    init {
        for (count in listOf(1, 2)) for (reverse in listOf(false, true)) {
            test("real Preordain bottom $count reverse $reverse has exact final hand library and spell destinations") {
                completeSplit(count, reverse)
            }
        }
        test("wrong bottom-card selection cannot reach final-state promotion and consumes owner") {
            val (seat, low) = split(1, false); val before = seat.state
            val input = seat.input(); val p = proposal(seat, input); val action = p.action as SubmitDecision
            val response = action.response as CardsSelectedResponse
            val wrong = (input.decision as SelectCardsDecision).options.single { it !in low }
            val owner = boundary(seat, input)
            shouldThrowAny { owner.executeOnce(input, p.copy(action = action.copy(
                response = response.copy(selectedCards = listOf(wrong))))) }
            shouldThrowAny { owner.executeOnce(input, p) }
            seat.state shouldBe before
        }
        test("resealed altered bottom-choice source and stale epoch fail without state promotion") {
            val (seat, _) = split(2, true); val before = seat.state
            val input = seat.input(); val p = proposal(seat, input); val q = input.decision as SelectCardsDecision
            for (bad in listOf(ActorInputCodec.seal(input.copy(decision = q.copy(
                context = q.context.copy(sourceId = EntityId.of("different-source"))))),
                ActorInputCodec.seal(input.copy(epoch = input.epoch.copy(step = input.epoch.step + 1))))) {
                val owner = boundary(seat, input)
                shouldThrowAny { owner.executeOnce(bad, p.copy(inputBindingHash = bad.bindingHash)) }
                shouldThrowAny { owner.executeOnce(input, p) }
                seat.state shouldBe before
            }
        }
    }
}
