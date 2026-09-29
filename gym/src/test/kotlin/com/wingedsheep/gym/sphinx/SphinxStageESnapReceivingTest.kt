package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.handlers.effects.ZoneEntryOptions
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
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

/**
 * Prospective Snap receiving bank. It qualifies only a unique real creature target followed by
 * the already-source-bounded public SelectCardsDecision for up to two currently tapped own lands.
 */
class SphinxStageESnapReceivingTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_SNAP_RECEIVING_BUDGET_20260928.json")) }
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)
    private val epoch = ActorEpoch("stage-e-snap-receiving-v1", "fixed-snap-bank", 0)

    private fun name(state: com.wingedsheep.engine.state.GameState, id: EntityId) =
        state.getEntity(id)!!.get<CardComponent>()!!.name

    init {
        listOf("reconstructed-v01", "reconstructed-hybrid", "closest-no-approach-v01").forEach { identity ->
            test("SN1 $identity real Snap unique target and two-land untap continuation") {
                val bytes = Files.readAllBytes(root.resolve("sphinx-approach/decks/$identity.csv"))
                val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
                (own.cards["Snap"] ?: 0) shouldBe 4

                val actor = EntityId.of("snap-$identity-actor")
                val opponent = EntityId.of("snap-$identity-opponent")
                val ownNames = own.cards.flatMap { (card, count) -> List(count) { card } }
                val oppNames = List(59) { "Island" } + "Grizzly Bears"
                val initialized = GameInitializer(cardRegistry).initializeGame(GameConfig(
                    players = listOf(
                        PlayerConfig("Actual frozen 60", Deck(ownNames), playerId = actor),
                        PlayerConfig("Excluded target seat", Deck(oppNames), playerId = opponent),
                    ),
                    startingHandSize = 7, skipMulligans = false, useHandSmoother = false,
                    startingPlayerIndex = 0, seed = 0x5350_4E41_5000L,
                ))

                val all = (initialized.state.getHand(actor) + initialized.state.getLibrary(actor)).sortedBy { it.value }
                val snap = all.first { name(initialized.state, it) == "Snap" }
                val islands = all.filter { name(initialized.state, it) == "Island" || name(initialized.state, it) == "Snow-Covered Island" }
                val hand = listOf(snap) + islands.take(2) + all.filter { it != snap && it !in islands.take(2) }.take(4)
                hand.size shouldBe 7
                val arranged = initialized.copy(state = initialized.state.copy(zones = initialized.state.zones +
                    (ZoneKey(actor, Zone.HAND) to hand) +
                    (ZoneKey(actor, Zone.LIBRARY) to all.filter { it !in hand })))
                val seat = SphinxStageEInitializedSeat.bindOpening(arranged, actor, bytes, epoch)

                var state = arranged.state
                fun advance(action: GameAction) {
                    val result = actionProcessor.process(state, action).result
                    result.error shouldBe null
                    state = result.state
                }
                advance(KeepHand(actor))
                advance(KeepHand(opponent))

                val actorIslands = state.getHand(actor).filter { name(state, it) == "Island" || name(state, it) == "Snow-Covered Island" }.take(2)
                actorIslands.size shouldBe 2
                actorIslands.forEach {
                    state = ZoneTransitionService.moveToZone(state, it, Zone.BATTLEFIELD,
                        ZoneEntryOptions(controllerId = actor)).state
                }
                val targetCreature = (state.getHand(opponent) + state.getLibrary(opponent)).first { name(state, it) == "Grizzly Bears" }
                state = ZoneTransitionService.moveToZone(state, targetCreature, Zone.BATTLEFIELD,
                    ZoneEntryOptions(controllerId = opponent)).state
                state = state.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN,
                    activePlayerId = actor, priorityPlayerId = actor)

                val input = adapter.build(state, actor, completeActorLegalActions(state, actor, enumerator),
                    epoch.copy(step = 1), 0x5350_4E41_5001L)
                val index = input.legalActions.indexOfFirst { (it.action as? CastSpell)?.cardId == snap }
                require(index >= 0)
                val proposed = seat.decideCurrentCast(input, epoch.copy(step = 1), index,
                    SphinxStageEComponentCall.SNAP).shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
                (proposed.proposal.action as CastSpell).targets.size shouldBe 1
                advance(proposed.proposal.action)

                repeat(6) {
                    if (state.pendingDecision == null) advance(PassPriority(requireNotNull(state.priorityPlayerId)))
                }
                state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()

                val choiceInput = adapter.build(state, actor, completeActorLegalActions(state, actor, enumerator),
                    epoch.copy(step = 2), 0x5350_4E41_5002L)
                val choice = seat.decideVisibleChoice(choiceInput, epoch.copy(step = 2))
                    .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
                advance(choice.proposal.action)
                actorIslands.all { state.getEntity(it)!!.has<TappedComponent>() == false } shouldBe true
                state.getHand(opponent).contains(targetCreature) shouldBe true
            }
        }
    }
}
