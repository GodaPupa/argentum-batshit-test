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

/** Two prospective real-engine accepted-transition/replay traces. Prior memory bank is not rerun. */
class SphinxStageEAcceptedTransitionJournalTest : ScenarioTestBase() {
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

    init {
        listOf(false, true).forEach { shouldShuffle ->
            test("accepted physical Ponder reorder journals and replays shuffle=$shouldShuffle") {
                val seat = setup("closest-no-approach-v01", "Ponder", 5)
                val library = seat.state.getLibrary(seat.actor)
                val top = if (shouldShuffle) {
                    library.filter { name(seat.state, it) == "Island" }.take(3)
                } else {
                    listOf(library.first { name(seat.state, it) == "Counterspell" }) +
                        library.filter { name(seat.state, it) == "Island" }.take(2)
                }
                top.size shouldBe 3
                seat.state = seat.state.copy(zones = seat.state.zones +
                    (ZoneKey(seat.actor, Zone.LIBRARY) to (top + library.filter { it !in top })))
                seat.advance(CastSpell(seat.actor, seat.sourceId))
                seat.resolveToQuestion()
                val reorderInput = seat.input()
                reorderInput.decision.shouldBeInstanceOf<ReorderLibraryDecision>()
                val reorder = seat.pilot.decideVisibleChoice(reorderInput, reorderInput.epoch)
                    .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>().proposal
                val before = seat.state
                val journal = SphinxStageEAcceptedTransitionJournal(actionProcessor, cardRegistry)
                val accepted = journal.acceptPonderReorder(
                    before, reorderInput, reorderInput.epoch, seat.pilot, reorder)
                seat.state = accepted.state
                journal.replay(before, accepted.recordJson) shouldBe accepted.state
                shouldThrow<IllegalArgumentException> {
                    journal.replay(before.copy(turnNumber = before.turnNumber + 1), accepted.recordJson)
                }
                val shuffleInput = seat.input()
                val answer = seat.pilot.decidePonderShuffle(
                    shuffleInput, shuffleInput.epoch, accepted.memory)
                    .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>().proposal
                val submitted = answer.action.shouldBeInstanceOf<SubmitDecision>()
                submitted.response.shouldBeInstanceOf<YesNoResponse>().choice shouldBe shouldShuffle
                seat.advance(submitted)
                seat.state.pendingDecision shouldBe null
            }
        }
    }
}
