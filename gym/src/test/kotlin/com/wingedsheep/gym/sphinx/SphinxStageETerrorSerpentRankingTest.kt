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
class SphinxStageETerrorSerpentRankingTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/decks/serpico-terror-benchmark.csv")) }
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)
    private data class Fixture(val state: com.wingedsheep.engine.state.GameState,
        val seat: SphinxStageEInitializedSeat, val input: ActorInput, val epoch: ActorEpoch)

    private fun fixture(discount: Boolean = false): Fixture {
        val mixed = true
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
        val copies = all.filter { name(it) == "Tolarian Terror" }.take(if (mixed) 1 else 2) +
            if (mixed) all.filter { name(it) == "Cryptic Serpent" }.take(1) else emptyList()
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
        if (discount) {
            val fuel = all.filter { name(it) in setOf("Mental Note", "Thought Scour", "Counterspell", "Brainstorm", "Ponder", "Preordain") }.take(6)
            fuel.size shouldBe 6
            for (id in fuel) state = ZoneTransitionService.moveToZone(state, id, Zone.GRAVEYARD).state
        }
        val epoch = epoch0.copy(step = 1)
        return Fixture(state, seat, adapter.build(state, actor,
            completeActorLegalActions(state, actor, enumerator), epoch, 17L), epoch)
    }
    private fun sealed(input: ActorInput) = ActorInputCodec.seal(input)
    private fun proposals(f: Fixture, input: ActorInput = f.input) = input.legalActions.withIndex()
        .filter { it.value.action is CastSpell }.map {
            f.seat.decideCurrentCast(input, f.epoch, it.index, SphinxStageEComponentCall.DEPLOYMENT)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
        }
    init {
        test("equal-cost current deployments choose greater visible power and engine accepts") {
            val f = fixture(); proposals(f).size shouldBe 2
            val chosen = SphinxStageEWholeActor.decide(f.input, f.epoch, f.seat)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
            val cast = chosen.proposal.action.shouldBeInstanceOf<CastSpell>()
            f.state.getEntity(cast.cardId)!!.get<CardComponent>()!!.name shouldBe "Cryptic Serpent"
            actionProcessor.process(f.state, cast).result.error shouldBe null
        }
        test("real graveyard discount makes lower canonical cost Terror the selected cast") {
            val f = fixture(discount = true)
            val costs = f.input.legalActions.filter { it.action is CastSpell }
                .associate { f.state.getEntity((it.action as CastSpell).cardId)!!.get<CardComponent>()!!.name to it.basicBluePayment!!.totalMana }
            costs["Tolarian Terror"] shouldBe 1
            costs["Cryptic Serpent"] shouldBe 2
            val chosen = SphinxStageEWholeActor.decide(f.input, f.epoch, f.seat)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
            val cast = chosen.proposal.action.shouldBeInstanceOf<CastSpell>()
            f.state.getEntity(cast.cardId)!!.get<CardComponent>()!!.name shouldBe "Tolarian Terror"
            actionProcessor.process(f.state, cast).result.error shouldBe null
        }
        test("ranking does not depend on enumeration order") {
            val f = fixture()
            val a = SphinxStageEWholeActor.decide(f.input, f.epoch, f.seat)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
            val reverse = sealed(f.input.copy(legalActions = f.input.legalActions.reversed()))
            val b = SphinxStageEWholeActor.decide(reverse, f.epoch, f.seat)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
            a.proposal.action shouldBe b.proposal.action
        }
        test("nonbasic public board and wrong timing remain unqualified") {
            val f = fixture()
            val changed = sealed(f.input.copy(observation = f.input.observation.copy(zones =
                f.input.observation.zones.map { z -> z.copy(cards = z.cards.map {
                    if (it.zone == Zone.BATTLEFIELD) it.copy(name = "Unqualified public permanent") else it
                }) })))
            val p = proposals(f).map { it.copy(proposal = it.proposal.copy(inputBindingHash = changed.bindingHash)) }
            SphinxStageETerrorSerpentRanking.choose(changed, f.epoch, f.seat.actorId, p)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
            val wrongTurn = sealed(f.input.copy(observation = f.input.observation.copy(
                activePlayerId = f.state.turnOrder.single { it != f.seat.actorId })))
            SphinxStageETerrorSerpentRanking.choose(wrongTurn, f.epoch, f.seat.actorId, proposals(f).map {
                it.copy(proposal = it.proposal.copy(inputBindingHash = wrongTurn.bindingHash))
            }).shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
        }
        test("changed payload duplicate identity or missing payment refuses ranking") {
            val f = fixture(); val p = proposals(f)
            val cast = p.last().proposal.action as CastSpell
            val altered = p.dropLast(1) + p.last().copy(proposal = p.last().proposal.copy(action = cast.copy(xValue = 1)))
            SphinxStageETerrorSerpentRanking.choose(f.input, f.epoch, f.seat.actorId, altered)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
            SphinxStageETerrorSerpentRanking.choose(f.input, f.epoch, f.seat.actorId, listOf(p.first(), p.first()))
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
            val noPayment = sealed(f.input.copy(legalActions = f.input.legalActions.map { it.copy(basicBluePayment = null) }))
            SphinxStageETerrorSerpentRanking.choose(noPayment, f.epoch, f.seat.actorId, p.map {
                it.copy(proposal = it.proposal.copy(inputBindingHash = noPayment.bindingHash))
            }).shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
        }
        test("stale epoch and proposal binding reject") {
            val f = fixture(); val p = proposals(f)
            shouldThrowAny { SphinxStageETerrorSerpentRanking.choose(f.input, f.epoch.copy(step = 2), f.seat.actorId, p) }
            SphinxStageETerrorSerpentRanking.choose(f.input, f.epoch, f.seat.actorId, p.map {
                it.copy(proposal = it.proposal.copy(inputBindingHash = "stale"))
            }).shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
        }
    }
}
