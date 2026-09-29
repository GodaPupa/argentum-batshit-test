package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.AlternativeCostType
import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.handlers.effects.ZoneEntryOptions
import com.wingedsheep.engine.handlers.effects.ZoneTransitionService
import com.wingedsheep.engine.legalactions.LegalActionEnumerator
import com.wingedsheep.engine.state.ZoneKey
import com.wingedsheep.engine.state.components.battlefield.TappedComponent
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
import com.wingedsheep.engine.support.ScenarioTestBase
import com.wingedsheep.gym.actorinput.ActorEpoch
import com.wingedsheep.gym.actorinput.ObservationAdapter
import com.wingedsheep.gym.actorinput.completeActorLegalActions
import com.wingedsheep.sdk.core.AbilityFlag
import com.wingedsheep.sdk.core.Phase
import com.wingedsheep.sdk.core.Step
import com.wingedsheep.sdk.core.Zone
import com.wingedsheep.sdk.model.Deck
import com.wingedsheep.sdk.model.EntityId
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

/** Exact Sleep of the Dead escape receiving only; no general escape or graveyard ranking policy. */
class SphinxStageESleepEscapeReceivingTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_SLEEP_ESCAPE_RECEIVING_BUDGET_20260929.json")) }
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)
    private val epoch = ActorEpoch("stage-e-sleep-escape-v1", "fixed-sleep-bank", 0)

    private fun name(state: com.wingedsheep.engine.state.GameState, id: EntityId) =
        state.getEntity(id)!!.get<CardComponent>()!!.name

    init {
        test("SE1 benchmark real Sleep escape pays exact three visible cards and taps the unique opposing creature") {
            val identity = "serpico-terror-benchmark"
            val bytes = Files.readAllBytes(root.resolve("sphinx-approach/decks/$identity.csv"))
            val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
            (own.cards["Sleep of the Dead"] ?: 0) shouldBe 2

            val actor = EntityId.of("sleep-benchmark-actor")
            val opponent = EntityId.of("sleep-benchmark-opponent")
            val ownNames = own.cards.flatMap { (card, count) -> List(count) { card } }
            val opponentNames = listOf("Grizzly Bears") + List(59) { "Island" }
            val initialized = GameInitializer(cardRegistry).initializeGame(GameConfig(
                players = listOf(
                    PlayerConfig("Actual frozen benchmark", Deck(ownNames), playerId = actor),
                    PlayerConfig("Single opposing creature seat", Deck(opponentNames), playerId = opponent),
                ),
                startingHandSize = 7, skipMulligans = false, useHandSmoother = false,
                startingPlayerIndex = 0, seed = 0x534c_4545_5000L,
            ))

            val allOwn = (initialized.state.getHand(actor) + initialized.state.getLibrary(actor)).sortedBy { it.value }
            val openingIslands = allOwn.filter {
                name(initialized.state, it) in setOf("Island", "Snow-Covered Island")
            }.take(7)
            openingIslands.size shouldBe 7
            val sleep = allOwn.first { name(initialized.state, it) == "Sleep of the Dead" }
            val fuel = allOwn.filter {
                it !in openingIslands &&
                    name(initialized.state, it) in setOf("Island", "Snow-Covered Island")
            }.take(3).sortedBy { it.value }
            fuel.size shouldBe 3
            val opponentObjects = initialized.state.getHand(opponent) + initialized.state.getLibrary(opponent)
            val target = opponentObjects.single { name(initialized.state, it) == "Grizzly Bears" }

            val arranged = initialized.copy(state = initialized.state.copy(zones = initialized.state.zones +
                (ZoneKey(actor, Zone.HAND) to openingIslands) +
                (ZoneKey(actor, Zone.LIBRARY) to allOwn.filter { it !in openingIslands })))
            val seat = SphinxStageEInitializedSeat.bindOpening(arranged, actor, bytes, epoch)

            var state = arranged.state
            fun advance(action: GameAction) {
                val result = actionProcessor.process(state, action).result
                result.error shouldBe null
                state = result.state
            }
            advance(KeepHand(actor))
            advance(KeepHand(opponent))

            openingIslands.take(3).forEach {
                state = ZoneTransitionService.moveToZone(
                    state, it, Zone.BATTLEFIELD, ZoneEntryOptions(controllerId = actor)
                ).state
            }
            openingIslands.drop(3).forEach {
                state = ZoneTransitionService.moveToZone(state, it, Zone.LIBRARY).state
            }
            state = ZoneTransitionService.moveToZone(state, sleep, Zone.GRAVEYARD).state
            fuel.forEach {
                state = ZoneTransitionService.moveToZone(state, it, Zone.GRAVEYARD).state
            }
            state = ZoneTransitionService.moveToZone(
                state, target, Zone.BATTLEFIELD, ZoneEntryOptions(controllerId = opponent)
            ).state
            state = state.copy(
                phase = Phase.PRECOMBAT_MAIN,
                step = Step.PRECOMBAT_MAIN,
                activePlayerId = actor,
                priorityPlayerId = actor,
            )

            val step = epoch.copy(step = 1)
            val input = adapter.build(
                state,
                actor,
                completeActorLegalActions(state, actor, enumerator),
                step,
                0x534c_4545_5001L,
            )
            val casts = input.legalActions.filter { it.action is CastSpell }
            casts.size shouldBe 1
            val legal = casts.single()
            val offered = legal.action.shouldBeInstanceOf<CastSpell>()
            offered.cardId shouldBe sleep
            offered.useAlternativeCost shouldBe true
            offered.alternativeCostType shouldBe AlternativeCostType.ESCAPE
            legal.additionalCostInfo!!.costType shouldBe "ExileFromGraveyard"
            legal.additionalCostInfo!!.validExileTargets.sortedBy { it.value } shouldBe fuel
            legal.additionalCostInfo!!.exileMinCount shouldBe 3
            legal.additionalCostInfo!!.exileMaxCount shouldBe 3

            val proposed = SphinxStageEWholeActor.decide(input, step, seat)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
            val cast = proposed.proposal.action.shouldBeInstanceOf<CastSpell>()
            cast.cardId shouldBe sleep
            cast.useAlternativeCost shouldBe true
            cast.alternativeCostType shouldBe AlternativeCostType.ESCAPE
            cast.targets shouldBe listOf(ChosenTarget.Permanent(target))
            cast.additionalCostPayment!!.exiledCards.sortedBy { it.value } shouldBe fuel
            advance(cast)

            fuel.forEach { state.zones[ZoneKey(actor, Zone.EXILE)].orEmpty().contains(it) shouldBe true }

            repeat(6) {
                if (state.stack.isNotEmpty() && state.pendingDecision == null && state.priorityPlayerId != null) {
                    advance(PassPriority(requireNotNull(state.priorityPlayerId)))
                }
            }
            state.stack.isEmpty() shouldBe true
            state.zones[ZoneKey(actor, Zone.GRAVEYARD)].orEmpty().contains(sleep) shouldBe true
            state.getEntity(target)?.has<TappedComponent>() shouldBe true
            state.projectedState.hasKeyword(target, AbilityFlag.DOESNT_UNTAP) shouldBe true
        }
    }
}
