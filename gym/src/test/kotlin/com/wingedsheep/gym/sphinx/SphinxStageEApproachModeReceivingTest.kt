package com.wingedsheep.gym.sphinx

import com.wingedsheep.engine.core.CastSpell
import com.wingedsheep.engine.core.GameAction
import com.wingedsheep.engine.core.GameConfig
import com.wingedsheep.engine.core.GameInitializer
import com.wingedsheep.engine.core.KeepHand
import com.wingedsheep.engine.core.PassPriority
import com.wingedsheep.engine.core.PlayerConfig
import com.wingedsheep.engine.core.SelectCardsDecision
import com.wingedsheep.engine.core.YesNoDecision
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
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.nio.file.Files
import java.nio.file.Path

/**
 * Bounded real receiving for the complete Sphinx's Approach mid-resolution choice sequence.
 * No new policy is authored here; every answer must come from the current whole actor.
 */
class SphinxStageEApproachModeReceivingTest : ScenarioTestBase() {
    private val root = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.exists(it.resolve("sphinx-approach/STAGE_E_APPROACH_MODE_RECEIVING_BUDGET_20260929.json")) }
    private val adapter = ObservationAdapter(cardRegistry)
    private val enumerator = LegalActionEnumerator.create(cardRegistry)

    private fun name(state: com.wingedsheep.engine.state.GameState, id: EntityId) =
        state.getEntity(id)!!.get<CardComponent>()!!.name

    private fun runCase(identity: String) {
        val bytes = Files.readAllBytes(root.resolve("sphinx-approach/decks/$identity.csv"))
        val own = SphinxStageEOwnDeck.fromFrozenCsv(bytes)
        ((own.cards["Sphinx's Approach"] ?: 0) >= 6) shouldBe true
        (own.cards["Goliath Sphinx"] ?: 0) shouldBe 2

        val actor = EntityId.of("approach-mode-actor-$identity")
        val opponent = EntityId.of("approach-mode-opponent-$identity")
        val ownNames = own.cards.flatMap { (card, count) -> List(count) { card } }
        val epoch = ActorEpoch("stage-e-approach-mode-v1", identity, 0)
        val initialized = GameInitializer(cardRegistry).initializeGame(GameConfig(
            players = listOf(
                PlayerConfig("Actual frozen Approach", Deck(ownNames), playerId = actor),
                PlayerConfig("Passive opponent", Deck(List(60) { "Island" }), playerId = opponent),
            ),
            startingHandSize = 7,
            skipMulligans = false,
            useHandSmoother = false,
            startingPlayerIndex = 0,
            seed = 0x4150_5052_4F41L,
        ))

        val all = (initialized.state.getHand(actor) + initialized.state.getLibrary(actor)).sortedBy { it.value }
        val source = all.first { name(initialized.state, it) == "Sphinx's Approach" }
        val islands = all.filter { name(initialized.state, it) == "Island" }
        val openingIslands = islands.take(3)
        val fillers = all.filter {
            it != source && it !in openingIslands &&
                name(initialized.state, it) !in setOf("Sphinx's Approach", "Goliath Sphinx", "Island")
        }.take(3)
        val hand = listOf(source) + openingIslands + fillers
        hand.size shouldBe 7
        val safeDraws = islands.filter { it !in openingIslands }.take(2)
        safeDraws.size shouldBe 2
        val library = safeDraws + all.filter { it !in hand && it !in safeDraws }
        val arranged = initialized.copy(state = initialized.state.copy(
            zones = initialized.state.zones +
                (ZoneKey(actor, Zone.HAND) to hand) +
                (ZoneKey(actor, Zone.LIBRARY) to library)
        ))
        val seat = SphinxStageEInitializedSeat.bindOpening(arranged, actor, bytes, epoch)

        var state = arranged.state
        fun advance(action: GameAction) {
            val result = actionProcessor.process(state, action).result
            result.error shouldBe null
            state = result.state
        }
        fun whole(step: Long): SphinxStageEAdapterResult.Proposed {
            val currentEpoch = epoch.copy(step = step)
            val input = adapter.build(
                state,
                actor,
                completeActorLegalActions(state, actor, enumerator),
                currentEpoch,
                0x4150_5052_0000L + step,
            )
            return SphinxStageEWholeActor.decide(input, currentEpoch, seat)
                .shouldBeInstanceOf<SphinxStageEAdapterResult.Proposed>()
        }

        advance(KeepHand(actor))
        advance(KeepHand(opponent))
        openingIslands.forEach {
            state = ZoneTransitionService.moveToZone(
                state, it, Zone.BATTLEFIELD, ZoneEntryOptions(controllerId = actor)
            ).state
        }

        val graveApproaches = state.getLibrary(actor)
            .filter { name(state, it) == "Sphinx's Approach" }
            .take(5)
        graveApproaches.size shouldBe 5
        graveApproaches.forEach {
            state = ZoneTransitionService.moveToZone(state, it, Zone.GRAVEYARD).state
        }
        state.getLibrary(actor).take(2) shouldBe safeDraws
        state = state.copy(
            phase = Phase.PRECOMBAT_MAIN,
            step = Step.PRECOMBAT_MAIN,
            activePlayerId = actor,
            priorityPlayerId = actor,
        )

        advance(CastSpell(actor, source))
        var guard = 0
        while (state.pendingDecision == null && state.stack.isNotEmpty() && guard++ < 12) {
            advance(PassPriority(requireNotNull(state.priorityPlayerId)))
        }

        val may = state.pendingDecision.shouldBeInstanceOf<YesNoDecision>()
        may.context.sourceName shouldBe "Sphinx's Approach"
        state.getHand(actor).size shouldBe 5
        advance(whole(1).proposal.action)

        val payment = state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        payment.context.sourceName shouldBe "Sphinx's Approach"
        payment.minSelections shouldBe 4
        payment.maxSelections shouldBe 4
        payment.options.size shouldBe 5
        payment.options.toSet() shouldBe graveApproaches.toSet()
        val expectedPaid = payment.options.sortedBy { it.value }.take(4).toSet()
        advance(whole(2).proposal.action)

        val search = state.pendingDecision.shouldBeInstanceOf<SelectCardsDecision>()
        search.context.sourceName shouldBe "Sphinx's Approach"
        (search.minSelections in 0..1) shouldBe true
        search.maxSelections shouldBe 1
        search.options.isNotEmpty() shouldBe true
        search.options.all { name(state, it) == "Goliath Sphinx" } shouldBe true
        val expectedSphinx = search.options.sortedBy { it.value }.first()
        advance(whole(3).proposal.action)

        state.pendingDecision shouldBe null
        state.stack shouldBe emptyList()
        val exile = state.getExile(actor).toSet()
        (source in exile) shouldBe true
        expectedPaid.all { it in exile } shouldBe true
        graveApproaches.count { it in exile } shouldBe 4
        state.getGraveyard(actor).filter { name(state, it) == "Sphinx's Approach" }.toSet() shouldBe
            (graveApproaches.toSet() - expectedPaid)
        (expectedSphinx in state.getBattlefield()) shouldBe true
        name(state, expectedSphinx) shouldBe "Goliath Sphinx"
    }

    init {
        listOf("reconstructed-v01", "reconstructed-hybrid").forEach { identity ->
            test("AM1 $identity real Approach May payment and Sphinx search receiving") {
                runCase(identity)
            }
        }
    }
}
