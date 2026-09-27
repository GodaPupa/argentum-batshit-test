package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.DecisionPhase
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.ReorderLibraryDecision
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.TypecycleCard
import com.wingedsheep.engine.core.YesNoDecision
import com.wingedsheep.engine.handlers.effects.ZoneEntryOptions
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.GameState
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.actorinput.ActorEpoch
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

/** Excluded initialized source-shape trace, not a prospective policy bank or a game. */
class SphinxStageEVisibleChoiceTraceTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_PILOT_FIXTURE_BUDGET.json")) }
    private val epoch = ActorEpoch("stage-e-visible-choice-trace-v1", "excluded-initialized-source-shape", 0)
    private val observation = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)

    private fun cardName(state: GameState, id: EntityId) =
        state.getEntity(id)!!.get<CardComponent>()!!.name

    private fun trace(identity: String, sourceName: String, cycling: Boolean = false) {
        val bytes = Files.readAllBytes(root.resolve("sphinx-approach/decks/$identity.csv"))
        val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
        val names = own.cards.flatMap { (name, count) -> List(count) { name } }
        val actor = EntityId.of("stage-e-trace-actor")
        val other = EntityId.of("stage-e-trace-opponent")
        val initialized = GameInitializer(cardRegistry).initializeGame(GameConfig(
            players = listOf(PlayerConfig("Actual frozen 60", Deck(names), playerId = actor),
                PlayerConfig("Passive excluded seat", Deck(List(60) { "Island" }), playerId = other)),
            startingHandSize = 7, skipMulligans = false, useHandSmoother = false,
            startingPlayerIndex = 0, seed = 0x5350_4849_4E58_0003L,
        ))
        val all = (initialized.state.getHand(actor) + initialized.state.getLibrary(actor))
            .sortedBy { it.value }
        val sourceId = all.first { cardName(initialized.state, it) == sourceName }
        val islands = all.filter { cardName(initialized.state, it) == "Island" }
        val hand = listOf(sourceId) + islands.take(3) +
            all.filter { it != sourceId && it !in islands.take(3) &&
                cardName(initialized.state, it) != "Goliath Sphinx" }.take(3)
        require(hand.size == 7 && hand.distinct().size == 7)
        val library = all.filter { it !in hand }
        val arranged = initialized.copy(state = initialized.state.copy(zones = initialized.state.zones +
            (ZoneKey(actor, Zone.HAND) to hand) + (ZoneKey(actor, Zone.LIBRARY) to library)))
        val seat = SphinxStageEInitializedSeat.bindOpening(arranged, actor, bytes, epoch)
        var state = arranged.state
        fun advance(action: com.wingedsheep.engine.core.GameAction) {
            val result = actionProcessor.process(state, action).result
            result.error shouldBe null
            state = result.state
        }
        advance(KeepHand(actor))
        advance(KeepHand(other))
        if (sourceName == "Sphinx's Approach") {
            val extra = state.getLibrary(actor).filter { cardName(state, it) == sourceName }.take(4)
            extra.size shouldBe 4
            extra.forEach { state = ZoneTransitionService.moveToZone(state, it, Zone.GRAVEYARD).state }
        }
        islands.take(if (sourceName == "Sphinx's Approach") 3 else 1).forEach {
            state = ZoneTransitionService.moveToZone(state, it, Zone.BATTLEFIELD,
                ZoneEntryOptions(controllerId = actor)).state
        }
        state = state.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN,
            activePlayerId = actor, priorityPlayerId = actor)
        if (cycling) {
            advance(TypecycleCard(actor, sourceId))
        } else {
            advance(CastSpell(actor, sourceId))
            repeat(4) {
                if (state.pendingDecision == null) advance(PassPriority(requireNotNull(state.priorityPlayerId)))
            }
        }
        val q = requireNotNull(state.pendingDecision) { "$sourceName produced no typed choice" }
        q.context.phase shouldBe DecisionPhase.RESOLUTION
        q.context.sourceId shouldBe sourceId
        q.context.sourceName shouldBe sourceName
        val input = observation.build(state, actor,
            completeActorLegalActions(state, actor, enumerator), epoch.copy(step = 1), 0x5350_0004L)
        val sourceVisible = input.observation.stack.any { it.view.entityId == sourceId &&
            it.spell?.ownerId == actor } || input.observation.zones.flatMap { it.cards }.any {
            it.entityId == sourceId && it.ownerId == actor }
        sourceVisible shouldBe true
        val result = seat.decideVisibleChoice(input, epoch.copy(step = 1))
        result.shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
        when (sourceName) {
            "Sphinx's Approach" -> q.shouldBeInstanceOf<YesNoDecision>()
            "Brainstorm", "Preordain", "Lórien Revealed" -> q.shouldBeInstanceOf<SelectCardsDecision>()
            "Ponder" -> q.shouldBeInstanceOf<ReorderLibraryDecision>()
        }
    }

    init {
        listOf("reconstructed-v01", "reconstructed-hybrid").forEach { identity ->
            test("T1 $identity initialized Approach May source and actor projection") {
                trace(identity, "Sphinx's Approach")
            }
        }
        listOf("closest-no-approach-v01", "serpico-terror-benchmark").forEach { identity ->
            listOf("Brainstorm", "Preordain", "Ponder", "Lórien Revealed").forEach { source ->
                test("T2 $identity initialized $source first question source and actor projection") {
                    trace(identity, source, cycling = source == "Lórien Revealed")
                }
            }
        }
    }
}
