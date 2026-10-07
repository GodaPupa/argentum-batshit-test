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
class SphinxStageEOrdinaryPriorityTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/decks/serpico-terror-benchmark.csv")) }
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)
    private data class Fixture(val state: com.wingedsheep.engine.state.GameState,
        val seat: SphinxStageEInitializedSeat, val input: ActorInput, val epoch: ActorEpoch)

    private fun fixture(): Fixture {
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
        val hand = all.filter { name(it) in setOf("Cryptic Serpent", "Tolarian Terror") }.take(7)
        val arranged = initialized.copy(state = initialized.state.copy(zones = initialized.state.zones +
            (ZoneKey(actor, Zone.HAND) to hand) + (ZoneKey(actor, Zone.LIBRARY) to all.filter { it !in hand })))
        val seat = SphinxStageEInitializedSeat.bindOpening(arranged, actor, bytes, epoch0)
        var state = arranged.state
        for (player in listOf(actor, other)) {
            val kept = actionProcessor.process(state, KeepHand(player)).result
            kept.error shouldBe null; state = kept.state
        }
        val island = all.first { name(it) == "Island" }
        state = ZoneTransitionService.moveToZone(state, island, Zone.BATTLEFIELD,
            ZoneEntryOptions(controllerId = actor)).state
        state = state.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN,
            activePlayerId = actor, priorityPlayerId = actor)
        val epoch = epoch0.copy(step = 1)
        return Fixture(state, seat, adapter.build(state, actor,
            completeActorLegalActions(state, actor, enumerator), epoch, 17L), epoch)
    }
    private fun sealed(input: ActorInput) = ActorInputCodec.seal(input)
    init {
        test("whole actor passes real empty-stack Island mana window and engine accepts") {
            val f = fixture()
            f.input.legalActions.any { it.isManaAbility } shouldBe true
            val result = SphinxStageEWholeActor.decide(f.input, f.epoch, f.seat)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
            result.proposal.action.shouldBeInstanceOf<PassPriority>()
            actionProcessor.process(f.state, result.proposal.action).result.error shouldBe null
        }
        test("opponent turn with only basic mana alternatives also has a bounded pass") {
            val f = fixture()
            val other = f.state.turnOrder.single { it != f.seat.actorId }
            val state = f.state.copy(activePlayerId = other)
            val input = adapter.build(state, f.seat.actorId,
                completeActorLegalActions(state, f.seat.actorId, enumerator), f.epoch, 17L)
            val result = SphinxStageEWholeActor.decide(input, f.epoch, f.seat)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
            result.proposal.action.shouldBeInstanceOf<PassPriority>()
            actionProcessor.process(state, result.proposal.action).result.error shouldBe null
        }
        test("stale input and initialized source identity reject") {
            val f = fixture()
            shouldThrowAny { SphinxStageEWholeActor.decide(f.input, f.epoch.copy(step = 2), f.seat) }
            val epoch = f.epoch.copy(sourceVersion = "another-source")
            val input = sealed(f.input.copy(epoch = epoch))
            shouldThrowAny { SphinxStageEWholeActor.decide(input, epoch, f.seat) }
        }
        test("landcycling or casting competitor is never ignored by ordinary scheduling") {
            val f = fixture()
            val template = f.input.legalActions.first { it.isManaAbility }
            for (action in listOf(CastSpell(f.seat.actorId, f.state.getHand(f.seat.actorId).first()),
                TypecycleCard(f.seat.actorId, f.state.getHand(f.seat.actorId).first()))) {
                val input = sealed(f.input.copy(legalActions = f.input.legalActions +
                    template.copy(action = action, isManaAbility = false)))
                SphinxStageEOrdinaryPriority.decide(input, f.epoch, f.seat.actorId)
                    .shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
            }
        }
        test("nonmana activation and nonbasic source remain unqualified") {
            val f = fixture()
            val altered = sealed(f.input.copy(legalActions = f.input.legalActions.map {
                if (it.isManaAbility) it.copy(isManaAbility = false) else it }))
            SphinxStageEOrdinaryPriority.decide(altered, f.epoch, f.seat.actorId)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
            val sourceChanged = sealed(f.input.copy(observation = f.input.observation.copy(
                zones = f.input.observation.zones.map { zone -> zone.copy(cards = zone.cards.map {
                    if (it.zone == Zone.BATTLEFIELD) it.copy(name = "Unqualified mana source") else it
                }) })))
            SphinxStageEOrdinaryPriority.decide(sourceChanged, f.epoch, f.seat.actorId)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
        }
        test("wrong priority actor and duplicate pass cannot be scheduled") {
            val f = fixture()
            val changed = sealed(f.input.copy(observation = f.input.observation.copy(
                priorityPlayerId = f.state.turnOrder.single { it != f.seat.actorId })))
            SphinxStageEOrdinaryPriority.decide(changed, f.epoch, f.seat.actorId)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
            val duplicate = sealed(f.input.copy(legalActions = f.input.legalActions +
                f.input.legalActions.single { it.action is PassPriority }))
            SphinxStageEOrdinaryPriority.decide(duplicate, f.epoch, f.seat.actorId)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Unqualified>()
        }
    }
}
