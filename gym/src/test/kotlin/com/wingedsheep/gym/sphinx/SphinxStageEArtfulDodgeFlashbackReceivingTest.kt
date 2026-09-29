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
import com.wingedsheep.engine.state.components.identity.CardComponent
import com.wingedsheep.engine.state.components.stack.ChosenTarget
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

/** Artful Dodge flashback receiving only; no general alternate-cost or combat policy. */
class SphinxStageEArtfulDodgeFlashbackReceivingTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_ARTFUL_DODGE_FLASHBACK_RECEIVING_BUDGET_20260929.json")) }
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)
    private val epoch = ActorEpoch("stage-e-artful-flashback-v1", "fixed-artful-bank", 0)

    private fun name(state: com.wingedsheep.engine.state.GameState, id: EntityId) =
        state.getEntity(id)!!.get<CardComponent>()!!.name

    init {
        test("AD1 benchmark real Artful Dodge flashback selects unique own creature and resolves to exile") {
            val identity = "serpico-terror-benchmark"
            val bytes = Files.readAllBytes(root.resolve("sphinx-approach/decks/$identity.csv"))
            val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
            (own.cards["Artful Dodge"] ?: 0) shouldBe 2

            val actor = EntityId.of("artful-benchmark-actor")
            val opponent = EntityId.of("artful-benchmark-opponent")
            val ownNames = own.cards.flatMap { (card, count) -> List(count) { card } }
            val initialized = GameInitializer(cardRegistry).initializeGame(GameConfig(
                players = listOf(
                    PlayerConfig("Actual frozen benchmark", Deck(ownNames), playerId = actor),
                    PlayerConfig("No competing target seat", Deck(List(60) { "Island" }), playerId = opponent),
                ),
                startingHandSize = 7, skipMulligans = false, useHandSmoother = false,
                startingPlayerIndex = 0, seed = 0x4152_5446_0000L,
            ))

            val all = (initialized.state.getHand(actor) + initialized.state.getLibrary(actor)).sortedBy { it.value }
            val openingIslands = all.filter { name(initialized.state, it) in setOf("Island", "Snow-Covered Island") }
                .take(7)
            openingIslands.size shouldBe 7
            val artful = all.first { name(initialized.state, it) == "Artful Dodge" }
            val creature = all.first { name(initialized.state, it) == "Cryptic Serpent" }
            val arranged = initialized.copy(state = initialized.state.copy(zones = initialized.state.zones +
                (ZoneKey(actor, Zone.HAND) to openingIslands) +
                (ZoneKey(actor, Zone.LIBRARY) to all.filter { it !in openingIslands })))
            val seat = SphinxStageEInitializedSeat.bindOpening(arranged, actor, bytes, epoch)

            var state = arranged.state
            fun advance(action: GameAction) {
                val result = actionProcessor.process(state, action).result
                result.error shouldBe null
                state = result.state
            }
            advance(KeepHand(actor))
            advance(KeepHand(opponent))

            state = ZoneTransitionService.moveToZone(
                state, openingIslands.first(), Zone.BATTLEFIELD, ZoneEntryOptions(controllerId = actor)
            ).state
            openingIslands.drop(1).forEach {
                state = ZoneTransitionService.moveToZone(state, it, Zone.LIBRARY).state
            }
            state = ZoneTransitionService.moveToZone(state, artful, Zone.GRAVEYARD).state
            state = ZoneTransitionService.moveToZone(
                state, creature, Zone.BATTLEFIELD, ZoneEntryOptions(controllerId = actor)
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
                0x4152_5446_0001L,
            )
            val casts = input.legalActions.filter { it.action is CastSpell }
            casts.size shouldBe 1
            val offered = casts.single().action.shouldBeInstanceOf<CastSpell>()
            offered.cardId shouldBe artful
            offered.useAlternativeCost shouldBe true
            offered.alternativeCostType shouldBe AlternativeCostType.FLASHBACK

            val proposed = SphinxStageEWholeActor.decide(input, step, seat)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
            val cast = proposed.proposal.action.shouldBeInstanceOf<CastSpell>()
            cast.cardId shouldBe artful
            cast.useAlternativeCost shouldBe true
            cast.alternativeCostType shouldBe AlternativeCostType.FLASHBACK
            cast.targets shouldBe listOf(ChosenTarget.Permanent(creature))
            advance(cast)

            repeat(6) {
                val exile = state.zones[ZoneKey(actor, Zone.EXILE)].orEmpty()
                if (artful !in exile && state.pendingDecision == null && state.priorityPlayerId != null) {
                    advance(PassPriority(requireNotNull(state.priorityPlayerId)))
                }
            }
            state.zones[ZoneKey(actor, Zone.EXILE)].orEmpty().contains(artful) shouldBe true
        }
    }
}
