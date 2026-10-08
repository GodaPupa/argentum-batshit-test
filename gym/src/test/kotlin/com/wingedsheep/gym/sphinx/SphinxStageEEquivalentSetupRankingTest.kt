package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.*
import com.wingedsheep.engine.handlers.effects.ZoneEntryOptions
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.actorinput.*
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

/** New deterministic synthetic state fixtures; excluded Island seat is not an opponent package. */
class SphinxStageEEquivalentSetupRankingTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/decks/serpico-terror-benchmark.csv")) }
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)
    private data class Fixture(val state: com.wingedsheep.engine.state.GameState,
        val seat: SphinxStageEInitializedSeat, val input: ActorInput, val epoch: ActorEpoch)

    private fun fixture(cardName: String = "Ponder", mixed: Boolean = false): Fixture {
        val bytes = Files.readAllBytes(root.resolve("sphinx-approach/decks/serpico-terror-benchmark.csv"))
        val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
        val actor = EntityId.of("landcycle-actor")
        val other = EntityId.of("landcycle-excluded-seat")
        val epoch0 = ActorEpoch("sphinx-landcycle-synthetic-v1", "landcycle-fixture", 0)
        val initialized = GameInitializer(cardRegistry).initializeGame(GameConfig(
            players = listOf(PlayerConfig("Actor", Deck(own.cards.flatMap { (n, c) -> List(c) { n } }), playerId = actor),
                PlayerConfig("Excluded fixture seat", Deck(List(60) { "Island" }), playerId = other)),
            startingHandSize = 7, skipMulligans = false, useHandSmoother = false,
            startingPlayerIndex = 0, seed = 0x5350_4C43_0001L))
        val all = (initialized.state.getHand(actor) + initialized.state.getLibrary(actor)).sortedBy { it.value }
        fun name(id: EntityId) = initialized.state.getEntity(id)!!.get<CardComponent>()!!.name
        val lorien = all.first { name(it) == "Lórien Revealed" }
        val creatures = all.filter { name(it) in setOf("Cryptic Serpent", "Tolarian Terror") }.take(6)
        val copies = all.filter { name(it) == cardName }.take(if (mixed) 1 else 2) +
            if (mixed) all.filter { name(it) == "Preordain" }.take(1) else emptyList()
        val hand = copies + all.filter { name(it) == "Island" }.take(5)
        val arranged = initialized.copy(state = initialized.state.copy(zones = initialized.state.zones +
            (ZoneKey(actor, Zone.HAND) to hand) + (ZoneKey(actor, Zone.LIBRARY) to all.filter { it !in hand })))
        val seat = SphinxStageEInitializedSeat.bindOpening(arranged, actor, bytes, epoch0)
        var state = arranged.state
        for (player in listOf(actor, other)) {
            val kept = actionProcessor.process(state, KeepHand(player)).result
            kept.error shouldBe null; state = kept.state
        }
        val islands = all.filter { name(it) == "Island" && it !in hand }.take(7)
        for (island in islands) state = ZoneTransitionService.moveToZone(state, island, Zone.BATTLEFIELD,
            ZoneEntryOptions(controllerId = actor)).state
        state = state.updateEntity(actor) { it.with(com.wingedsheep.engine.state.components.player.LandDropsComponent(remaining = 0)) }
        state = state.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN,
            activePlayerId = actor, priorityPlayerId = actor)
        val epoch = epoch0.copy(step = 1)
        return Fixture(state, seat, adapter.build(state, actor,
            completeActorLegalActions(state, actor, enumerator), epoch, 17L), epoch)
    }
    private fun sealed(input: ActorInput) = ActorInputCodec.seal(input)
    private fun proposals(f: Fixture, input: ActorInput = f.input) = input.legalActions.withIndex()
        .filter { it.value.action is CastSpell }.map {
            f.seat.decideCurrentCast(input, f.epoch, it.index, SphinxStageEComponentCall.SETUP_DRAW)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
        }
    init {
        test("equivalent Ponder copies choose stable offered identity and real engine accepts") {
            val f = fixture(); val p = proposals(f); p.size shouldBe 2
            val chosen = SphinxStageEWholeActor.decide(f.input, f.epoch, f.seat)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
            val cast = chosen.proposal.action.shouldBeInstanceOf<CastSpell>()
            cast.cardId shouldBe p.map { (it.proposal.action as CastSpell).cardId }.minBy { it.value }
            actionProcessor.process(f.state, cast).result.error shouldBe null
        }
        test("equivalent Preordain copies also resolve a real current cast ambiguity") {
            val f = fixture("Preordain"); proposals(f).size shouldBe 2
            val chosen = SphinxStageEWholeActor.decide(f.input, f.epoch, f.seat)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
            actionProcessor.process(f.state, chosen.proposal.action).result.error shouldBe null
        }
        test("setup copy choice is invariant under offer enumeration order") {
            val f = fixture()
            val a = SphinxStageEWholeActor.decide(f.input, f.epoch, f.seat)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
            val reversed = sealed(f.input.copy(legalActions = f.input.legalActions.reversed()))
            SphinxStageEWholeActor.decide(reversed, f.epoch, f.seat)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>().proposal.action shouldBe a.proposal.action
        }
        test("mixed setup identities remain unqualified") {
            val f = fixture(mixed = true)
            proposals(f).size shouldBe 2
            SphinxStageEWholeActor.decide(f.input, f.epoch, f.seat)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
        }
        test("modified payload duplicate identity and missing payment cannot rank") {
            val f = fixture(); val p = proposals(f)
            val cast = p.last().proposal.action as CastSpell
            val altered = p.dropLast(1) + p.last().copy(proposal = p.last().proposal.copy(action = cast.copy(xValue = 1)))
            for (bad in listOf(altered, listOf(p.first(), p.first())))
                SphinxStageEEquivalentSetupRanking.choose(f.input, f.epoch, f.seat.actorId, bad)
                    .shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
            val missing = sealed(f.input.copy(legalActions = f.input.legalActions.map { it.copy(basicBluePayment = null) }))
            SphinxStageEEquivalentSetupRanking.choose(missing, f.epoch, f.seat.actorId, p.map {
                it.copy(proposal = it.proposal.copy(inputBindingHash = missing.bindingHash))
            }).shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
        }
        test("stale epoch proposal binding wrong timing and nonbasic board fail closed") {
            val f = fixture(); val p = proposals(f)
            shouldThrowAny { SphinxStageEEquivalentSetupRanking.choose(f.input, f.epoch.copy(step = 2), f.seat.actorId, p) }
            SphinxStageEEquivalentSetupRanking.choose(f.input, f.epoch, f.seat.actorId, p.map {
                it.copy(proposal = it.proposal.copy(inputBindingHash = "stale"))
            }).shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
            val wrongTurn = sealed(f.input.copy(observation = f.input.observation.copy(
                activePlayerId = f.state.turnOrder.single { it != f.seat.actorId })))
            val nonbasic = sealed(f.input.copy(observation = f.input.observation.copy(zones =
                f.input.observation.zones.map { z -> z.copy(cards = z.cards.map {
                    if (it.zone == Zone.BATTLEFIELD) it.copy(name = "Unqualified permanent") else it
                }) })))
            for (bad in listOf(wrongTurn, nonbasic))
                SphinxStageEEquivalentSetupRanking.choose(bad, f.epoch, f.seat.actorId, p.map {
                    it.copy(proposal = it.proposal.copy(inputBindingHash = bad.bindingHash))
                }).shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
        }
    }
}
