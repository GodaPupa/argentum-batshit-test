package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.CastSpell
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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

/** New excluded continuation traces only; prior ten first-question traces are not repeated. */
class SphinxStageEVisibleContinuationTraceTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_PILOT_FIXTURE_BUDGET.json")) }
    private val epoch = ActorEpoch("stage-e-visible-continuation-trace-v1", "excluded-initialized-continuation", 0)
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
            startingPlayerIndex = 0, seed = 0x5350_4849_4E58_0005L,
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

    init {
        listOf("reconstructed-v01", "reconstructed-hybrid").forEach { identity ->
            test("U1 $identity Approach May to exact-five graveyard payment and Sphinx search") {
                val seat = setup(identity, "Sphinx's Approach", 3)
                val copies = seat.state.getLibrary(seat.actor)
                    .filter { name(seat.state, it) == "Sphinx's Approach" }.take(5)
                copies.size shouldBe 5
                copies.forEach {
                    seat.state = ZoneTransitionService.moveToZone(seat.state, it, Zone.GRAVEYARD).state
                }
                seat.advance(CastSpell(seat.actor, seat.sourceId))
                seat.resolveToQuestion()
                seat.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
                seat.advance(seat.proposed())
                val payment = seat.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                payment.minSelections shouldBe 4
                payment.maxSelections shouldBe 4
                seat.advance(seat.proposed())
                val search = seat.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                search.minSelections shouldBe 0
                search.maxSelections shouldBe 1
                seat.advance(seat.proposed())
                seat.state.pendingDecision shouldBe null
                seat.state.getBattlefield(seat.actor).any { name(seat.state, it) == "Goliath Sphinx" } shouldBe true
            }

            test("U2 $identity Snap untaps currently tapped own lands after public target bounce") {
                val seat = setup(identity, "Snap", 2)
                val sphinx = seat.state.getLibrary(seat.actor)
                    .first { name(seat.state, it) == "Goliath Sphinx" }
                seat.state = ZoneTransitionService.moveToZone(seat.state, sphinx, Zone.BATTLEFIELD,
                    ZoneEntryOptions(controllerId = seat.other)).state
                seat.advance(CastSpell(seat.actor, seat.sourceId,
                    targets = listOf(ChosenTarget.Permanent(sphinx))))
                seat.resolveToQuestion()
                val untap = seat.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                untap.minSelections shouldBe 0
                val paidLands = seat.state.getBattlefield(seat.actor).filter {
                    name(seat.state, it) == "Island" &&
                        seat.state.getEntity(it)!!.has<TappedComponent>()
                }
                paidLands.size shouldBe 2
                untap.options.containsAll(paidLands) shouldBe true
                (sphinx in seat.state.getHand(seat.actor)) shouldBe true
                seat.advance(seat.proposed())
                seat.state.pendingDecision shouldBe null
                paidLands.all { !seat.state.getEntity(it)!!.has<TappedComponent>() } shouldBe true
                (sphinx in seat.state.getHand(seat.actor)) shouldBe true
            }
        }
        listOf("closest-no-approach-v01", "serpico-terror-benchmark").forEach { identity ->
            test("U3 $identity Brainstorm selects two then reorders current top cards") {
                val seat = setup(identity, "Brainstorm", 1)
                seat.advance(CastSpell(seat.actor, seat.sourceId))
                seat.resolveToQuestion()
                val choose = seat.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                choose.ordered shouldBe false
                choose.cardInfo?.keys?.containsAll(choose.options) shouldBe true
                seat.advance(seat.proposed())
                val order = seat.state.pendingDecision.shouldBeInstanceOf<ReorderLibraryDecision>()
                order.cards.size shouldBe 2
                seat.advance(seat.proposed())
                seat.state.pendingDecision shouldBe null
            }

            test("U4 $identity Preordain bottoms and reorders using only current look metadata") {
                val seat = setup(identity, "Preordain", 1)
                seat.advance(CastSpell(seat.actor, seat.sourceId))
                seat.resolveToQuestion()
                val choose = seat.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                choose.cardInfo?.keys?.containsAll(choose.options) shouldBe true
                seat.advance(seat.proposed())
                val order = seat.state.pendingDecision.shouldBeInstanceOf<ReorderLibraryDecision>()
                order.cards.isNotEmpty() shouldBe true
                seat.advance(seat.proposed())
                seat.state.pendingDecision shouldBe null
            }

            test("U5 $identity Ponder reorder exposes later unqualified shuffle May") {
                val seat = setup(identity, "Ponder", 1)
                seat.advance(CastSpell(seat.actor, seat.sourceId))
                seat.resolveToQuestion()
                seat.state.pendingDecision.shouldBeInstanceOf<ReorderLibraryDecision>()
                seat.advance(seat.proposed())
                seat.state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
                val input = seat.input()
                seat.pilot.decideVisibleChoice(input, input.epoch)
                    .shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
            }

            test("U6 $identity Lórien typecycling search continues into current own hand") {
                val seat = setup(identity, "Lórien Revealed", 1)
                val before = seat.state.getHand(seat.actor).size
                seat.advance(TypecycleCard(seat.actor, seat.sourceId))
                val search = seat.state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                search.minSelections shouldBe 0
                search.maxSelections shouldBe 1
                seat.advance(seat.proposed())
                seat.state.pendingDecision shouldBe null
                seat.state.getHand(seat.actor).size shouldBe before
            }
        }
    }
}
