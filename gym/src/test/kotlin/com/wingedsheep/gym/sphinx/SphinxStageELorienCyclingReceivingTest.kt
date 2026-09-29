package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.TypecycleCard
import com.wingedsheep.engine.handlers.effects.ZoneEntryOptions
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
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
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

/**
 * Receiving only: proves real Lórien Islandcycling reaches the existing visible Island-search seam.
 * It does not decide when a whole pilot should cycle instead of passing or casting the sorcery.
 */
class SphinxStageELorienCyclingReceivingTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_LORIEN_CYCLING_RECEIVING_BUDGET_20260929.json")) }
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)
    private val epoch = ActorEpoch("stage-e-lorien-cycling-receiving-v1", "fixed-lorien-bank", 0)

    private fun name(state: com.wingedsheep.engine.state.GameState, id: EntityId) =
        state.getEntity(id)!!.get<CardComponent>()!!.name

    init {
        listOf("closest-no-approach-v01", "serpico-terror-benchmark").forEach { identity ->
            test("LR1 $identity real Islandcycling and visible Island search continuation") {
                val bytes = Files.readAllBytes(root.resolve("sphinx-approach/decks/$identity.csv"))
                val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
                (own.cards["Lórien Revealed"] ?: 0) shouldBe 4

                val actor = EntityId.of("lorien-$identity-actor")
                val opponent = EntityId.of("lorien-$identity-opponent")
                val ownNames = own.cards.flatMap { (card, count) -> List(count) { card } }
                val initialized = GameInitializer(cardRegistry).initializeGame(GameConfig(
                    players = listOf(
                        PlayerConfig("Actual frozen 60", Deck(ownNames), playerId = actor),
                        PlayerConfig("Excluded seat", Deck(List(60) { "Island" }), playerId = opponent),
                    ),
                    startingHandSize = 7, skipMulligans = false, useHandSmoother = false,
                    startingPlayerIndex = 0, seed = 0x4C4F_5249_454EL,
                ))

                val all = (initialized.state.getHand(actor) + initialized.state.getLibrary(actor)).sortedBy { it.value }
                val lorien = all.first { name(initialized.state, it) == "Lórien Revealed" }
                val islands = all.filter {
                    name(initialized.state, it) == "Island" || name(initialized.state, it) == "Snow-Covered Island"
                }
                islands.size shouldBe (own.cards["Island"] ?: 0) + (own.cards["Snow-Covered Island"] ?: 0)
                val paymentLand = islands.first()
                val hand = listOf(lorien, paymentLand) +
                    all.filter { it != lorien && it != paymentLand }.take(5)
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
                state = ZoneTransitionService.moveToZone(state, paymentLand, Zone.BATTLEFIELD,
                    ZoneEntryOptions(controllerId = actor)).state
                state = state.copy(phase = Phase.PRECOMBAT_MAIN, step = Step.PRECOMBAT_MAIN,
                    activePlayerId = actor, priorityPlayerId = actor)

                val input = adapter.build(state, actor, completeActorLegalActions(state, actor, enumerator),
                    epoch.copy(step = 1), 0x4C4F_5249_454FL)
                val typecycle = input.legalActions.singleOrNull {
                    (it.action as? TypecycleCard)?.cardId == lorien && it.affordable
                } ?: error("real affordable Lórien TypecycleCard offer absent")
                advance(typecycle.action)

                state.getHand(actor) shouldNotContain lorien
                state.getGraveyard(actor) shouldContain lorien
                val decision = state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
                decision.options.isNotEmpty() shouldBe true
                decision.options.all {
                    val n = name(state, it)
                    n == "Island" || n == "Snow-Covered Island"
                } shouldBe true

                val before = state.getHand(actor).toSet()
                val choiceInput = adapter.build(state, actor, completeActorLegalActions(state, actor, enumerator),
                    epoch.copy(step = 2), 0x4C4F_5249_4550L)
                val choice = seat.decideVisibleChoice(choiceInput, epoch.copy(step = 2))
                    .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
                advance(choice.proposal.action)
                val added = state.getHand(actor).toSet() - before
                added.size shouldBe 1
                val found = added.single()
                (name(state, found) == "Island" || name(state, found) == "Snow-Covered Island") shouldBe true
                state.getLibrary(actor) shouldNotContain found
            }
        }
    }
}
